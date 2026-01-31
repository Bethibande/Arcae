import {useEffect, useState} from "react";
import {type ColumnDef, getCoreRowModel, type PaginationState, useReactTable,} from "@tanstack/react-table";
import {type UserDTOWithoutPassword, UserEndpointApi, type UserRole} from "@/generated";
import {DataTable} from "@/components/data-table";
import {DataTablePagination} from "@/components/data-table-pagination";
import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {Plus, MoreHorizontal, Pencil, Trash} from "lucide-react";
import {UserEditDialog} from "@/components/user-edit-dialog";
import {ConfirmDialog} from "@/components/confirm-dialog";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {toast} from "sonner";

export default function UserManagementView() {
    const [data, setData] = useState<UserDTOWithoutPassword[]>([]);
    const [loading, setLoading] = useState(true);
    const [pagination, setPagination] = useState<PaginationState>({
        pageIndex: 0,
        pageSize: 10,
    });
    const [totalPages, setTotalPages] = useState(0);
    const [editUser, setEditUser] = useState<UserDTOWithoutPassword | null>(null);
    const [isDialogOpen, setIsDialogOpen] = useState(false);
    const [userToDelete, setUserToDelete] = useState<number | null>(null);
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);

    const fetchData = async () => {
        setLoading(true);
        try {
            const api = new UserEndpointApi();
            const response = await api.apiV1UserGet({
                p: pagination.pageIndex,
                s: pagination.pageSize,
            });
            setData(response.data || []);
            setTotalPages(response.pages || 0);
        } catch (error) {
            console.error("Failed to fetch users", error);
            toast.error("Failed to fetch users");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchData();
    }, [pagination.pageIndex, pagination.pageSize]);

    const deleteUser = async () => {
        if (userToDelete === null) return;

        setIsDeleting(true);
        try {
            const api = new UserEndpointApi();
            await api.apiV1UserDelete({id: userToDelete});
            toast.success("User deleted successfully");
            setIsDeleteDialogOpen(false);
            setUserToDelete(null);
            fetchData();
        } catch (error) {
            console.error("Failed to delete user", error);
            toast.error("Failed to delete user");
        } finally {
            setIsDeleting(false);
        }
    };

    const columns: ColumnDef<UserDTOWithoutPassword>[] = [
        {
            accessorKey: "id",
            header: "ID",
        },
        {
            accessorKey: "name",
            header: "Name",
        },
        {
            accessorKey: "email",
            header: "Email",
        },
        {
            accessorKey: "roles",
            header: "Roles",
            cell: ({row}) => {
                const roles = row.getValue("roles") as UserRole[];
                return (
                    <div className="flex gap-1">
                        {roles.map((role) => (
                            <Badge key={role} variant="outline">
                                {role}
                            </Badge>
                        ))}
                    </div>
                );
            },
        },
        {
            id: "actions",
            cell: ({row}) => {
                const user = row.original;

                return (
                    <div className="text-right">
                        <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                                <Button variant="ghost" className="h-8 w-8 p-0">
                                    <span className="sr-only">Open menu</span>
                                    <MoreHorizontal className="h-4 w-4" />
                                </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                                <DropdownMenuLabel>Actions</DropdownMenuLabel>
                                <DropdownMenuItem
                                    onClick={() => {
                                        setEditUser(user);
                                        setIsDialogOpen(true);
                                    }}
                                >
                                    <Pencil className="mr-2 h-4 w-4" />
                                    Edit
                                </DropdownMenuItem>
                                <DropdownMenuItem
                                    className="text-destructive focus:text-destructive"
                                    onClick={() => {
                                        setUserToDelete(user.id!);
                                        setIsDeleteDialogOpen(true);
                                    }}
                                >
                                    <Trash className="mr-2 h-4 w-4" />
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
        manualPagination: true,
        pageCount: totalPages,
        state: {
            pagination,
        },
        onPaginationChange: setPagination,
    });

    return (
        <div className="p-6 space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-2xl font-bold tracking-tight">User Management</h1>
                    <p className="text-muted-foreground">Manage users and their permissions.</p>
                </div>
                <Button onClick={() => {
                    setEditUser(null);
                    setIsDialogOpen(true);
                }}>
                    <Plus className="mr-2 h-4 w-4" /> Add User
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

            <UserEditDialog
                open={isDialogOpen}
                onOpenChange={setIsDialogOpen}
                user={editUser}
                onSuccess={fetchData}
            />

            <ConfirmDialog
                open={isDeleteDialogOpen}
                onOpenChange={setIsDeleteDialogOpen}
                title="Delete User"
                description="Are you sure you want to delete this user? This action cannot be undone."
                onConfirm={deleteUser}
                confirmText="Delete"
                variant="destructive"
                loading={isDeleting}
            />
        </div>
    );
}
