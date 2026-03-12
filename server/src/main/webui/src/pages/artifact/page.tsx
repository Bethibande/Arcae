import {useNavigate, useParams, useSearchParams} from "react-router";
import {useEffect, useState} from "react";
import {
    type ArtifactDTO, ArtifactSortOrder,
    type ArtifactVersionDTO,
    type PagedResponseArtifactVersionDTO,
    type RepositoryOverviewDTO
} from "@/generated";
import {artifactApi, repositoryApi} from "@/lib/api";
import {showError} from "@/lib/errors";
import {
    ArrowLeft,
    ChevronRight,
    ExternalLink,
    Globe,
    Info,
    Package,
    Trash2,
    User
} from "lucide-react";
import {Button} from "@/components/ui/button";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {Badge} from "@/components/ui/badge";
import {Tooltip, TooltipContent, TooltipTrigger} from "@/components/ui/tooltip";
import {toast} from "sonner";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "@/components/ui/dialog";
import {VersionHistory} from "@/components/artifact/version-history";
import {UsageDetails} from "@/components/artifact/usage-details";
import {OCIManifestsCard} from "@/components/artifact/oci-manifests-card";
import {PackageManager} from "@/generated";

export default function ArtifactPage() {
    const {id} = useParams();
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();

    const versionPage = parseInt(searchParams.get("p") || "0");
    const versionSearch = searchParams.get("q") || "";

    const [artifact, setArtifact] = useState<ArtifactDTO | null>(null);
    const [repository, setRepository] = useState<RepositoryOverviewDTO | null>(null);
    const [versions, setVersions] = useState<PagedResponseArtifactVersionDTO | null>(null);
    const [selectedVersion, setSelectedVersion] = useState<ArtifactVersionDTO | null>(null);
    const [loading, setLoading] = useState(true);
    const [versionsLoading, setVersionsLoading] = useState(false);
    const [canWrite, setCanWrite] = useState(false);

    const [isDeletingArtifact, setIsDeletingArtifact] = useState(false);
    const [isDeletingVersion, setIsDeletingVersion] = useState(false);
    const [showDeleteArtifactDialog, setShowDeleteArtifactDialog] = useState(false);
    const [showDeleteVersionDialog, setShowDeleteVersionDialog] = useState(false);

    const fetchArtifact = () => {
        if (!id) return;
        setLoading(true);

        artifactApi.apiV1ArtifactIdGet({id: parseInt(id)})
            .then(data => {
                setArtifact(data);
                return Promise.all([
                    repositoryApi.apiV1RepositoryOverviewIdGet({id: data.repositoryId}),
                    repositoryApi.apiV1RepositoryIdCanWriteGet({id: data.repositoryId})
                ]);
            })
            .then(([overview, canWrite]) => {
                setRepository(overview);
                setCanWrite(canWrite);
            })
            .catch(showError)
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        fetchArtifact();
    }, [id]);

    const fetchVersions = () => {
        if (!id) return;

        setVersionsLoading(true);

        artifactApi.apiV1ArtifactIdVersionsSearchGet({
            id: parseInt(id),
            q: versionSearch,
            p: versionPage,
            o: ArtifactSortOrder.LastUpdated,
            s: 5
        })
            .then(data => {
                setVersions(data);
                if (data.data && data.data.length > 0) {
                    if (!selectedVersion || !data.data.find(v => v.id === selectedVersion.id)) {
                        setSelectedVersion(data.data[0]);
                    }
                } else {
                    setSelectedVersion(null);
                }
            })
            .catch(showError)
            .finally(() => setVersionsLoading(false));
    };

    useEffect(() => {
        fetchVersions();
    }, [id, versionPage, versionSearch]);

    const handleDeleteArtifact = () => {
        if (!artifact) return;
        setIsDeletingArtifact(true);
        artifactApi.apiV1ArtifactIdDelete({id: artifact.id!})
            .then(() => {
                toast.success("Artifact deleted successfully");
                navigate(`/repository/${artifact.repositoryId}/browse`);
            })
            .catch(showError)
            .finally(() => {
                setIsDeletingArtifact(false);
                setShowDeleteArtifactDialog(false);
            });
    };

    const handleDeleteVersion = () => {
        if (!selectedVersion || !artifact) return;
        setIsDeletingVersion(true);
        const isLatest = selectedVersion.version === artifact.latestVersion;
        artifactApi.apiV1ArtifactVersionIdDelete({id: selectedVersion.id!})
            .then(() => {
                toast.success("Version deleted successfully");
                if (versions?.total === 1) {
                    navigate(`/repository/${artifact.repositoryId}/browse`);
                } else {
                    fetchVersions();
                    if (isLatest) {
                        fetchArtifact();
                    }
                }
            })
            .catch(showError)
            .finally(() => {
                setIsDeletingVersion(false);
                setShowDeleteVersionDialog(false);
            });
    };

    const handlePage = (newPage: number) => {
        setSearchParams(prev => {
            prev.set("p", newPage.toString());
            return prev;
        });
    };

    const handleSearch = (newSearch: string) => {
        setSearchParams(prev => {
            if (newSearch) prev.set("q", newSearch);
            else prev.delete("q");
            prev.set("p", "0");
            return prev;
        });
    };

    if (loading) {
        return <div className="p-8 text-center animate-pulse text-muted-foreground">Loading artifact...</div>;
    }

    if (!artifact) {
        return <div className="p-8 text-center text-muted-foreground font-medium">Artifact not found.</div>;
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
                          onClick={() => navigate(`/repository/${artifact.repositoryId}/browse`)}>Browse</span>
                    <ChevronRight className="size-4"/>
                    <span className="text-foreground truncate">{artifact.artifactId}</span>
                </div>

                <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
                    <div className="flex items-center gap-4">
                        <Button variant="outline" size="icon" onClick={() => navigate(`/repository/${artifact.repositoryId}/browse`)} className="shrink-0">
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

                    {canWrite && (
                        <Button
                            variant="destructive"
                            size="sm"
                            className="flex items-center gap-2"
                            onClick={() => setShowDeleteArtifactDialog(true)}
                        >
                            <Trash2 className="size-4"/>
                            Delete Artifact
                        </Button>
                    )}
                </div>
            </div>

            <div className="flex flex-col md:flex-row gap-8 items-start">
                {/* Left side: Version Selection */}
                <div className="w-full md:w-80 space-y-6">
                    <VersionHistory
                        versions={versions}
                        selectedVersion={selectedVersion}
                        onSelectVersion={setSelectedVersion}
                        latestVersion={artifact.latestVersion}
                        loading={versionsLoading}
                        page={versionPage}
                        onPageChange={handlePage}
                        search={versionSearch}
                        onSearchChange={handleSearch}
                    />
                </div>

                {/* Right side: Selected Version Details */}
                <div className="flex-1 min-w-0 space-y-6">
                    {selectedVersion ? (
                        <>
                            <Card>
                                <CardHeader>
                                    <CardTitle className="text-xl flex items-center justify-between gap-2 overflow-hidden">
                                        <div className="flex items-center gap-2 truncate">
                                            <Info className="size-5 shrink-0"/>
                                            <span className="truncate">Details for {selectedVersion.version}</span>
                                        </div>
                                        {canWrite && (
                                            <Tooltip>
                                                <TooltipTrigger asChild>
                                                    <Button
                                                        variant="ghost"
                                                        size="icon"
                                                        className="text-destructive hover:text-destructive hover:bg-destructive/10 shrink-0"
                                                        onClick={() => setShowDeleteVersionDialog(true)}
                                                    >
                                                        <Trash2 className="size-4"/>
                                                    </Button>
                                                </TooltipTrigger>
                                                <TooltipContent>
                                                    Delete Version
                                                </TooltipContent>
                                            </Tooltip>
                                        )}
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
                                    repository={repository}
                                />
                            )}

                            {selectedVersion && repository?.repository.packageManager === PackageManager.Oci && (
                                <OCIManifestsCard version={selectedVersion} />
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

            <Dialog open={showDeleteArtifactDialog} onOpenChange={setShowDeleteArtifactDialog}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Delete Artifact</DialogTitle>
                        <DialogDescription>
                            Are you sure you want to delete the artifact <strong>{artifact.groupId}:{artifact.artifactId}</strong>? This action cannot be undone.
                        </DialogDescription>
                    </DialogHeader>
                    <DialogFooter>
                        <Button variant="ghost" onClick={() => setShowDeleteArtifactDialog(false)} disabled={isDeletingArtifact}>
                            Cancel
                        </Button>
                        <Button variant="destructive" onClick={handleDeleteArtifact} disabled={isDeletingArtifact}>
                            {isDeletingArtifact ? "Deleting..." : "Delete Artifact"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            <Dialog open={showDeleteVersionDialog} onOpenChange={setShowDeleteVersionDialog}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>Delete Version</DialogTitle>
                        <DialogDescription>
                            Are you sure you want to delete version <strong>{selectedVersion?.version}</strong> of {artifact.artifactId}? This action cannot be undone.
                        </DialogDescription>
                    </DialogHeader>
                    <DialogFooter>
                        <Button variant="ghost" onClick={() => setShowDeleteVersionDialog(false)} disabled={isDeletingVersion}>
                            Cancel
                        </Button>
                        <Button variant="destructive" onClick={handleDeleteVersion} disabled={isDeletingVersion}>
                            {isDeletingVersion ? "Deleting..." : "Delete Version"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}
