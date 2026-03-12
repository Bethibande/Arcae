import { useState, useEffect, useMemo } from "react";
import { KeyRound, Plus, Trash2, Copy, MoreHorizontal, CalendarIcon } from "lucide-react";
import { Button } from "@/components/ui/button.tsx";
import { Input } from "@/components/ui/input.tsx";
import { Field, FieldLabel, FieldError } from "@/components/ui/field.tsx";
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
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select.tsx";
import {
    Popover,
    PopoverContent,
    PopoverTrigger,
} from "@/components/ui/popover.tsx";
import { Calendar } from "@/components/ui/calendar.tsx";
import { toast } from "sonner";
import { accessTokenApi } from "@/lib/api.ts";
import { showError } from "@/lib/errors.ts";
import { useAuth } from "@/components/auth-provider.tsx";
import type { AccessTokenDTOWithoutToken, AccessTokenDTO } from "@/generated";
import { DataTable, type ColumnDef } from "@/components/ui/data-table.tsx";
import { useForm, Controller } from "react-hook-form";
import { FormField } from "@/components/form-field.tsx";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { format, addDays, isBefore, endOfDay } from "date-fns";
import { cn } from "@/lib/utils.ts";

const tokenSchema = z.object({
    name: z.string().min(1, "Please enter a token name."),
    expirationType: z.enum(["never", "7d", "30d", "60d", "90d", "custom"]),
    customExpiration: z.date().optional(),
}).refine((data) => {
    if (data.expirationType === "custom" && !data.customExpiration) {
        return false;
    }
    return true;
}, {
    message: "Please select an expiration date.",
    path: ["customExpiration"],
});

type TokenFormValues = z.infer<typeof tokenSchema>;

interface NewTokenDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onCreated: (token: AccessTokenDTO) => void;
}

function NewTokenDialog({ open, onOpenChange, onCreated }: NewTokenDialogProps) {
    const [loading, setLoading] = useState(false);

    const {
        handleSubmit,
        control,
        watch,
        reset,
        formState,
    } = useForm<TokenFormValues>({
        resolver: zodResolver(tokenSchema),
        defaultValues: {
            name: "",
            expirationType: "30d",
        },
    });

    const expirationType = watch("expirationType");

    const handleCreate = async (values: TokenFormValues) => {
        setLoading(true);
        try {
            let expiresAfter: Date | undefined;
            if (values.expirationType !== "never") {
                if (values.expirationType === "custom" && values.customExpiration) {
                    expiresAfter = values.customExpiration;
                } else {
                    const days = parseInt(values.expirationType);
                    expiresAfter = addDays(new Date(), days);
                }
            }

            const token = await accessTokenApi.apiV1TokensPost({
                accessTokenDTOWithoutId: {
                    name: values.name.trim(),
                    expiresAfter,
                }
            });
            onCreated(token);
            reset();
            onOpenChange(false);
        } catch (e) {
            showError(e);
        } finally {
            setLoading(false);
        }
    };

    const handleOpenChange = (v: boolean) => {
        if (!v) reset();
        onOpenChange(v);
    };

    return (
        <Dialog open={open} onOpenChange={handleOpenChange}>
            <DialogContent className="sm:max-w-md">
                <DialogHeader>
                    <DialogTitle>Create Access Token</DialogTitle>
                    <DialogDescription>
                        Give your token a descriptive name and set an expiration date.
                    </DialogDescription>
                </DialogHeader>
                <form onSubmit={handleSubmit(handleCreate)} className="space-y-4 py-2">
                    <FormField
                        control={control}
                        fieldName="name"
                        label="Token Name"
                        placeholder="e.g. CI/CD Pipeline"
                    />

                    <Field>
                        <FieldLabel>Expiration</FieldLabel>
                        <Controller
                            name="expirationType"
                            control={control}
                            render={({ field }) => (
                                <Select onValueChange={field.onChange} value={field.value}>
                                    <SelectTrigger>
                                        <SelectValue placeholder="Select expiration" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="7d">7 days</SelectItem>
                                        <SelectItem value="30d">30 days</SelectItem>
                                        <SelectItem value="60d">60 days</SelectItem>
                                        <SelectItem value="90d">90 days</SelectItem>
                                        <SelectItem value="custom">Custom date...</SelectItem>
                                        <SelectItem value="never">Never</SelectItem>
                                    </SelectContent>
                                </Select>
                            )}
                        />
                    </Field>

                    {expirationType === "custom" && (
                        <Field>
                            <FieldLabel>Custom Expiration Date</FieldLabel>
                            <Controller
                                name="customExpiration"
                                control={control}
                                render={({ field }) => (
                                    <Popover>
                                        <PopoverTrigger asChild>
                                            <Button
                                                variant="outline"
                                                className={cn(
                                                    "w-full justify-start text-left font-normal",
                                                    !field.value && "text-muted-foreground"
                                                )}
                                            >
                                                <CalendarIcon className="mr-2 h-4 w-4" />
                                                {field.value ? format(field.value, "PPP") : <span>Pick a date</span>}
                                            </Button>
                                        </PopoverTrigger>
                                        <PopoverContent className="w-auto p-0" align="start">
                                            <Calendar
                                                mode="single"
                                                captionLayout={"dropdown-years"}
                                                selected={field.value}
                                                onSelect={field.onChange}
                                                disabled={(date) => isBefore(date, endOfDay(new Date()))}
                                            />
                                        </PopoverContent>
                                    </Popover>
                                )}
                            />
                            <FieldError errors={[formState.errors.customExpiration]} />
                        </Field>
                    )}

                    <DialogFooter className="pt-4">
                        <Button type="button" variant="outline" onClick={() => handleOpenChange(false)} disabled={loading}>
                            Cancel
                        </Button>
                        <Button type="submit" disabled={loading}>
                            {loading ? "Creating..." : "Create"}
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}

interface TokenRevealDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    token: AccessTokenDTO | null;
}

