import {useEffect, useMemo, useState} from "react";
import {Monitor, MoreHorizontal, Trash2} from "lucide-react";
import {Button} from "@/components/ui/button.tsx";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog.tsx";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu.tsx";
import {toast} from "sonner";
import {authApi} from "@/lib/api.ts";
import {showError} from "@/lib/errors.ts";
import {type ColumnDef, DataTable} from "@/components/ui/data-table.tsx";
import {format, isBefore} from "date-fns";
import type {ActiveUserSession} from "@/generated";

interface SessionDeleteDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onConfirm: () => void;
}

function SessionDeleteDialog({ open, onOpenChange, onConfirm }: SessionDeleteDialogProps) {
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Revoke Session</DialogTitle>
                    <DialogDescription>
                        Are you sure you want to revoke this session? You will be logged out on that device.
                    </DialogDescription>
                </DialogHeader>
                <DialogFooter>
                    <Button variant="outline" onClick={() => onOpenChange(false)}>
                        Cancel
                    </Button>
                    <Button variant="destructive" onClick={onConfirm}>
                        Revoke
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

export function SessionsTab() {
    const [sessions, setSessions] = useState<ActiveUserSession[]>([]);
    const [loading, setLoading] = useState(false);
    const [deleteSession, setDeleteSession] = useState<ActiveUserSession | null>(null);
    const [deleteOpen, setDeleteOpen] = useState(false);

    useEffect(() => {
        loadSessions();
    }, []);

    const loadSessions = async () => {
        setLoading(true);
        try {
            const result = await authApi.apiV1AuthSessionsGet();
            setSessions(result);
        } catch (e) {
            showError(e);
        } finally {
            setLoading(false);
        }
    };

    const handleDelete = async () => {
        if (!deleteSession) return;
        try {
            await authApi.apiV1AuthSessionIdDelete({ id: deleteSession.id! });
            setSessions((prev) => prev.filter((s) => s.id !== deleteSession.id));
            setDeleteOpen(false);
            toast.success("Session revoked.");
        } catch (e) {
            showError(e);
        }
    };

    const columns = useMemo<ColumnDef<ActiveUserSession>[]>(() => [
        {
            header: "IP Address",
            accessorKey: "address",
            cell: (session) => (
                <div className="flex items-center gap-3">
                    <div className="size-8 rounded-full bg-primary/10 flex items-center justify-center text-primary">
                        <Monitor className="size-4" />
                    </div>
                    <span className="font-medium font-mono text-xs">{session.address}</span>
                </div>
            ),
        },
        {
            header: "Created",
            cell: (session) => (
                <span className="text-muted-foreground font-medium">
                    {session.created ? format(session.created, "PPP p") : "Unknown"}
                </span>
            ),
        },
        {
            header: "Expires",
            cell: (session) => (
                <span className="text-muted-foreground font-medium">
                    {session.expiresAfter ? format(session.expiresAfter, "PPP p") : "Never"}
                </span>
            ),
        },
        {
            header: "Status",
            cell: (session) => {
                const isExpired = session.expiresAfter && isBefore(session.expiresAfter, new Date());
                if (session.current) {
                    return (
                        <div className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-primary/10 text-primary border border-primary/20">
                            Current
                        </div>
                    );
                }
                return isExpired ? (
                    <div className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-destructive/10 text-destructive border border-destructive/20">
                        Expired
                    </div>
                ) : (
                    <div className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-500 border border-emerald-500/20">
                        Active
                    </div>
                );
            },
        },
        {
            header: "",
            cell: (session) => (
                <div className="flex justify-end">
                    <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" className="h-8 w-8">
                                <MoreHorizontal className="size-4" />
                            </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className={"w-fit"}>
                            <DropdownMenuItem
                                onClick={() => {
                                    setDeleteSession(session);
                                    setDeleteOpen(true);
                                }}
                                className="text-destructive focus:text-destructive focus:bg-destructive/10 cursor-pointer"
                            >
                                <Trash2 className="mr-2 size-4" />
                                Revoke Session
                            </DropdownMenuItem>
                        </DropdownMenuContent>
                    </DropdownMenu>
                </div>
            ),
        },
    ], []);

    return (
        <section className="space-y-6">
            <div>
                <h2 className="text-xl font-semibold flex items-center gap-2">
                    <Monitor className="size-5" /> Active Sessions
                </h2>
                <p className="text-sm text-muted-foreground mt-1">
                    This is a list of devices that have logged into your account. Revoke any sessions that you do not recognize.
                </p>
            </div>

            <DataTable
                columns={columns}
                data={sessions}
                loading={loading}
                emptyMessage="No active sessions found."
            />

            <SessionDeleteDialog
                open={deleteOpen}
                onOpenChange={setDeleteOpen}
                onConfirm={handleDelete}
            />
        </section>
    );
}
