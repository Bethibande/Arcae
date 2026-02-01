import {useNavigate, useParams, useSearchParams} from "react-router";
import {useEffect, useState} from "react";
import {
    type ArtifactDTO,
    ArtifactEndpointApi,
    type ArtifactVersionDTO,
    type PagedResponseArtifactVersionDTO,
    RepositoryEndpointApi,
    type RepositoryOverviewDTO
} from "@/generated";
import {showError} from "@/lib/errors.ts";
import {
    ArrowLeft,
    Check,
    ChevronRight,
    Clock,
    Copy,
    ExternalLink,
    Globe,
    Info,
    Package,
    Tag,
    Terminal,
    User
} from "lucide-react";
import {Button} from "@/components/ui/button.tsx";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card.tsx";
import {Badge} from "@/components/ui/badge.tsx";
import {cn} from "@/lib/utils.ts";
import {Tabs, TabsContent, TabsList, TabsTrigger} from "@/components/ui/tabs.tsx";

interface DependencySnippet {
    name: string;
    language: string;
    content: string;
}

function UsageDetails({ artifact, version, packageManager }: { artifact: ArtifactDTO, version: ArtifactVersionDTO, packageManager: string }) {
    const [copied, setCopied] = useState<string | null>(null);

    const getSnippets = (): DependencySnippet[] => {
        if (packageManager === "MAVEN_3") {
            return [
                {
                    name: "Maven",
                    language: "xml",
                    content: `<dependency>\n    <groupId>${artifact.groupId}</groupId>\n    <artifactId>${artifact.artifactId}</artifactId>\n    <version>${version.version}</version>\n</dependency>`
                },
                {
                    name: "Gradle (Kotlin)",
                    language: "kotlin",
                    content: `implementation("${artifact.groupId}:${artifact.artifactId}:${version.version}")`
                },
                {
                    name: "Gradle (Groovy)",
                    language: "groovy",
                    content: `implementation '${artifact.groupId}:${artifact.artifactId}:${version.version}'`
                },
                {
                    name: "SBT",
                    language: "scala",
                    content: `libraryDependencies += "${artifact.groupId}" % "${artifact.artifactId}" % "${version.version}"`
                }
            ];
        }
        return [];
    };

    const snippets = getSnippets();

    if (snippets.length === 0) return null;

    const copyToClipboard = (text: string, name: string) => {
        navigator.clipboard.writeText(text);
        setCopied(name);
        setTimeout(() => setCopied(null), 2000);
    };

    return (
        <Card>
            <CardHeader className="pb-3">
                <CardTitle className="text-lg flex items-center gap-2">
                    <Terminal className="size-5"/>
                    Dependency
                </CardTitle>
            </CardHeader>
            <CardContent>
                <Tabs defaultValue={snippets[0].name} className="w-full">
                    <TabsList className="mb-4">
                        {snippets.map(s => (
                            <TabsTrigger key={s.name} value={s.name}>
                                {s.name}
                            </TabsTrigger>
                        ))}
                    </TabsList>
                    
                    {snippets.map(s => (
                        <TabsContent key={s.name} value={s.name}>
                            <div className="relative group">
                                <pre className="p-4 bg-muted/50 rounded-lg text-xs font-mono overflow-x-auto whitespace-pre">
                                    {s.content}
                                </pre>
                                <Button
                                    size="icon"
                                    variant="ghost"
                                    className="absolute right-2 top-2 h-8 w-8 opacity-0 group-hover:opacity-100 transition-opacity"
                                    onClick={() => copyToClipboard(s.content, s.name)}
                                >
                                    {copied === s.name ? <Check className="size-4 text-green-500" /> : <Copy className="size-4" />}
                                </Button>
                            </div>
                        </TabsContent>
                    ))}
                </Tabs>
            </CardContent>
        </Card>
    );
}

