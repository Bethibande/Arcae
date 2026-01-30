import {useNavigate, useParams, useSearchParams} from "react-router";
import {useEffect, useState} from "react";
import {
    type ArtifactDTO,
    ArtifactEndpointApi,
    type PagedResponseArtifactDTO,
    RepositoryEndpointApi,
    type RepositoryOverviewDTO
} from "@/generated";
import {showError} from "@/lib/errors.ts";
import {ArrowLeft, ChevronRight, Clock, ExternalLink, Package, Search, SortAsc, Tag} from "lucide-react";
import {Input} from "@/components/ui/input.tsx";
import {Button} from "@/components/ui/button.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {Card, CardContent} from "@/components/ui/card.tsx";

export default function RepositoryBrowseView() {
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

        new RepositoryEndpointApi()
            .apiV1RepositoryOverviewIdGet({id: parseInt(id)})
            .then(setRepository)
            .catch(showError);
    }, [id]);

    useEffect(() => {
        if (!id) return;

        setLoading(true);
        new ArtifactEndpointApi()
            .apiV1ArtifactGet({
                r: parseInt(id),
                q: query,
                p: page,
                s: 20,
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
        <div className="flex flex-col gap-8 p-8 max-w-7xl mx-auto w-full">
            {/* Breadcrumbs & Title */}
            <div className="space-y-4">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <span className="cursor-pointer hover:text-foreground"
                          onClick={() => navigate("/")}>Repositories</span>
                    <ChevronRight className="size-4"/>
                    <span className="text-foreground">{repository?.repository.name || "Loading..."}</span>
                    <ChevronRight className="size-4"/>
                    <span className="text-foreground">Browse</span>
                </div>
                <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
                    <div className="flex items-center gap-4">
                        <Button variant="outline" size="icon" onClick={() => navigate("/")}>
                            <ArrowLeft className="size-4"/>
                        </Button>
                        <div>
                            <h1 className="text-3xl font-bold tracking-tight">
                                {repository?.repository.name}
                            </h1>
                            <p className="text-muted-foreground mt-1">
                                Browse and search artifacts in this repository.
                            </p>
                        </div>
                    </div>

                    <div className="flex items-center gap-3">
                        <div className="relative w-full md:w-64">
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

                        <div className="flex items-center gap-2 bg-muted/30 p-1 rounded-lg border">
                            <Select value={sort} onValueChange={handleSort}>
                                <SelectTrigger className="border-none bg-transparent shadow-none h-8 w-[140px]">
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
            <div className="space-y-4">
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
                                      className="group hover:ring-primary/50 transition-all cursor-pointer"
                                      onClick={() => navigate(`/artifacts/${artifact.id}`)}>
                                    <CardContent className="p-4 flex items-center justify-between">
                                        <div className="flex items-center gap-4">
                                            <div className="p-2 rounded-lg bg-primary/10 text-primary">
                                                <Package className="size-5"/>
                                            </div>
                                            <div>
                                                <div className="flex items-center gap-2">
                                                    <span
                                                        className="font-mono text-sm text-muted-foreground">{artifact.groupId}</span>
                                                    <span className="text-muted-foreground">/</span>
                                                    <span className="font-bold">{artifact.artifactId}</span>
                                                </div>
                                                <div
                                                    className="flex items-center gap-3 mt-1 text-xs text-muted-foreground">
                                                    {artifact.latestVersion && (
                                                        <div
                                                            className="flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-primary/10 text-primary font-medium">
                                                            <Tag className="size-3"/>
                                                            <span>{artifact.latestVersion}</span>
                                                        </div>
                                                    )}
                                                    <div className="flex items-center gap-1">
                                                        <Clock className="size-3"/>
                                                        <span>{artifact.lastUpdated ? `Updated ${new Date(artifact.lastUpdated).toLocaleDateString()}` : "Never Updated"}</span>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                        <Button variant="ghost" size="icon">
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
                            <div className="flex justify-center gap-2 mt-8">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    disabled={page === 0}
                                    onClick={() => handlePage(page - 1)}
                                >
                                    Previous
                                </Button>
                                <div className="flex items-center px-4 text-sm font-medium">
                                    Page {page + 1} of {results?.pages}
                                </div>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    disabled={page >= (results?.pages || 1) - 1}
                                    onClick={() => handlePage(page + 1)}
                                >
                                    Next
                                </Button>
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}
