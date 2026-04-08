import {Button} from "@/components/ui/button";
import {DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger} from "@/components/ui/dropdown-menu";
import {Anchor, Box, Clock, Coffee, ExternalLink, Lock, type LucideIcon, MoreHorizontal, Pencil, Trash2} from "lucide-react";
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

interface RepositoryRowProps {
    repository: RepositoryOverviewDTO;
    onDelete?: () => void;
}

export function RepositoryRow({ repository: overview, onDelete }: RepositoryRowProps) {
    const {user} = useAuth();
    const navigate = useNavigate();
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
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
            <div
                onClick={() => navigate(`/repository/${repo.id}/browse`)}
                className="group flex items-center justify-between p-4 bg-card border rounded-xl hover:ring-1 hover:ring-primary/50 transition-all cursor-pointer"
            >
                <div className="flex items-center gap-4 flex-1">
                    <div className={cn("p-2.5 rounded-lg", packageBG[repo.packageManager] || "bg-muted")}>
                        <Icon className={cn("size-5", packageFG[repo.packageManager] || "text-muted-foreground")}/>
                    </div>
                    <div>
                        <div className="flex items-center gap-2">
                            <h3 className="font-semibold text-lg leading-tight">{repo.name}</h3>
                            {!repo.publicAccessAllowed && (
                                <Lock className="size-4 text-muted-foreground" />
                            )}
                        </div>
                        <p className="text-sm text-muted-foreground">
                            {repo.publicAccessAllowed ? "Public Repository" : "Private Repository"}
                        </p>
                    </div>
                </div>

                <div className="flex items-center gap-8 text-sm">
                    <div className="w-32 flex justify-center mr-12">
                        <span className={cn("text-[10px] font-bold px-2 py-0.5 rounded text-white uppercase", packageBGPrimary[repo.packageManager] || "bg-muted")}>
                            {repo.packageManager}
                        </span>
                    </div>

                    <div className="w-24 text-center">
                        <span className="font-medium">{overview.artifactsCount}</span>
                    </div>

                    <div className="w-40 flex items-center gap-2 text-muted-foreground justify-start">
                        <Clock className="size-4 shrink-0"/>
                        <span>{updatedString}</span>
                    </div>

                    <div className="w-10 flex justify-center">
                        {hasRole(UserRole.Admin) && (
                            <DropdownMenu>
                                <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
                                    <Button variant="ghost" size="icon" className="h-9 w-9 text-muted-foreground">
                                        <MoreHorizontal className="size-5"/>
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
                    </div>
                </div>
            </div>

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
