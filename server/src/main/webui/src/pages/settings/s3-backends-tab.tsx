import {useCallback, useEffect, useMemo, useState} from "react";
import {Database, Edit, MoreHorizontal, Plus, Trash2} from "lucide-react";
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
import {s3BackendApi} from "@/lib/api.ts";
import {showError} from "@/lib/errors.ts";
import {type S3RepositoryBackendDTO} from "@/generated";
import {type ColumnDef, DataTable} from "@/components/ui/data-table.tsx";
import {useForm} from "react-hook-form";
import {FormField} from "@/components/form-field.tsx";
import {zodResolver} from "@hookform/resolvers/zod";
import * as z from "zod";

const backendSchema = z.object({
    name: z.string().min(1, "Name is required."),
    uri: z.string().url("Invalid host URI."),
    bucket: z.string().min(1, "Bucket name is required."),
    region: z.string().min(1, "Region is required."),
    accessKey: z.string().min(1, "Access Key is required."),
    secretKey: z.string().min(1, "Secret Key is required."),
});

type BackendFormValues = z.infer<typeof backendSchema>;

interface BackendDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    backend: S3RepositoryBackendDTO | null;
    onSuccess: () => void;
}

function BackendDialog({ open, onOpenChange, backend, onSuccess }: BackendDialogProps) {
    const [loading, setLoading] = useState(false);
    const isEdit = !!backend;

    const { handleSubmit, control, reset } = useForm<BackendFormValues>({
        resolver: zodResolver(backendSchema),
        defaultValues: {
            name: "",
            uri: "",
            bucket: "",
            region: "",
            accessKey: "",
            secretKey: ""
        },
    });

    useEffect(() => {
        if (backend) {
            reset({
                name: backend.name,
                uri: backend.uri,
                bucket: backend.bucket,
                region: backend.region,
                accessKey: backend.accessKey,
                secretKey: backend.secretKey,
            });
        } else {
            reset({
                name: "",
                uri: "",
                bucket: "",
                region: "",
                accessKey: "",
                secretKey: ""
            });
        }
    }, [backend, reset, open]);

    const onSubmit = async (values: BackendFormValues) => {
        setLoading(true);
        try {
            if (isEdit && backend) {
                await s3BackendApi.apiV1S3backendPut({
                    s3RepositoryBackendDTO: {
                        ...values,
                        id: backend.id,
                    }
                });
                toast.success("S3 Backend updated successfully.");
            } else {
                await s3BackendApi.apiV1S3backendPost({
                    s3RepositoryBackendDTOWithoutId: values
                });
                toast.success("S3 Backend created successfully.");
            }
            onSuccess();
            onOpenChange(false);
        } catch (e) {
            showError(e);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-md">
                <DialogHeader>
                    <DialogTitle>{isEdit ? "Edit" : "Create"} S3 Backend</DialogTitle>
                    <DialogDescription>
                        {isEdit ? "Update the S3 storage backend configuration." : "Add a new S3 compatible storage backend."}
                    </DialogDescription>
                </DialogHeader>
                <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                    <FormField
                        control={control}
                        fieldName="name"
                        label="Name"
                        placeholder="e.g. AWS S3, MinIO"
                    />
                    <FormField
                        control={control}
                        fieldName="uri"
                        label="Host URI"
                        placeholder="https://s3.amazonaws.com"
                    />
                    <div className="grid grid-cols-2 gap-4">
                        <FormField
                            control={control}
                            fieldName="bucket"
                            label="Bucket"
                            placeholder="my-bucket"
                        />
                        <FormField
                            control={control}
                            fieldName="region"
                            label="Region"
                            placeholder="us-east-1"
                        />
                    </div>
                    <FormField
                        control={control}
                        fieldName="accessKey"
                        label="Access Key"
                        placeholder="AKIA..."
                    />
                    <FormField
                        control={control}
                        fieldName="secretKey"
                        label="Secret Key"
                        type="password"
                        placeholder="Secret Key"
                    />
                    <DialogFooter>
                        <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
                        <Button type="submit" disabled={loading}>{isEdit ? "Save Changes" : "Create"}</Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}

interface BackendDeleteDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onConfirm: () => void;
    backendName: string;
}

function BackendDeleteDialog({ open, onOpenChange, onConfirm, backendName }: BackendDeleteDialogProps) {
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Delete S3 Backend</DialogTitle>
                    <DialogDescription>
                        Are you sure you want to delete the backend <strong>{backendName}</strong>? This action cannot be undone.
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

export function S3BackendsTab() {
    const [backends, setBackends] = useState<S3RepositoryBackendDTO[]>([]);
    const [loading, setLoading] = useState(false);
    const [open, setOpen] = useState(false);
    const [selectedBackend, setSelectedBackend] = useState<S3RepositoryBackendDTO | null>(null);
    const [deleteOpen, setDeleteOpen] = useState(false);
    const [backendToDelete, setBackendToDelete] = useState<S3RepositoryBackendDTO | null>(null);

    const loadBackends = useCallback(async () => {
        setLoading(true);
        try {
            const result = await s3BackendApi.apiV1S3backendGet({ p: 0, s: 100 });
            setBackends(result.data ?? []);
        } catch (e) {
            showError(e);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadBackends();
    }, [loadBackends]);

    const handleDelete = useCallback(async () => {
        if (!backendToDelete) return;
        try {
            await s3BackendApi.apiV1S3backendIdDelete({ id: backendToDelete.id! });
            toast.success("S3 Backend deleted.");
            setDeleteOpen(false);
            loadBackends();
        } catch (e) {
            if (e && typeof e === "object" && "response" in e && e.response instanceof Response && e.response.status === 409) {
                toast.error("Cannot delete backend: It is still in use by one or more repositories.");
                return;
            }
            showError(e);
        }
    }, [backendToDelete, loadBackends]);

    const columns = useMemo<ColumnDef<S3RepositoryBackendDTO>[]>(() => [
        {
            header: "Name",
            cell: (backend) => (
                <div className="flex items-center gap-3">
                    <div className="size-8 rounded-full bg-primary/10 flex items-center justify-center text-primary">
                        <Database className="size-4" />
                    </div>
                    <span className="font-medium">{backend.name}</span>
                </div>
            ),
        },
        {
            header: "Host",
            accessorKey: "uri",
        },
        {
            header: "Bucket",
            accessorKey: "bucket",
        },
        {
            header: "",
            className: "w-0",
            cell: (backend) => (
                <div className="flex justify-end">
                    <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" className="h-8 w-8">
                                <MoreHorizontal className="size-4" />
                            </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className={"w-fit"}>
                            <DropdownMenuItem onClick={() => { setSelectedBackend(backend); setOpen(true); }}>
                                <Edit className="mr-2 size-4" /> Edit Backend
                          </DropdownMenuItem>
                            <DropdownMenuItem
                                onClick={() => { setBackendToDelete(backend); setDeleteOpen(true); }}
                                className="text-destructive focus:text-destructive focus:bg-destructive/10"
                            >
                                <Trash2 className="mr-2 size-4" /> Delete Backend
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
                        <Database className="size-5" /> S3 Backends
                    </h2>
                    <p className="text-sm text-muted-foreground mt-1">
                        Manage S3 compatible storage backends for your repositories.
                    </p>
                </div>
                <Button onClick={() => { setSelectedBackend(null); setOpen(true); }} className="gap-2">
                    <Plus className="size-4" /> Add Backend
                </Button>
            </div>

            <DataTable
                columns={columns}
                data={backends}
                loading={loading}
                emptyMessage="No S3 backends configured."
            />

            <BackendDialog
                open={open}
                onOpenChange={setOpen}
                backend={selectedBackend}
                onSuccess={loadBackends}
            />

            <BackendDeleteDialog
                open={deleteOpen}
                onOpenChange={setDeleteOpen}
                onConfirm={handleDelete}
                backendName={backendToDelete?.name ?? ""}
            />
        </section>
    );
}
