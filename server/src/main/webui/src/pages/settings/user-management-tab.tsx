import {useCallback, useEffect, useMemo, useState} from "react";
import {Edit, Mail, MoreHorizontal, Plus, Shield, Trash2, User} from "lucide-react";
import {Button} from "@/components/ui/button.tsx";
import {Field, FieldError, FieldLabel} from "@/components/ui/field.tsx";
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
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue,} from "@/components/ui/select.tsx";
import {toast} from "sonner";
import {userApi} from "@/lib/api.ts";
import {showError} from "@/lib/errors.ts";
import {type UserDTOWithoutPassword, UserRole} from "@/generated";
import {type ColumnDef, DataTable} from "@/components/ui/data-table.tsx";
import {Controller, useForm} from "react-hook-form";
import {FormField} from "@/components/form-field.tsx";
import {zodResolver} from "@hookform/resolvers/zod";
import * as z from "zod";
import {Badge} from "@/components/ui/badge.tsx";
import {useAuth} from "@/components/auth-provider.tsx";

const createUserSchema = z.object({
    name: z.string().min(1, "Username is required."),
    email: z.string().email("Invalid email address."),
    password: z.string().min(1, "Password is required."),
    role: z.nativeEnum(UserRole),
});

const editUserSchema = z.object({
    name: z.string().min(1, "Username is required."),
    email: z.string().email("Invalid email address."),
    role: z.nativeEnum(UserRole),
});

type CreateUserFormValues = z.infer<typeof createUserSchema>;
type EditUserFormValues = z.infer<typeof editUserSchema>;

interface CreateUserDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onCreated: () => void;
}

