import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {Button} from "@/components/ui/button";
import {DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,} from "@/components/ui/dropdown-menu";
import {Anchor, Box, Clock, Coffee, ExternalLink, type LucideIcon, MoreHorizontal, Pencil, Trash2} from "lucide-react";
import {cn, formatLastUpdate} from "@/lib/utils";
import {PackageManager, type RepositoryOverviewDTO, UserRole} from "@/generated";
import {useNavigate} from "react-router";
import {useState} from "react";
import {useAuth} from "@/components/auth-provider";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "@/components/ui/dialog";
import {showError, showErrorMessage} from "@/lib/errors";
import {repositoryApi} from "@/lib/api";

const packageBG: Record<PackageManager, string> = {
    [PackageManager.Maven]: "bg-red-500/10",
    [PackageManager.Oci]: "bg-blue-500/10",
    [PackageManager.Helm]: "bg-cyan-500/10"
}
const packageBGPrimary: Record<PackageManager, string> = {
    [PackageManager.Maven]: "bg-red-600",
    [PackageManager.Oci]: "bg-blue-600",
    [PackageManager.Helm]: "bg-cyan-600"
}

const packageFG: Record<PackageManager, string> = {
    [PackageManager.Maven]: "text-red-500",
    [PackageManager.Oci]: "text-blue-500",
    [PackageManager.Helm]: "text-cyan-500"
}

const packageIcon: Record<PackageManager, LucideIcon> = {
    [PackageManager.Maven]: Coffee,
    [PackageManager.Oci]: Box,
    [PackageManager.Helm]: Anchor
}

interface RepositoryCardProps {
    repository: RepositoryOverviewDTO;
    onDelete?: () => void;
}

export function RepositoryCard({ repository: overview, onDelete }: RepositoryCardProps) {
    const {user} = useAuth();
    const navigate = useNavigate();
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
    const isMaintenance = false;
    const repo = overview.repository;
    const Icon = packageIcon[repo.packageManager] || Coffee;

    const updatedString = overview.lastUpdated && formatLastUpdate(overview.lastUpdated) || "Never Updated";

    const hasRole = (role: UserRole) => {
        return user?.roles?.includes(role);
    };

    const handleDelete = async () => {
        setIsDeleting(true);
        try {
            await repositoryApi.apiV1RepositoryIdDelete({ id: repo.id! });
            setIsDeleteDialogOpen(false);
            onDelete?.();
        } catch (err: unknown) {
            // @ts-expect-error - err is unknown, but we check for response status
            if (err && typeof err === 'object' && 'response' in err && err.response?.status === 409) {
                showErrorMessage("Repository is not empty")
            } else {
                showError(err);
            }
        } finally {
            setIsDeleting(false);
        }
    }

    return (
        <>
            <Card key={repo.name}
                  onClick={() => !isMaintenance && navigate(`/repository/${repo.id}/browse`)}
                  className={cn(
                      "relative group hover:ring-primary/50 transition-all",
                      isMaintenance ? "cursor-default opacity-80" : "cursor-pointer"
                  )}>
                <CardHeader className="flex flex-row items-start justify-between">
                    <div className={cn("p-2 rounded-lg", packageBG[repo.packageManager] || "bg-muted")}>
                        <Icon className={cn("size-5", packageFG[repo.packageManager] || "text-muted-foreground")}/>
                    </div>
                    {hasRole(UserRole.Admin) && (
                        <DropdownMenu>
                            <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
                                <Button variant="ghost" size="icon" className="h-8 w-8 text-muted-foreground p-0">
                                    <MoreHorizontal className="size-4"/>
                                </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                                <DropdownMenuItem onClick={(e) => { e.stopPropagation(); navigate(`/repository/${repo.id}/browse`); }}>
                                    <ExternalLink className="size-4 mr-2"/>
                                    Browse
                                </DropdownMenuItem>
                                <DropdownMenuItem onClick={(e) => { e.stopPropagation(); navigate(`/repository/${repo.id}/settings`); }}>
                                    <Pencil className="size-4 mr-2"/>
                                    Settings
                                </DropdownMenuItem>
                                <DropdownMenuItem variant="destructive" onClick={(e) => { e.stopPropagation(); setIsDeleteDialogOpen(true); }}>
                                    <Trash2 className="size-4 mr-2"/>
                                    Delete
                                </DropdownMenuItem>
                            </DropdownMenuContent>
                        </DropdownMenu>
                    )}
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

                    <div className="flex items-center justify-between pt-2 border-t">
                        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                            <Clock className="size-3.5"/>
                            <span>{updatedString}</span>
                        </div>
                    </div>
                </CardContent>
            </Card>

            <Dialog open={isDeleteDialogOpen} onOpenChange={setIsDeleteDialogOpen}>
                <DialogContent onClick={(e) => e.stopPropagation()}>
                    <DialogHeader>
                        <DialogTitle>Delete Repository</DialogTitle>
                        <DialogDescription>
                            Are you sure you want to delete <strong>{repo.name}</strong>? This action cannot be undone.
                            The repository must be empty to be deleted.
                        </DialogDescription>
                    </DialogHeader>
                    <DialogFooter>
                        <Button variant="ghost" onClick={() => setIsDeleteDialogOpen(false)} disabled={isDeleting}>
                            Cancel
                        </Button>
                        <Button variant="destructive" onClick={handleDelete} disabled={isDeleting}>
                            {isDeleting ? "Deleting..." : "Delete Repository"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </>
    );
}
