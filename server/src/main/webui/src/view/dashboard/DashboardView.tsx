import {Card} from "@/components/ui/card.tsx";
import {Button} from "@/components/ui/button.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {ChevronRight, LayoutGrid, List, Plus,} from "lucide-react";
import {useEffect, useState} from "react";
import {RepositoryEndpointApi, type RepositoryOverviewDTO} from "@/generated";
import {showError} from "@/lib/errors.ts";
import {RepositoryCard} from "@/components/repository/RepositoryCard.tsx";
import {useNavigate} from "react-router";

export default function DashboardView() {
    const [repositories, setRepositories] = useState<RepositoryOverviewDTO[]>([])
    const navigate = useNavigate();
    useEffect(() => {
        new RepositoryEndpointApi()
            .apiV1RepositoryOverviewGet()
            .then(setRepositories)
            .catch(showError)
    }, [])

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

                    { /* Hidden until we implement this */}
                    <div className="flex items-center gap-3 hidden">
                        <div className="flex items-center gap-2 bg-muted/30 p-1 rounded-lg border">
                            <Select defaultValue="all">
                                <SelectTrigger className="border-none bg-transparent shadow-none h-8">
                                    <span className="text-muted-foreground mr-1">Status:</span>
                                    <SelectValue placeholder="All"/>
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="all">All</SelectItem>
                                    <SelectItem value="healthy">Healthy</SelectItem>
                                    <SelectItem value="vulnerable">Vulnerable</SelectItem>
                                </SelectContent>
                            </Select>
                        </div>

                        <div className="flex items-center gap-2 bg-muted/30 p-1 rounded-lg border">
                            <Select defaultValue="public">
                                <SelectTrigger className="border-none bg-transparent shadow-none h-8">
                                    <span className="text-muted-foreground mr-1">Visibility:</span>
                                    <SelectValue placeholder="Public"/>
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value="public">Public</SelectItem>
                                    <SelectItem value="private">Private</SelectItem>
                                </SelectContent>
                            </Select>
                        </div>

                        <div className="flex items-center bg-muted/30 p-1 rounded-lg border">
                            <Button variant="ghost" size="icon-sm" className="bg-primary/10 text-primary">
                                <LayoutGrid className="size-4"/>
                            </Button>
                            <Button variant="ghost" size="icon-sm" className="text-muted-foreground">
                                <List className="size-4"/>
                            </Button>
                        </div>
                    </div>
                </div>
            </div>

            {/* Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                {repositories.map((repo) => (
                    <RepositoryCard key={repo.repository.name} repository={repo}/>
                ))}

                {/* Add New Repository */}
                <Card
                    onClick={() => navigate("/repositories/new")}
                    className="border-dashed border-2 bg-transparent ring-0 shadow-none flex flex-col items-center justify-center min-h-[200px] gap-4 group hover:border-primary/50 transition-all cursor-pointer">
                    <div className="p-3 rounded-full bg-muted/50 group-hover:bg-primary/10 transition-colors">
                        <Plus className="size-6 text-muted-foreground group-hover:text-primary"/>
                    </div>
                    <span
                        className="font-medium text-muted-foreground group-hover:text-foreground">Add New Repository</span>
                </Card>
            </div>
        </div>
    );
}
