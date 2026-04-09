import {useEffect, useState} from "react";
import {Link2, Unlink, User} from "lucide-react";
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card.tsx";
import {FieldDescription} from "@/components/ui/field.tsx";
import {Separator} from "@/components/ui/separator.tsx";
import {Button} from "@/components/ui/button.tsx";
import {toast} from "sonner";
import {useAuth} from "@/components/auth-provider.tsx";
import {authApi, oidcApi, userApi} from "@/lib/api.ts";
import {PasswordConfirmDialog} from "./password-confirm-dialog.tsx";
import {FormField} from "@/components/form-field.tsx";
import {useForm} from "react-hook-form";
import {zodResolver} from "@hookform/resolvers/zod";
import * as z from "zod";
import {type OpenIDConnectionDTO} from "@/generated";
import {showError} from "@/lib/errors.ts";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle
} from "@/components/ui/alert-dialog.tsx";

const profileSchema = z.object({
    name: z.string().min(1, "Username is required."),
    email: z.string().email("Invalid email address.").min(1, "Email is required."),
});

type ProfileFormValues = z.infer<typeof profileSchema>;

export function ProfileTab() {
    const {user, refresh} = useAuth();
    const [loading, setLoading] = useState(false);
    const [confirmOpen, setConfirmOpen] = useState(false);
    const [oidcItems, setOidcItems] = useState<string[]>([]);
    const [connections, setConnections] = useState<OpenIDConnectionDTO[]>([]);
    const [unlinkId, setUnlinkId] = useState<number | null>(null);

    const fetchOidcData = async () => {
        try {
            const [items, links] = await Promise.all([
                authApi.apiV1AuthOptionsGet().then(result => result.openIdConnectProviders),
                oidcApi.apiV1OidcLinksGet(),
            ]);
            setOidcItems(items);
            setConnections(links);
        } catch (e) {
            showError(e);
        }
    };

    useEffect(() => {
        fetchOidcData();
    }, []);

    const handleLink = async (provider: string) => {
        try {
            const url = await oidcApi.apiV1OidcLinkProviderGet({provider});
            window.location.href = url;
        } catch (e) {
            showError(e);
        }
    };

    const handleUnlink = async () => {
        if (unlinkId === null) return;
        try {
            await oidcApi.apiV1OidcLinkIdDelete({id: unlinkId});
            toast.success("Account unlinked successfully.");
            fetchOidcData();
        } catch (e) {
            showError(e);
        } finally {
            setUnlinkId(null);
        }
    };

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
                userDTOWithoutIdAndRoles: {
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
                    <User className="size-5"/> Profile
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
                        <Separator/>
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

            {oidcItems.length > 0 && (
                <Card>
                    <CardHeader>
                        <CardTitle className="text-lg flex items-center gap-2">
                            <Link2 className="size-4"/> Connected Accounts
                        </CardTitle>
                        <CardDescription>
                            Link your account with external providers for an improved login experience.
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-4">
                        {oidcItems.map((item) => {
                            const connection = connections.find(c => c.provider?.name === item);
                            return (
                                <div key={item} className="flex items-center justify-between py-2">
                                    <div className="flex flex-col">
                                        <span className="font-medium capitalize">{item}</span>
                                        <span className="text-xs text-muted-foreground">
                                            {connection ? "Connected" : "Not connected"}
                                        </span>
                                    </div>
                                    {connection ? (
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={() => setUnlinkId(connection.id!)}
                                            className="text-destructive hover:text-destructive"
                                        >
                                            <Unlink className="size-4 mr-2"/> Unlink
                                        </Button>
                                    ) : (
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={() => handleLink(item)}
                                        >
                                            <Link2 className="size-4 mr-2"/> Link
                                        </Button>
                                    )}
                                </div>
                            );
                        })}
                    </CardContent>
                </Card>
            )}

            <PasswordConfirmDialog
                open={confirmOpen}
                onOpenChange={setConfirmOpen}
                onConfirm={handleConfirm}
                loading={loading}
            />

            <AlertDialog open={unlinkId !== null} onOpenChange={(open) => !open && setUnlinkId(null)}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Are you sure?</AlertDialogTitle>
                        <AlertDialogDescription>
                            This will unlink your account from this provider. You will no longer be able to log in using
                            this provider unless you link it again.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel>Cancel</AlertDialogCancel>
                        <AlertDialogAction onClick={handleUnlink}
                                           className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
                            Unlink
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </section>
    );
}
