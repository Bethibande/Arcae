import {useEffect, useState} from "react";
import {User} from "lucide-react";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {FieldDescription} from "@/components/ui/field.tsx";
import {Separator} from "@/components/ui/separator.tsx";
import {Button} from "@/components/ui/button.tsx";
import {toast} from "sonner";
import {useAuth} from "@/components/auth-provider.tsx";
import {userApi} from "@/lib/api.ts";
import {PasswordConfirmDialog} from "./password-confirm-dialog.tsx";
import {FormField} from "@/components/form-field.tsx";
import {useForm} from "react-hook-form";
import {zodResolver} from "@hookform/resolvers/zod";
import * as z from "zod";

const profileSchema = z.object({
    name: z.string().min(1, "Username is required."),
    email: z.string().email("Invalid email address.").min(1, "Email is required."),
});

type ProfileFormValues = z.infer<typeof profileSchema>;

export function ProfileTab() {
    const { user, refresh } = useAuth();
    const [loading, setLoading] = useState(false);
    const [confirmOpen, setConfirmOpen] = useState(false);

    const {
        handleSubmit,
        control,
        reset,
        getValues,
    } = useForm<ProfileFormValues>({
        resolver: zodResolver(profileSchema),
        defaultValues: {
            name: user?.name ?? "",
            email: user?.email ?? "",
        },
    });

    useEffect(() => {
        if (user) {
            reset({
                name: user.name ?? "",
                email: user.email ?? "",
            });
        }
    }, [user, reset]);

    const onSubmit = () => {
        setConfirmOpen(true);
    };

    const handleConfirm = async (currentPassword: string) => {
        setLoading(true);
        try {
            const values = getValues();
            await userApi.apiV1UserSelfPut({
                userDTOWithoutRoles: {
                    name: values.name,
                    email: values.email,
                    password: currentPassword,
                }
            });
            await refresh();
            setConfirmOpen(false);
            toast.success("Profile updated successfully.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <section className="space-y-6">
            <div>
                <h2 className="text-xl font-semibold flex items-center gap-2">
                    <User className="size-5" /> Profile
                </h2>
                <p className="text-sm text-muted-foreground mt-1">
                    Manage your public profile and account settings.
                </p>
            </div>
            <Card>
                <CardContent className="p-6">
                    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
                        <FormField
                            control={control}
                            fieldName="name"
                            label="Username"
                            placeholder="Your username"
                        />
                        <Separator />
                        <FormField
                            control={control}
                            fieldName="email"
                            label="Email"
                            type="email"
                            placeholder="your@email.com"
                        />
                        <FieldDescription>
                            Changing your username or email requires your current password.
                        </FieldDescription>
                        <div className="flex justify-end">
                            <Button type="submit" disabled={loading}>
                                Save Changes
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