function TokenRevealDialog({ open, onOpenChange, token }: TokenRevealDialogProps) {
    const [copied, setCopied] = useState(false);

    const handleCopy = () => {
        if (!token) return;
        navigator.clipboard.writeText(token.token);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Access Token Created</DialogTitle>
                    <DialogDescription>
                        Copy your token now. You won't be able to see it again.
                    </DialogDescription>
                </DialogHeader>
                <div className="flex items-center gap-2">
                    <Input readOnly value={token?.token ?? ""} className="font-mono text-xs" />
                    <Button variant="outline" size="icon" onClick={handleCopy}>
                        <Copy className="size-4" />
                    </Button>
                </div>
                {copied && <p className="text-xs text-green-600">Copied to clipboard!</p>}
                <DialogFooter>
                    <Button onClick={() => onOpenChange(false)}>Done</Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

interface TokenDeleteDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onConfirm: () => void;
    tokenName: string;
}

function TokenDeleteDialog({ open, onOpenChange, onConfirm, tokenName }: TokenDeleteDialogProps) {
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Delete Access Token</DialogTitle>
                    <DialogDescription>
                        Are you sure you want to delete the token <strong>{tokenName}</strong>? This action cannot be undone.
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

export function TokensTab() {
    const { user } = useAuth();
    const [tokens, setTokens] = useState<AccessTokenDTOWithoutToken[]>([]);
    const [loading, setLoading] = useState(false);
    const [newTokenOpen, setNewTokenOpen] = useState(false);
    const [revealToken, setRevealToken] = useState<AccessTokenDTO | null>(null);
    const [revealOpen, setRevealOpen] = useState(false);
    const [deleteToken, setDeleteToken] = useState<AccessTokenDTOWithoutToken | null>(null);
    const [deleteOpen, setDeleteOpen] = useState(false);

    useEffect(() => {
        loadTokens();
    }, []);

    const loadTokens = async () => {
        setLoading(true);
        try {
            const result = await accessTokenApi.apiV1TokensGet();
            setTokens(result);
        } catch (e) {
            showError(e);
        } finally {
            setLoading(false);
        }
    };

    const handleDelete = async () => {
        if (!deleteToken) return;
        try {
            await accessTokenApi.apiV1TokensIdDelete({ id: deleteToken.id! });
            setTokens((prev) => prev.filter((t) => t.id !== deleteToken.id));
            setDeleteOpen(false);
            toast.success("Token deleted.");
        } catch (e) {
            showError(e);
        }
    };

    const handleCreated = (token: AccessTokenDTO) => {
        setTokens((prev) => [...prev, {
            id: token.id,
            name: token.name,
            expiresAfter: token.expiresAfter
        }]);
        setRevealToken(token);
        setRevealOpen(true);
    };

    const columns = useMemo<ColumnDef<AccessTokenDTOWithoutToken>[]>(() => [
        {
            header: "Name",
            accessorKey: "name",
            cell: (token) => (
                <div className="flex items-center gap-3">
                    <div className="size-8 rounded-full bg-primary/10 flex items-center justify-center text-primary">
                        <KeyRound className="size-4" />
                    </div>
                    <span className="font-medium">{token.name}</span>
                </div>
            ),
        },
        {
            header: "Expires",
            cell: (token) => (
                <span className="text-muted-foreground font-medium">
                    {token.expiresAfter ? format(token.expiresAfter, "PPP") : "Never"}
                </span>
            ),
        },
        {
            header: "Status",
            cell: (token) => {
                const isExpired = token.expiresAfter && isBefore(token.expiresAfter, new Date());
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
            cell: (token) => (
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
                                    setDeleteToken(token);
                                    setDeleteOpen(true);
                                }}
                                className="text-destructive focus:text-destructive focus:bg-destructive/10 cursor-pointer"
                            >
                                <Trash2 className="mr-2 size-4" />
                                Delete Token
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
                        <KeyRound className="size-5" /> Access Tokens
                    </h2>
                    <p className="text-sm text-muted-foreground mt-1">
                        Manage your personal access tokens for API access.
                    </p>
                </div>
                <Button
                    onClick={() => setNewTokenOpen(true)}
                    className="gap-2"
                >
                    <Plus className="size-4" />
                    Generate Token
                </Button>
            </div>

            <DataTable
                columns={columns}
                data={tokens}
                loading={loading}
                emptyMessage="No access tokens yet. Create one to authenticate with the API."
            />

            {user && (
                <NewTokenDialog
                    open={newTokenOpen}
                    onOpenChange={setNewTokenOpen}
                    onCreated={handleCreated}
                />
            )}
            <TokenRevealDialog
                open={revealOpen}
                onOpenChange={setRevealOpen}
                token={revealToken}
            />

            <TokenDeleteDialog
                open={deleteOpen}
                onOpenChange={setDeleteOpen}
                onConfirm={handleDelete}
                tokenName={deleteToken?.name ?? ""}
            />
        </section>
    );
}