export default function ArtifactView() {
    const {id} = useParams();
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();

    const versionPage = parseInt(searchParams.get("p") || "0");

    const [artifact, setArtifact] = useState<ArtifactDTO | null>(null);
    const [repository, setRepository] = useState<RepositoryOverviewDTO | null>(null);
    const [versions, setVersions] = useState<PagedResponseArtifactVersionDTO | null>(null);
    const [selectedVersion, setSelectedVersion] = useState<ArtifactVersionDTO | null>(null);
    const [loading, setLoading] = useState(true);
    const [versionsLoading, setVersionsLoading] = useState(false);

    useEffect(() => {
        if (!id) return;

        setLoading(true);
        const api = new ArtifactEndpointApi();
        const repoApi = new RepositoryEndpointApi();
        
        api.apiV1ArtifactIdGet({id: parseInt(id)})
            .then(data => {
                setArtifact(data);
                return repoApi.apiV1RepositoryOverviewIdGet({id: data.repositoryId});
            })
            .then(setRepository)
            .catch(showError)
            .finally(() => setLoading(false));
    }, [id]);

    useEffect(() => {
        if (!id) return;

        setVersionsLoading(true);
        const api = new ArtifactEndpointApi();

        api.apiV1ArtifactIdVersionsGet({id: parseInt(id), p: versionPage, s: 10})
            .then(data => {
                setVersions(data);
                if (data.data && data.data.length > 0 && !selectedVersion) {
                    setSelectedVersion(data.data[0]);
                }
            })
            .catch(showError)
            .finally(() => setVersionsLoading(false));
    }, [id, versionPage]);

    const handlePage = (newPage: number) => {
        setSearchParams(prev => {
            prev.set("p", newPage.toString());
            return prev;
        });
    };

    if (loading) {
        return <div className="p-8 text-center">Loading artifact...</div>;
    }

    if (!artifact) {
        return <div className="p-8 text-center">Artifact not found.</div>;
    }

    return (
        <div className="flex flex-col gap-8 p-8 max-w-7xl mx-auto w-full">
            {/* Header: Breadcrumbs & Title */}
            <div className="space-y-4">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <span className="cursor-pointer hover:text-foreground"
                          onClick={() => navigate("/")}>Repositories</span>
                    <ChevronRight className="size-4"/>
                    <span className="cursor-pointer hover:text-foreground"
                          onClick={() => navigate(`/repositories/${artifact.repositoryId}/browse`)}>Browse</span>
                    <ChevronRight className="size-4"/>
                    <span className="text-foreground truncate">{artifact.artifactId}</span>
                </div>

                <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
                    <div className="flex items-center gap-4">
                        <Button variant="outline" size="icon" onClick={() => navigate(-1)} className="shrink-0">
                            <ArrowLeft className="size-4"/>
                        </Button>
                        <div className="min-w-0">
                            <div className="flex items-center gap-2 text-muted-foreground text-sm overflow-hidden">
                                <span className="font-mono truncate">{artifact.groupId}</span>
                                <span>/</span>
                            </div>
                            <h1 className="text-3xl font-bold tracking-tight truncate">{artifact.artifactId}</h1>
                            <p className="text-muted-foreground mt-1 flex items-center gap-2 text-sm">
                                <Package className="size-4 shrink-0"/>
                                <span className="truncate">Latest: {artifact.latestVersion || "N/A"}</span>
                            </p>
                        </div>
                    </div>
                </div>
            </div>

            <div className="flex flex-col md:flex-row gap-8 items-start">
                {/* Left side: Version Selection */}
                <div className="w-full md:w-80 space-y-6">
                    <Card>
                        <CardHeader className="pb-3">
                            <CardTitle className="text-lg flex items-center gap-2">
                                <Tag className="size-5"/>
                                Versions
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            <div className="space-y-1">
                                {versionsLoading ? (
                                    <div className="py-8 text-center text-sm text-muted-foreground">Loading...</div>
                                ) : (
                                    versions?.data?.map(v => (
                                        <div
                                            key={v.id}
                                            onClick={() => setSelectedVersion(v)}
                                            className={cn(
                                                "px-3 py-2 rounded-md text-sm cursor-pointer transition-colors flex items-center justify-between",
                                                selectedVersion?.id === v.id 
                                                    ? "bg-primary text-primary-foreground font-medium" 
                                                    : "hover:bg-muted"
                                            )}
                                        >
                                            <span className="truncate">{v.version}</span>
                                            {v.version === artifact.latestVersion && (
                                                <Badge variant={selectedVersion?.id === v.id ? "outline" : "secondary"} className={cn((selectedVersion?.id === v.id && "text-primary-foreground"), "text-[10px] px-1.5 h-4")}>
                                                    Latest
                                                </Badge>
                                            )}
                                        </div>
                                    ))
                                )}
                                {!versionsLoading && versions?.data?.length === 0 && (
                                    <div className="py-4 text-center text-sm text-muted-foreground italic">No versions found</div>
                                )}
                            </div>

                            {/* Pagination */}
                            {(versions?.pages || 0) > 1 && (
                                <div className="flex items-center justify-between gap-2 pt-2 border-t">
                                    <Button
                                        variant="ghost"
                                        size="sm"
                                        className="h-8 px-2"
                                        disabled={versionPage === 0 || versionsLoading}
                                        onClick={() => handlePage(versionPage - 1)}
                                    >
                                        Prev
                                    </Button>
                                    <span className="text-[11px] font-medium text-muted-foreground">
                                        {versionPage + 1} / {versions?.pages}
                                    </span>
                                    <Button
                                        variant="ghost"
                                        size="sm"
                                        className="h-8 px-2"
                                        disabled={versionPage >= (versions?.pages || 1) - 1 || versionsLoading}
                                        onClick={() => handlePage(versionPage + 1)}
                                    >
                                        Next
                                    </Button>
                                </div>
                            )}

                            {selectedVersion && (
                                <div className="text-[12px] text-muted-foreground space-y-1 pt-2 border-t">
                                    <div className="flex items-center gap-2">
                                        <Clock className="size-3 shrink-0"/>
                                        <span className="truncate">Created: {selectedVersion.created ? new Date(selectedVersion.created).toLocaleDateString() : "Unknown"}</span>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <Clock className="size-3 shrink-0"/>
                                        <span className="truncate">Updated: {selectedVersion.updated ? new Date(selectedVersion.updated).toLocaleDateString() : "Unknown"}</span>
                                    </div>
                                </div>
                            )}
                        </CardContent>
                    </Card>
                </div>

                {/* Right side: Selected Version Details */}
                <div className="flex-1 min-w-0 space-y-6">
                    {selectedVersion ? (
                        <>
                            <Card>
                                <CardHeader>
                                    <CardTitle className="text-xl flex items-center gap-2 overflow-hidden">
                                        <Info className="size-5 shrink-0"/>
                                        <span className="truncate">Details for {selectedVersion.version}</span>
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="space-y-6">
                                    {selectedVersion.details?.description && (
                                        <div>
                                            <h3 className="font-semibold mb-2">Description</h3>
                                            <p className="text-muted-foreground">{selectedVersion.details.description}</p>
                                        </div>
                                    )}

                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                        <div>
                                            <h3 className="font-semibold mb-2 flex items-center gap-2">
                                                <Globe className="size-4"/>
                                                Project URL
                                            </h3>
                                            {selectedVersion.details?.url ? (
                                                <a href={selectedVersion.details.url} target="_blank" rel="noreferrer" className="text-primary hover:underline flex items-center gap-1 min-w-0">
                                                    <span className="truncate">{selectedVersion.details.url}</span>
                                                    <ExternalLink className="size-3 shrink-0"/>
                                                </a>
                                            ) : (
                                                <span className="text-muted-foreground italic">No URL provided</span>
                                            )}
                                        </div>

                                        <div>
                                            <h3 className="font-semibold mb-2 flex items-center gap-2">
                                                <User className="size-4"/>
                                                Authors
                                            </h3>
                                            <div className="flex flex-wrap gap-2">
                                                {selectedVersion.details?.authors && selectedVersion.details.authors.length > 0 ? (
                                                    selectedVersion.details.authors.map((author, i) => (
                                                        <Badge key={i} variant="secondary">
                                                            {author.name}{author.email ? ` <${author.email}>` : ""}
                                                        </Badge>
                                                    ))
                                                ) : (
                                                    <span className="text-muted-foreground italic">No authors listed</span>
                                                )}
                                            </div>
                                        </div>
                                    </div>

                                    <div>
                                        <h3 className="font-semibold mb-2">Licenses</h3>
                                        <div className="flex flex-wrap gap-2">
                                            {selectedVersion.details?.licenses && selectedVersion.details.licenses.length > 0 ? (
                                                selectedVersion.details.licenses.map((license, i) => (
                                                    <Badge key={i} variant="outline" className="flex items-center gap-1">
                                                        {license.name}
                                                        {license.url && (
                                                            <a href={license.url} target="_blank" rel="noreferrer">
                                                                <ExternalLink className="size-3"/>
                                                            </a>
                                                        )}
                                                    </Badge>
                                                ))
                                            ) : (
                                                <span className="text-muted-foreground italic">No license information</span>
                                            )}
                                        </div>
                                    </div>
                                </CardContent>
                            </Card>

                            {selectedVersion && repository && (
                                <UsageDetails 
                                    artifact={artifact} 
                                    version={selectedVersion} 
                                    packageManager={repository.repository.packageManager} 
                                />
                            )}
                        </>
                    ) : (
                        <Card className="h-full flex items-center justify-center border-dashed">
                            <CardContent className="text-muted-foreground py-12">
                                Select a version to see details.
                            </CardContent>
                        </Card>
                    )}
                </div>
            </div>
        </div>
    );
}
