import { useState } from "react";
import { Lock, Eye, EyeOff } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card.tsx";
import { FieldDescription } from "@/components/ui/field.tsx";
import { Button } from "@/components/ui/button.tsx";
import { toast } from "sonner";
import { userApi } from "@/lib/api.ts";
import { PasswordConfirmDialog } from "./password-confirm-dialog.tsx";
import { FormField } from "@/components/form-field.tsx";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";

const passwordSchema = z.object({
    newPassword: z.string().min(1, "Please enter a new password."),
    confirmPassword: z.string().min(1, "Please confirm your new password."),
}).refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords do not match.",
    path: ["confirmPassword"],
});

type PasswordFormValues = z.infer<typeof passwordSchema>;

export function PasswordTab() {
    const [showNew, setShowNew] = useState(false);
    const [showConfirm, setShowConfirm] = useState(false);
    const [loading, setLoading] = useState(false);
    const [confirmOpen, setConfirmOpen] = useState(false);

    const {
        handleSubmit,
        control,
        reset,
        getValues,
    } = useForm<PasswordFormValues>({
        resolver: zodResolver(passwordSchema),
        defaultValues: {
            newPassword: "",
            confirmPassword: "",
        },
    });

    const onSubmit = () => {
        setConfirmOpen(true);
    };

    const handleConfirm = async (currentPassword: string) => {
        setLoading(true);
        try {
            const values = getValues();
            await userApi.apiV1UserSelfPasswordPut({
                passwordResetForm: {
                    current: currentPassword,
                    newPassword: values.newPassword,
                }
            });
            setConfirmOpen(false);
            reset();
            toast.success("Password changed successfully.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <section className="space-y-6">
            <div>
                <h2 className="text-xl font-semibold flex items-center gap-2">
                    <Lock className="size-5" /> Change Password
                </h2>
                <p className="text-sm text-muted-foreground mt-1">
                    Update your password to keep your account secure.
                </p>
            </div>
            <Card>
                <CardContent className="p-6">
                    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
                        <FormField
                            control={control}
                            fieldName="newPassword"
                            label="New Password"
                            placeholder="New password"
                            Input={({ ...props }) => (
                                <div className="relative">
                                    <input
                                        {...props}
                                        className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 md:text-sm pr-10"
                                        type={showNew ? "text" : "password"}
                                    />
                                    <button
                                        type="button"
                                        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                                        onClick={() => setShowNew((s) => !s)}
                                    >
                                        {showNew ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                                    </button>
                                </div>
                            )}
                        />
                        <FormField
                            control={control}
                            fieldName="confirmPassword"
                            label="Confirm New Password"
                            placeholder="Confirm new password"
                            Input={({ ...props }) => (
                                <div className="relative">
                                    <input
                                        {...props}
                                        className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-sm transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 md:text-sm pr-10"
                                        type={showConfirm ? "text" : "password"}
                                    />
                                    <button
                                        type="button"
                                        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                                        onClick={() => setShowConfirm((s) => !s)}
                                    >
                                        {showConfirm ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                                    </button>
                                </div>
                            )}
                        />
                        <FieldDescription>
                            You will be asked to confirm your current password before the change is applied.
                        </FieldDescription>
                        <div className="flex justify-end">
                            <Button type="submit" disabled={loading}>
                                Change Password
                            </Button>
                        </div>
                    </form>
                </CardContent>
            </Card>

            <PasswordConfirmDialog
                open={confirmOpen}
                onOpenChange={setConfirmOpen}
                onConfirm={handleConfirm}
                loading={loading}
            />
        </section>
    );
}