function CreateUserDialog({ open, onOpenChange, onCreated }: CreateUserDialogProps) {
    const [loading, setLoading] = useState(false);
    const { handleSubmit, control, reset, formState } = useForm<CreateUserFormValues>({
        resolver: zodResolver(createUserSchema),
        defaultValues: { name: "", email: "", password: "", role: UserRole.Default },
    });

    const handleCreate = async (values: CreateUserFormValues) => {
        setLoading(true);
        try {
            await userApi.apiV1UserPost({
                userDTOWithoutId: {
                    name: values.name,
                    email: values.email,
                    password: values.password,
                    roles: [values.role],
                }
            });
            toast.success("User created successfully.");
            onCreated();
            onOpenChange(false);
            reset();
        } catch (e) {
            showError(e);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Create User</DialogTitle>
                    <DialogDescription>Add a new user to the system.</DialogDescription>
                </DialogHeader>
                <form onSubmit={handleSubmit(handleCreate)} className="space-y-4">
                    <FormField
                        control={control}
                        fieldName="name"
                        label="Username"
                        placeholder="Username"
                    />
                    <FormField
                        control={control}
                        fieldName="email"
                        label="Email"
                        type="email"
                        placeholder="Email"
                    />
                    <FormField
                        control={control}
                        fieldName="password"
                        label="Password"
                        type="password"
                        autoComplete={"new-password"}
                        placeholder="Password"
                    />
                    <Field>
                        <FieldLabel>Role</FieldLabel>
                        <Controller
                            name="role"
                            control={control}
                            render={({ field }) => (
                                <Select onValueChange={field.onChange} defaultValue={field.value} value={field.value}>
                                    <SelectTrigger>
                                        <SelectValue placeholder="Select a role" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value={UserRole.Default}>User</SelectItem>
                                        <SelectItem value={UserRole.Admin}>Admin</SelectItem>
                                    </SelectContent>
                                </Select>
                            )}
                        />
                        <FieldError errors={[formState.errors.role]} />
                    </Field>
                    <DialogFooter>
                        <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
                        <Button type="submit" disabled={loading}>Create</Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}

interface EditUserDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    user: UserDTOWithoutPassword | null;
    onUpdated: () => void;
}

function EditUserDialog({ open, onOpenChange, user, onUpdated }: EditUserDialogProps) {
    const [loading, setLoading] = useState(false);
    const { user: currentUser } = useAuth();
    const isSelf = currentUser?.id === user?.id;

    const { handleSubmit, control, reset, formState } = useForm<EditUserFormValues>({
        resolver: zodResolver(editUserSchema),
    });

    useEffect(() => {
        if (user) {
            reset({
                name: user.name,
                email: user.email,
                role: user.roles?.[0] ?? UserRole.Default,
            });
        }
    }, [user, reset]);

    const handleUpdate = async (values: EditUserFormValues) => {
        if (!user) return;
        setLoading(true);
        try {
            await userApi.apiV1UserPut({
                userDTOWithoutPassword: {
                    name: values.name,
                    email: values.email,
                    roles: [values.role],
                    id: user.id,
                }
            });
            toast.success("User updated successfully.");
            onUpdated();
            onOpenChange(false);
        } catch (e) {
            showError(e);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Edit User</DialogTitle>
                    <DialogDescription>Update user information and roles.</DialogDescription>
                </DialogHeader>
                <form onSubmit={handleSubmit(handleUpdate)} className="space-y-4">
                    <FormField
                        control={control}
                        fieldName="name"
                        label="Username"
                        placeholder="Username"
                    />
                    <FormField
                        control={control}
                        fieldName="email"
                        label="Email"
                        type="email"
                        placeholder="Email"
                    />
                    <Field>
                        <FieldLabel>Role</FieldLabel>
                        <Controller
                            name="role"
                            control={control}
                            render={({ field }) => (
                                <Select
                                    onValueChange={field.onChange}
                                    defaultValue={field.value}
                                    value={field.value}
                                    disabled={isSelf}
                                >
                                    <SelectTrigger>
                                        <SelectValue placeholder="Select a role" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value={UserRole.Default}>User</SelectItem>
                                        <SelectItem value={UserRole.Admin}>Admin</SelectItem>
                                    </SelectContent>
                                </Select>
                            )}
                        />
                        {isSelf && (
                            <p className="text-[10px] text-muted-foreground mt-1">
                                You cannot change your own role.
                            </p>
                        )}
                        <FieldError errors={[formState.errors.role]} />
                    </Field>
                    <DialogFooter>
                        <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
                        <Button type="submit" disabled={loading}>Save Changes</Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}

interface UserDeleteDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onConfirm: () => void;
    userName: string;
}

function UserDeleteDialog({ open, onOpenChange, onConfirm, userName }: UserDeleteDialogProps) {
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Delete User</DialogTitle>
                    <DialogDescription>
                        Are you sure you want to delete the user <strong>{userName}</strong>? This action cannot be undone.
                    </DialogDescription>
                </DialogHeader>
                <DialogFooter>
                    <Button variant="outline" onClick={() => onOpenChange(false)}>
                        Cancel
                    </Button>
                    <Button variant="destructive" onClick={onConfirm}>
                        Delete
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

export function UserManagementTab() {
    const [users, setUsers] = useState<UserDTOWithoutPassword[]>([]);
    const [loading, setLoading] = useState(false);
    const [createOpen, setCreateOpen] = useState(false);
    const [editOpen, setEditOpen] = useState(false);
    const [selectedUser, setSelectedUser] = useState<UserDTOWithoutPassword | null>(null);
    const [deleteOpen, setDeleteOpen] = useState(false);
    const [userToDelete, setUserToDelete] = useState<UserDTOWithoutPassword | null>(null);

    const loadUsers = useCallback(async () => {
        setLoading(true);
        try {
            const result = await userApi.apiV1UserGet({});
            setUsers(result.data ?? []);
        } catch (e) {
            showError(e);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadUsers();
    }, [loadUsers]);

    const handleDelete = useCallback(async () => {
        if (!userToDelete) return;
        try {
            await userApi.apiV1UserDelete({ id: userToDelete.id! });
            toast.success("User deleted.");
            setDeleteOpen(false);
            loadUsers();
        } catch (e) {
            showError(e);
        }
    }, [userToDelete, loadUsers]);

    const columns = useMemo<ColumnDef<UserDTOWithoutPassword>[]>(() => [
        {
            header: "User",
            cell: (user) => (
                <div className="flex items-center gap-3">
                    <div className="size-8 rounded-full bg-primary/10 flex items-center justify-center text-primary">
                        <User className="size-4" />
                    </div>
                    <div className="flex flex-col">
                        <span className="font-medium">{user.name}</span>
                        <span className="text-xs text-muted-foreground flex items-center gap-1">
                            <Mail className="size-3" /> {user.email}
                        </span>
                    </div>
                </div>
            ),
        },
        {
            header: "Role",
            cell: (user) => {
                const role = user.roles?.[0] ?? UserRole.Default;
                return (
                    <Badge variant={role === UserRole.Admin ? "default" : "secondary"} className="text-[10px] px-1.5 py-0">
                        {role === UserRole.Default && "User"}
                        {role === UserRole.Admin && "Admin"}
                    </Badge>
                );
            },
        },
        {
            header: "",
            className: "w-0",
            cell: (user) => (
                <div className="flex justify-end">
                    <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" className="h-8 w-8">
                                <MoreHorizontal className="size-4" />
                            </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className={"w-fit"}>
                            <DropdownMenuItem onClick={() => { setSelectedUser(user); setEditOpen(true); }}>
                                <Edit className="mr-2 size-4" /> Edit User
                            </DropdownMenuItem>
                            <DropdownMenuItem
                                onClick={() => { setUserToDelete(user); setDeleteOpen(true); }}
                                className="text-destructive focus:text-destructive focus:bg-destructive/10"
                            >
                                <Trash2 className="mr-2 size-4" /> Delete User
                            </DropdownMenuItem>
                        </DropdownMenuContent>
                    </DropdownMenu>
                </div>
            ),
        },
    ], []);

    return (
        <section className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-xl font-semibold flex items-center gap-2">
                        <Shield className="size-5" /> User Management
                    </h2>
                    <p className="text-sm text-muted-foreground mt-1">
                        Manage users, their accounts and roles.
                    </p>
                </div>
                <Button onClick={() => setCreateOpen(true)} className="gap-2">
                    <Plus className="size-4" /> Create User
                </Button>
            </div>

            <DataTable
                columns={columns}
                data={users}
                loading={loading}
                emptyMessage="No users found."
            />

            <CreateUserDialog
                open={createOpen}
                onOpenChange={setCreateOpen}
                onCreated={loadUsers}
            />

            <EditUserDialog
                open={editOpen}
                onOpenChange={setEditOpen}
                user={selectedUser}
                onUpdated={loadUsers}
            />

            <UserDeleteDialog
                open={deleteOpen}
                onOpenChange={setDeleteOpen}
                onConfirm={handleDelete}
                userName={userToDelete?.name ?? ""}
            />
        </section>
    );
}
