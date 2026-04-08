import {Card} from "@/components/ui/card";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select";
import {ChevronRight, LayoutGrid, List, Plus} from "lucide-react";
import {useEffect, useState} from "react";
import {type RepositoryOverviewDTO, RepositorySortOrder, UserRole} from "@/generated";
import {repositoryApi} from "@/lib/api";
import {showError} from "@/lib/errors";
import {RepositoryCard} from "@/components/repository/repository-card";
import {RepositoryRow} from "@/components/repository/repository-row";
import {useNavigate} from "react-router";
import {useAuth} from "@/components/auth-provider";
import {ToggleGroup, ToggleGroupItem} from "@/components/ui/toggle-group";

export default function DashboardPage() {
    const {user} = useAuth();
    const [repositories, setRepositories] = useState<RepositoryOverviewDTO[]>([])
    const [sortOrder, setSortOrder] = useState<RepositorySortOrder>(() => {
        const saved = localStorage.getItem("repository_sort_order");
        if (saved && Object.values(RepositorySortOrder).includes(saved as RepositorySortOrder)) {
            return saved as RepositorySortOrder;
        }
        return RepositorySortOrder.Alphabetical;
    })
    const [viewMode, setViewMode] = useState<"grid" | "list">(() => {
        const saved = localStorage.getItem("repository_view_mode");
        return (saved === "list" || saved === "grid") ? saved : "grid";
    });
    const navigate = useNavigate();

    const fetchRepositories = (order: RepositorySortOrder) => {
        repositoryApi.apiV1RepositoryOverviewGet({ o: order })
            .then(setRepositories)
            .catch(showError)
    };

    useEffect(() => {
        fetchRepositories(sortOrder);
        localStorage.setItem("repository_sort_order", sortOrder);
    }, [sortOrder])

    useEffect(() => {
        localStorage.setItem("repository_view_mode", viewMode);
    }, [viewMode]);

    const hasRole = (role: UserRole) => {
        return user?.roles?.includes(role);
    };

    return (
        <div className="flex flex-col gap-8 p-8 max-w-7xl mx-auto">
            {/* Breadcrumbs & Title */}
            <div className="space-y-4">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <span>Repositories</span>
                    <ChevronRight className="size-4"/>
                    <span className="text-foreground">Overview</span>
                </div>
                <div className="flex justify-between items-end">
                    <div>
                        <h1 className="text-3xl font-bold tracking-tight">Repositories</h1>
                        <p className="text-muted-foreground mt-1">
                            Manage and monitor {repositories.length} active repositories.
                        </p>
                    </div>

                    <div className="flex items-center gap-3">
                        <div className="flex items-center gap-2 bg-muted/30 p-1 rounded-lg border">
                            <Select value={sortOrder} onValueChange={(v) => setSortOrder(v as RepositorySortOrder)}>
                                <SelectTrigger className="border-none bg-transparent shadow-none h-8">
                                    <span className="text-muted-foreground mr-1 text-xs">Sort:</span>
                                    <SelectValue placeholder="Alphabetical"/>
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value={RepositorySortOrder.Alphabetical}>Alphabetical</SelectItem>
                                    <SelectItem value={RepositorySortOrder.LastUpdated}>Last Updated</SelectItem>
                                    <SelectItem value={RepositorySortOrder.ArtifactCount}>Artifact Count</SelectItem>
                                </SelectContent>
                            </Select>
                        </div>

                        <ToggleGroup type="single" value={viewMode} onValueChange={(v) => v && setViewMode(v as "grid" | "list")} className="bg-muted/30 p-1 rounded-lg border">
                            <ToggleGroupItem value="grid" size="sm" className="h-8 w-8 p-0">
                                <LayoutGrid className="size-4"/>
                            </ToggleGroupItem>
                            <ToggleGroupItem value="list" size="sm" className="h-8 w-8 p-0">
                                <List className="size-4"/>
                            </ToggleGroupItem>
                        </ToggleGroup>
                    </div>
                </div>
            </div>

            {/* Repositories */}
            {viewMode === "grid" ? (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                    {repositories.map((repo) => (
                        <RepositoryCard key={repo.repository.name} repository={repo} onDelete={() => fetchRepositories(sortOrder)}/>
                    ))}

                    {/* Add New Repository (Grid) */}
                    {hasRole(UserRole.Admin) && (
                        <Card
                            onClick={() => navigate("/repository/new")}
                            className="border-dashed border-2 bg-transparent ring-0 shadow-none flex flex-col items-center justify-center min-h-[200px] gap-4 group hover:border-primary/50 transition-all cursor-pointer">
                            <div className="p-3 rounded-full bg-muted/50 group-hover:bg-primary/10 transition-colors">
                                <Plus className="size-6 text-muted-foreground group-hover:text-primary"/>
                            </div>
                            <span
                                className="font-medium text-muted-foreground group-hover:text-foreground">Add New Repository</span>
                        </Card>
                    )}
                </div>
            ) : (
                <div className="flex flex-col gap-4">
                    {/* List Header */}
                    <div className="flex items-center px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider h-10">
                        <div className="flex-1">Repository</div>
                        <div className="flex items-center gap-8">
                            <div className="w-32 text-center mr-12">Type</div>
                            <div className="w-24 text-center">Artifacts</div>
                            <div className="w-40 text-start">Last Updated</div>
                            <div className="w-10"></div>
                        </div>
                    </div>

                    {repositories.map((repo) => (
                        <RepositoryRow key={repo.repository.name} repository={repo} onDelete={() => fetchRepositories(sortOrder)}/>
                    ))}

                    {/* Add New Repository (List) */}
                    {hasRole(UserRole.Admin) && (
                        <div
                            onClick={() => navigate("/repository/new")}
                            className="flex items-center justify-center p-4 border-2 border-dashed rounded-xl bg-transparent gap-3 group hover:border-primary/50 transition-all cursor-pointer">
                            <Plus className="size-5 text-muted-foreground group-hover:text-primary"/>
                            <span className="font-medium text-muted-foreground group-hover:text-foreground">Add New Repository</span>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
