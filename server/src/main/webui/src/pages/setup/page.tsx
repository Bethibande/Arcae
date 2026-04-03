import {useEffect, useState} from "react";
import {useNavigate} from "react-router";
import {setupApi} from "@/lib/api.ts";
import {Button} from "@/components/ui/button";
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card";
import {Field, FieldGroup, FieldLabel} from "@/components/ui/field";
import {Input} from "@/components/ui/input";
import {showError} from "@/lib/errors.ts";
import {toast} from "sonner";
import {useAuth} from "@/components/auth-provider.tsx";

export default function SetupPage() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    
    const [username, setUsername] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");

    const {login} = useAuth()

    useEffect(() => {
        setupApi.apiV1SetupCompleteGet()
            .then((complete) => {
                if (complete) {
                    navigate("/", { replace: true });
                } else {
                    setLoading(false);
                }
            })
            .catch((e) => {
                showError(e);
                setLoading(false);
            });
    }, [navigate]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        
        if (password !== confirmPassword) {
            toast.error("Passwords do not match");
            return;
        }

        setSubmitting(true);
        try {
            await setupApi.apiV1SetupUserPost({
                userDTOWithoutIdAndRoles: {
                    name: username,
                    email: email,
                    password: password,
                }
            });

            login({ username, password }).catch(showError);

            toast.success("Admin user created successfully");
            navigate("/", { replace: true });
        } catch (e) {
            showError(e);
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) {
        return (
            <div className="flex min-h-svh items-center justify-center">
                <p className="text-muted-foreground animate-pulse">Loading...</p>
            </div>
        );
    }

    return (
        <div className="flex min-h-svh items-center justify-center p-6 md:p-10">
            <div className="w-full max-w-md">
                <Card>
                    <CardHeader className="text-center">
                        <CardTitle className="text-2xl">Initial Setup</CardTitle>
                        <CardDescription>
                            Create the default admin user to get started.
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        <form onSubmit={handleSubmit}>
                            <FieldGroup>
                                <Field>
                                    <FieldLabel htmlFor="username">Username</FieldLabel>
                                    <Input
                                        id="username"
                                        type="text"
                                        placeholder="admin"
                                        value={username}
                                        onChange={(e) => setUsername(e.target.value)}
                                        required
                                    />
                                </Field>
                                <Field>
                                    <FieldLabel htmlFor="email">Email</FieldLabel>
                                    <Input
                                        id="email"
                                        type="email"
                                        placeholder="admin@example.com"
                                        value={email}
                                        onChange={(e) => setEmail(e.target.value)}
                                        required
                                    />
                                </Field>
                                <Field>
                                    <FieldLabel htmlFor="password">Password</FieldLabel>
                                    <Input
                                        id="password"
                                        type="password"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        required
                                    />
                                </Field>
                                <Field>
                                    <FieldLabel htmlFor="confirmPassword">Confirm Password</FieldLabel>
                                    <Input
                                        id="confirmPassword"
                                        type="password"
                                        value={confirmPassword}
                                        onChange={(e) => setConfirmPassword(e.target.value)}
                                        required
                                    />
                                </Field>
                                <Field className="pt-2">
                                    <Button type="submit" className="w-full" disabled={submitting}>
                                        {submitting ? "Creating Admin..." : "Finish Setup"}
                                    </Button>
                                </Field>
                            </FieldGroup>
                        </form>
                    </CardContent>
                </Card>
            </div>
        </div>
    );
}
