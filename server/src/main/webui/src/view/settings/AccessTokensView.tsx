import {useEffect, useState} from "react";
import {type ColumnDef, getCoreRowModel, useReactTable} from "@tanstack/react-table";
import {type AccessTokenDTOWithoutToken} from "@/generated";
import {accessTokenApi} from "@/lib/api.ts";
import {DataTable} from "@/components/data-table";
import {DataTablePagination} from "@/components/data-table-pagination";
import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {MoreHorizontal, Pencil, Plus, Trash} from "lucide-react";
import {AccessTokenEditDialog} from "@/components/access-token-edit-dialog";
import {ConfirmDialog} from "@/components/confirm-dialog";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {toast} from "sonner";
import {showError} from "@/lib/errors.ts";

export default function AccessTokensView() {
    const [data, setData] = useState<AccessTokenDTOWithoutToken[]>([]);
    const [loading, setLoading] = useState(true);
    const [editToken, setEditToken] = useState<AccessTokenDTOWithoutToken | null>(null);
    const [isDialogOpen, setIsDialogOpen] = useState(false);
    const [tokenToDelete, setTokenToDelete] = useState<number | null>(null);
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);

    const fetchData = async () => {
        setLoading(true);
        try {
            const response = await accessTokenApi.apiV1TokensGet();
            setData(response || []);
        } catch (error) {
            console.error("Failed to fetch tokens", error);
            toast.error("Failed to fetch tokens");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchData().catch(showError);
    }, []);

    const deleteToken = async () => {
        if (tokenToDelete === null) return;

        setIsDeleting(true);
        try {
            await accessTokenApi.apiV1TokensIdDelete({id: tokenToDelete});
            toast.success("Token deleted successfully");
            setIsDeleteDialogOpen(false);
            setTokenToDelete(null);
            fetchData();
        } catch (error) {
            console.error("Failed to delete token", error);
            toast.error("Failed to delete token");
        } finally {
            setIsDeleting(false);
        }
    };

    const columns: ColumnDef<AccessTokenDTOWithoutToken>[] = [
        {
            accessorKey: "name",
            header: "Name",
        },
        {
            accessorKey: "expiresAfter",
            header: "Expires",
            cell: ({row}) => {
                const expires = row.getValue("expiresAfter") as string | undefined;
                if (!expires) return "Never";
                return new Date(expires).toLocaleString();
            },
        },
        {
            id: "status",
            header: "Status",
            cell: ({row}) => {
                const expires = row.original.expiresAfter;
                const isExpired = expires && new Date(expires) < new Date();
                return (
                    <Badge variant={isExpired ? "destructive" : "outline"} className={isExpired ? "" : "border-green-500/50 text-green-500 bg-green-500/10"}>
                        {isExpired ? "Expired" : "Active"}
                    </Badge>
                );
            }
        },
        {
            id: "actions",
            cell: ({row}) => {
                const token = row.original;

                return (
                    <div className="text-right">
                        <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                                <Button variant="ghost" className="h-8 w-8 p-0">
                                    <span className="sr-only">Open menu</span>
                                    <MoreHorizontal className="h-4 w-4"/>
                                </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                                <DropdownMenuLabel>Actions</DropdownMenuLabel>
                                <DropdownMenuItem
                                    onClick={() => {
                                        setEditToken(token);
                                        setIsDialogOpen(true);
                                    }}
                                >
                                    <Pencil className="mr-2 h-4 w-4"/>
                                    Edit
                                </DropdownMenuItem>
                                <DropdownMenuItem
                                    className="text-destructive focus:text-destructive"
                                    onClick={() => {
                                        setTokenToDelete(token.id!);
                                        setIsDeleteDialogOpen(true);
                                    }}
                                >
                                    <Trash className="mr-2 h-4 w-4"/>
                                    Delete
                                </DropdownMenuItem>
                            </DropdownMenuContent>
                        </DropdownMenu>
                    </div>
                );
            },
        },
    ];

    const table = useReactTable({
        data,
        columns,
        getCoreRowModel: getCoreRowModel(),
    });

    return (
        <div className="p-8 space-y-8 max-w-7xl mx-auto">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold tracking-tight">Access Tokens</h1>
                    <p className="text-muted-foreground mt-1">Manage your personal access tokens for API access.</p>
                </div>
                <Button onClick={() => {
                    setEditToken(null);
                    setIsDialogOpen(true);
                }}>
                    <Plus className="mr-2 h-4 w-4"/> Generate Token
                </Button>
            </div>

            {loading ? (
                <div>Loading...</div>
            ) : (
                <div className="space-y-4">
                    <DataTable columns={columns} data={data}/>
                    <DataTablePagination table={table}/>
                </div>
            )}

            <AccessTokenEditDialog
                open={isDialogOpen}
                onOpenChange={setIsDialogOpen}
                token={editToken}
                onSuccess={fetchData}
            />

            <ConfirmDialog
                open={isDeleteDialogOpen}
                onOpenChange={setIsDeleteDialogOpen}
                title="Delete Token"
                description="Are you sure you want to delete this token? This action cannot be undone."
                onConfirm={deleteToken}
                confirmText="Delete"
                variant="destructive"
                loading={isDeleting}
            />
        </div>
    );
}
