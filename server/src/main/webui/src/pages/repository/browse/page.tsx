import {useNavigate, useParams, useSearchParams} from "react-router";
import {useEffect, useState} from "react";
import {type ArtifactDTO, PackageManager, type PagedResponseArtifactDTO, type RepositoryOverviewDTO} from "@/generated";
import {artifactApi, repositoryApi} from "@/lib/api.ts";
import {showError} from "@/lib/errors.ts";
import {
    ArrowLeft,
    Check,
    ChevronRight,
    Clock,
    Copy,
    ExternalLink,
    Package,
    Search,
    SortAsc,
    Tag,
    Terminal
} from "lucide-react";
import {Input} from "@/components/ui/input.tsx";
import {Button} from "@/components/ui/button.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card.tsx";
import {Tabs, TabsContent, TabsList, TabsTrigger} from "@/components/ui/tabs.tsx";

import {
    Pagination,
    PaginationContent,
    PaginationItem,
    PaginationLink,
    PaginationNext,
    PaginationPrevious
} from "@/components/ui/pagination.tsx";

interface RepositorySnippet {
    name: string;
    language: string;
    content: string;
}

const PAGE_SIZE = 10;

function RepositoryConfigDetails({ repository }: { repository: RepositoryOverviewDTO }) {
    const [copied, setCopied] = useState<string | null>(null);

    const getSnippets = (): RepositorySnippet[] => {
        const baseUrl = `${window.location.origin}/repositories/maven/${repository.repository.name}`;
        if (repository.repository.packageManager === PackageManager.Maven) {
            return [
                {
                    name: "Maven",
                    language: "xml",
                    content: `<repository>\n    <id>${repository.repository.name}</id>\n    <url>${baseUrl}</url>\n</repository>`
                },
                {
                    name: "Gradle (Kotlin)",
                    language: "kotlin",
                    content: `repositories {\n    maven {\n        url = uri("${baseUrl}")\n    }\n}`
                },
                {
                    name: "Gradle (Groovy)",
                    language: "groovy",
                    content: `repositories {\n    maven {\n        url '${baseUrl}'\n    }\n}`
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
                    Repository Configuration
                </CardTitle>
            </CardHeader>
            <CardContent>
                <Tabs defaultValue={snippets[0].name} className="w-full">
                    <TabsList className="mb-4 flex-wrap h-auto">
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
                                <button
                                    className="absolute right-2 top-2 h-8 w-8 flex items-center justify-center rounded-md opacity-0 group-hover:opacity-100 transition-opacity hover:bg-muted"
                                    onClick={() => copyToClipboard(s.content, s.name)}
                                >
                                    {copied === s.name ? <Check className="size-4 text-green-500" /> : <Copy className="size-4" />}
                                </button>
                            </div>
                        </TabsContent>
                    ))}
                </Tabs>
            </CardContent>
        </Card>
    );
}

