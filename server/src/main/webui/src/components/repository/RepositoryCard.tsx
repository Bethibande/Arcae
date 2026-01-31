import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card.tsx";
import {Button} from "@/components/ui/button.tsx";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu.tsx";
import {Clock, Coffee, ExternalLink, MoreHorizontal, Pencil, Trash2} from "lucide-react";
import {cn} from "@/lib/utils.ts";
import {PackageManager, type RepositoryOverviewDTO} from "@/generated";
import {useNavigate} from "react-router";

const packageBG: Record<PackageManager, string> = {
    [PackageManager.Maven3]: "bg-red-500/10"
}
const packageBGPrimary: Record<PackageManager, string> = {
    [PackageManager.Maven3]: "bg-red-600"
}

const packageFG: Record<PackageManager, string> = {
    [PackageManager.Maven3]: "text-red-500"
}

const packageIcon: Record<PackageManager, any> = {
    [PackageManager.Maven3]: Coffee
}

interface RepositoryCardProps {
    repository: RepositoryOverviewDTO;
}

export function RepositoryCard({ repository: overview }: RepositoryCardProps) {
    const navigate = useNavigate();
    const isMaintenance = false;
    const repo = overview.repository;
    const Icon = packageIcon[repo.packageManager] || Coffee;

    const updated = overview.lastUpdated ? (new Date().getTime() - overview.lastUpdated.getTime()) : 0;
    const seconds = Math.floor(updated / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    const updatedString = days > 0 ? `Updated ${days} days ago` : (hours > 0 ? `Updated ${hours} hours ago` : (minutes > 0 ? `Updated ${minutes} minutes ago` : (seconds > 0 ? `Updated ${seconds} seconds ago` : "Never Updated")));

    return (
        <Card key={repo.name}
              onClick={() => !isMaintenance && navigate(`/repositories/${repo.id}/browse`)}
              className={cn(
                  "relative group hover:ring-primary/50 transition-all",
                  isMaintenance ? "cursor-default opacity-80" : "cursor-pointer"
              )}>
            <CardHeader className="flex flex-row items-start justify-between">
                <div className={cn("p-2 rounded-lg", packageBG[repo.packageManager] || "bg-muted")}>
                    <Icon className={cn("size-5", packageFG[repo.packageManager] || "text-muted-foreground")}/>
                </div>
                <DropdownMenu>
                    <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
                        <Button variant="ghost" size="icon-xs" className="text-muted-foreground">
                            <MoreHorizontal className="size-4"/>
                        </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={(e) => { e.stopPropagation(); navigate(`/repositories/${repo.id}/browse`); }}>
                            <ExternalLink className="size-4 mr-2"/>
                            Browse
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={(e) => { e.stopPropagation(); navigate(`/repositories/${repo.id}/edit`); }}>
                            <Pencil className="size-4 mr-2"/>
                            Edit
                        </DropdownMenuItem>
                        <DropdownMenuItem variant="destructive" onClick={(e) => e.stopPropagation()}>
                            <Trash2 className="size-4 mr-2"/>
                            Delete
                        </DropdownMenuItem>
                    </DropdownMenuContent>
                </DropdownMenu>
            </CardHeader>
            <CardContent className="space-y-4">
                <div>
                    <CardTitle className="text-lg font-semibold">{repo.name}</CardTitle>
                    <div className="flex items-center gap-2 mt-2">
                        <span
                            className={cn("text-[10px] font-bold px-1.5 py-0.5 rounded text-white", packageBGPrimary[repo.packageManager] || "bg-muted")}>
                            {repo.packageManager}
                        </span>
                        <span className="text-xs text-muted-foreground">• {overview.artifactsCount} {overview.artifactsCount === 1 ? 'Artifact' : 'Artifacts'}</span>
                    </div>
                </div>

                <div className="flex items-center justify-between pt-2 border-t border-border/50">
                    <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                        <Clock className="size-3.5"/>
                        <span>{updatedString}</span>
                    </div>
                </div>
            </CardContent>
        </Card>
    );
}
