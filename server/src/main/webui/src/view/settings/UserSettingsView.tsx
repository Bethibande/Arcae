import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card.tsx";
import {useForm} from "react-hook-form";
import {zodResolver} from "@hookform/resolvers/zod";
import * as z from "zod";
import {FormField} from "@/components/form-field.tsx";
import {Input} from "@/components/ui/input.tsx";
import {Button} from "@/components/ui/button.tsx";
import {AuthenticationEndpointApi, UserEndpointApi} from "@/generated";
import {showError} from "@/lib/errors.ts";
import {toast} from "sonner";
import {useEffect, useState} from "react";
import {Lock, Save, User} from "lucide-react";
import {useAuth} from "@/lib/auth.tsx";

const profileSchema = z.object({
    name: z.string().min(1, "Name is required"),
    email: z.string().email("Invalid email address"),
    password: z.string().min(1, "Password is required to save changes"),
});

const passwordSchema = z.object({
    current: z.string().min(1, "Current password is required"),
    newPassword: z.string().min(8, "New password must be at least 8 characters long"),
    confirmPassword: z.string().min(1, "Please confirm your new password"),
}).refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords don't match",
    path: ["confirmPassword"],
});

type ProfileValues = z.infer<typeof profileSchema>;
type PasswordValues = z.infer<typeof passwordSchema>;

export default function UserSettingsView() {
    const [loading, setLoading] = useState(true);
    const api = new UserEndpointApi();
    const authApi = new AuthenticationEndpointApi()

    const {updateAuthState} = useAuth()

    const profileForm = useForm<ProfileValues>({
        resolver: zodResolver(profileSchema),
        defaultValues: {
            name: "",
            email: "",
            password: "",
        }
    });

    const passwordForm = useForm<PasswordValues>({
        resolver: zodResolver(passwordSchema),
        defaultValues: {
            current: "",
            newPassword: "",
            confirmPassword: "",
        }
    });

    useEffect(() => {
        authApi.apiV1AuthMeGet()
            .then(user => {
                profileForm.reset({
                    name: user.name,
                    email: user.email,
                });
                setLoading(false);
            })
            .catch(err => {
                showError(err);
                setLoading(false);
            });
    }, []);

    const onProfileSubmit = async (data: ProfileValues) => {
        try {
            await api.apiV1UserSelfPut({
                userDTOWithoutRoles: {
                    name: data.name,
                    email: data.email,
                    password: data.password,
                }
            });
            toast.success("Profile updated successfully");
            profileForm.setValue("password", "");
            updateAuthState();
        } catch (err) {
            showError(err);
        }
    };

    const onPasswordSubmit = async (data: PasswordValues) => {
        try {
            await api.apiV1UserSelfPasswordPut({
                passwordResetForm: {
                    current: data.current,
                    newPassword: data.newPassword,
                }
            });
            toast.success("Password changed successfully");
            passwordForm.reset();
        } catch (err) {
            showError(err);
        }
    };

    if (loading) {
        return <div className="p-8 text-center">Loading...</div>;
    }

    return (
        <div className="p-8 max-w-4xl mx-auto space-y-12 pb-24">
            <div className="space-y-6">
                <div className="flex items-center gap-3">
                    <User className="size-6 text-primary"/>
                    <h2 className="text-xl font-bold tracking-tight">Profile Information</h2>
                </div>
                <Card>
                    <CardHeader>
                        <CardTitle>Public Profile</CardTitle>
                        <CardDescription>Update your personal information.</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <form onSubmit={profileForm.handleSubmit(onProfileSubmit)} className="space-y-6">
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                                <FormField
                                    fieldName="name"
                                    label="Username"
                                    Input={props => <Input {...props} />}
                                    control={profileForm.control}
                                />
                                <FormField
                                    fieldName="email"
                                    label="Email Address"
                                    Input={props => <Input {...props} type="email" />}
                                    control={profileForm.control}
                                />
                                <FormField
                                    fieldName="password"
                                    label="Confirm with Password"
                                    Input={props => <Input {...props} type="password" />}
                                    control={profileForm.control}
                                />
                            </div>
                            <div className="flex justify-end">
                                <Button type="submit">
                                    <Save className="size-4 mr-2"/>
                                    Save Profile
                                </Button>
                            </div>
                        </form>
                    </CardContent>
                </Card>
            </div>

            <div className="space-y-6">
                <div className="flex items-center gap-3">
                    <Lock className="size-6 text-primary"/>
                    <h2 className="text-xl font-bold tracking-tight">Security</h2>
                </div>
                <Card>
                    <CardHeader>
                        <CardTitle>Change Password</CardTitle>
                        <CardDescription>Ensure your account is using a long, random password to stay secure.</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <form onSubmit={passwordForm.handleSubmit(onPasswordSubmit)} className="space-y-6">
                            <div className="grid grid-cols-1 gap-6 max-w-md">
                                <FormField
                                    fieldName="current"
                                    label="Current Password"
                                    Input={props => <Input {...props} type="password" />}
                                    control={passwordForm.control}
                                />
                                <FormField
                                    fieldName="newPassword"
                                    label="New Password"
                                    Input={props => <Input {...props} type="password" />}
                                    control={passwordForm.control}
                                />
                                <FormField
                                    fieldName="confirmPassword"
                                    label="Confirm New Password"
                                    Input={props => <Input {...props} type="password" />}
                                    control={passwordForm.control}
                                />
                            </div>
                            <div className="flex justify-end">
                                <Button type="submit">
                                    <Lock className="size-4 mr-2"/>
                                    Update Password
                                </Button>
                            </div>
                        </form>
                    </CardContent>
                </Card>
            </div>
        </div>
    )
}