export default function RepositoryBrowsePage() {
    const {id} = useParams();
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();

    const query = searchParams.get("q") || "";
    const page = parseInt(searchParams.get("p") || "0");
    const sort = searchParams.get("o") || "BEST_MATCH";

    const [repository, setRepository] = useState<RepositoryOverviewDTO | null>(null);
    const [results, setResults] = useState<PagedResponseArtifactDTO | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        if (!id) return;

        repositoryApi.apiV1RepositoryOverviewIdGet({id: parseInt(id)})
            .then(setRepository)
            .catch(showError);
    }, [id]);

    useEffect(() => {
        if (!id) return;

        setLoading(true);
        artifactApi.apiV1ArtifactGet({
                r: parseInt(id),
                q: query,
                p: page,
                s: PAGE_SIZE,
                o: sort as any
            })
            .then(setResults)
            .catch(showError)
            .finally(() => setLoading(false));
    }, [id, query, page, sort]);

    const handleSearch = (newQuery: string) => {
        setSearchParams(prev => {
            if (newQuery) prev.set("q", newQuery);
            else prev.delete("q");
            prev.set("p", "0");
            return prev;
        });
    };

    const handleSort = (newSort: string) => {
        setSearchParams(prev => {
            prev.set("o", newSort);
            return prev;
        });
    };

    const handlePage = (newPage: number) => {
        setSearchParams(prev => {
            prev.set("p", newPage.toString());
            return prev;
        });
    };

    if (!repository && !loading) {
        return <div className="p-8 text-center">Repository not found.</div>;
    }

    return (
        <div className="flex flex-col gap-6 p-4 md:p-8 max-w-screen-xl mx-auto w-full">
            {/* Breadcrumbs & Title */}
            <div className="space-y-4">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <span className="cursor-pointer hover:text-foreground"
                          onClick={() => navigate("/")}>Repositories</span>
                    <ChevronRight className="size-4"/>
                    <span className="text-foreground">{repository?.repository.name || "Loading..."}</span>
                </div>
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                    <div className="flex items-center gap-4">
                        <Button variant="outline" size="icon" onClick={() => navigate("/")}>
                            <ArrowLeft className="size-4"/>
                        </Button>
                        <div>
                            <h1 className="text-2xl md:text-3xl font-bold tracking-tight">
                                {repository?.repository.name}
                            </h1>
                            <p className="text-muted-foreground text-sm">
                                Browse and search artifacts in this repository.
                            </p>
                        </div>
                    </div>

                    <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
                        <div className="relative w-full sm:w-64">
                            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground"/>
                            <Input
                                type="search"
                                placeholder="Search artifacts..."
                                className="pl-9"
                                defaultValue={query}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter") {
                                        handleSearch(e.currentTarget.value);
                                    }
                                }}
                            />
                        </div>

                        <div className="flex items-center gap-2">
                            <Select value={sort} onValueChange={handleSort}>
                                <SelectTrigger className="h-10 w-full sm:w-[160px]">
                                    <SortAsc className="size-4 mr-2 text-muted-foreground"/>
                                    <SelectValue placeholder="Sort by"/>
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="BEST_MATCH">Best Match</SelectItem>
                                    <SelectItem value="LAST_UPDATED">Last Updated</SelectItem>
                                </SelectContent>
                            </Select>
                        </div>
                    </div>
                </div>
            </div>

            {/* Results */}
            <div className="flex flex-col lg:flex-row gap-8">
                <div className="flex-1 min-w-0 space-y-4">
                    {loading ? (
                        <div className="grid grid-cols-1 gap-4">
                            {[1, 2, 3].map(i => (
                                <Card key={i} className="animate-pulse">
                                    <div className="h-24 bg-muted/50 rounded-lg"/>
                                </Card>
                            ))}
                        </div>
                    ) : (
                        <>
                            <div className="flex items-center justify-between text-sm text-muted-foreground">
                                <span>Found {results?.total || 0} artifacts</span>
                            </div>

                            <div className="grid grid-cols-1 gap-4">
                                {results?.data?.map((artifact: ArtifactDTO) => (
                                    <Card key={artifact.id}
                                          className="group hover:ring-1 hover:ring-primary/50 transition-all cursor-pointer shadow-sm"
                                          onClick={() => navigate(`/artifact/${artifact.id}`)}>
                                        <CardContent className="p-3 px-6 flex items-center justify-between">
                                            <div className="flex items-center gap-4 min-w-0">
                                                <div className="p-2 rounded-lg bg-primary/10 text-primary shrink-0">
                                                    <Package className="size-5"/>
                                                </div>
                                                <div className="min-w-0">
                                                    <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                                                        {artifact.groupId && (
                                                            <>
                                                                <span className="font-mono text-sm text-muted-foreground truncate max-w-[200px] md:max-w-xs" title={artifact.groupId}>
                                                                    {artifact.groupId}
                                                                </span>
                                                                <span className="text-muted-foreground">/</span>
                                                            </>
                                                        )}
                                                        <span className="font-bold truncate">{artifact.artifactId}</span>
                                                    </div>
                                                    <div className="flex flex-wrap items-center gap-3 mt-1 text-xs text-muted-foreground">
                                                        {artifact.latestVersion && (
                                                            <div className="flex items-center gap-1">
                                                                <Tag className="size-3"/>
                                                                <span className="font-medium text-primary">{artifact.latestVersion}</span>
                                                            </div>
                                                        )}
                                                        <div className="flex items-center gap-1">
                                                            <Clock className="size-3"/>
                                                            <span>{artifact.lastUpdated ? `Updated ${new Date(artifact.lastUpdated).toLocaleDateString()}` : "Never Updated"}</span>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                            <Button variant="ghost" size="icon" className="shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
                                                <ExternalLink className="size-4"/>
                                            </Button>
                                        </CardContent>
                                    </Card>
                                ))}

                                {results?.data?.length === 0 && (
                                    <div className="py-12 text-center border-2 border-dashed rounded-lg">
                                        <p className="text-muted-foreground">No artifacts found matching your search.</p>
                                        <Button
                                            variant="link"
                                            onClick={() => handleSearch("")}
                                            className="mt-2"
                                        >
                                            Clear search
                                        </Button>
                                    </div>
                                )}
                            </div>

                            {/* Pagination */}
                            {(results?.pages || 0) > 1 && (
                                <Pagination className="mt-8">
                                    <PaginationContent>
                                        <PaginationItem>
                                            <PaginationPrevious
                                                href="#"
                                                onClick={(e) => {
                                                    e.preventDefault();
                                                    if (page > 0) handlePage(page - 1);
                                                }}
                                                className={page === 0 ? "pointer-events-none opacity-50" : "cursor-pointer"}
                                            />
                                        </PaginationItem>

                                        {/* Show pages around current page */}
                                        {Array.from({length: Math.min(5, results?.pages || 0)}, (_, i) => {
                                            const totalPages = results?.pages || 1;
                                            let pageNum;

                                            if (totalPages <= 5) {
                                                pageNum = i;
                                            } else {
                                                if (page <= 2) {
                                                    pageNum = i;
                                                } else if (page >= totalPages - 3) {
                                                    pageNum = totalPages - 5 + i;
                                                } else {
                                                    pageNum = page - 2 + i;
                                                }
                                            }

                                            return (
                                                <PaginationItem key={pageNum}>
                                                    <PaginationLink
                                                        href="#"
                                                        onClick={(e) => {
                                                            e.preventDefault();
                                                            handlePage(pageNum);
                                                        }}
                                                        isActive={page === pageNum}
                                                    >
                                                        {pageNum + 1}
                                                    </PaginationLink>
                                                </PaginationItem>
                                            );
                                        })}

                                        <PaginationItem>
                                            <PaginationNext
                                                href="#"
                                                onClick={(e) => {
                                                    e.preventDefault();
                                                    if (page < (results?.pages || 1) - 1) handlePage(page + 1);
                                                }}
                                                className={page >= (results?.pages || 1) - 1 ? "pointer-events-none opacity-50" : "cursor-pointer"}
                                            />
                                        </PaginationItem>
                                    </PaginationContent>
                                </Pagination>
                            )}
                        </>
                    )}
                </div>

                <div className="lg:w-96 shrink-0 space-y-6">
                    {repository && <RepositoryConfigDetails repository={repository} />}
                </div>
            </div>
        </div>
    );
}
