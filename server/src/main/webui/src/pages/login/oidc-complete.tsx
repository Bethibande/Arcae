import {useEffect, useState} from "react";
import {useNavigate, useParams, useSearchParams} from "react-router";
import {useAuth} from "@/components/auth-provider";
import {showError, showErrorMessage} from "@/lib/errors";
import {oidcApi} from "@/lib/api.ts";
import {toast} from "sonner";

export default function OidcCompletePage() {
    const { provider } = useParams<{ provider: string }>();
    const [searchParams] = useSearchParams();
    const { loginOidc, user, loading } = useAuth();
    const navigate = useNavigate();
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (loading) return;

        const code = searchParams.get("code");
        const stateFromQuery = searchParams.get("state");
        const isLinking = !!user;

        if (!provider || !code) {
            setError("Missing provider or authorization code.");
            setTimeout(() => navigate(isLinking ? "/settings/profile" : "/login"), 3000);
            return;
        }

        if (isLinking) {
            oidcApi.apiV1OidcLinkCompleteProviderPost({
                provider,
                code,
                state: stateFromQuery ?? undefined,
            })
                .then(() => {
                    toast.success(`Successfully linked ${provider} account.`);
                    navigate("/settings/profile", { replace: true });
                })
                .catch((e) => {
                    if (e.response && e.response.status === 409) {
                        showErrorMessage("This OIDC account is already linked to another user.");
                        navigate("/settings/profile");
                        return;
                    }

                    showError(e);
                    setError("Linking failed. Redirecting to profile...");
                    setTimeout(() => navigate("/settings/profile"), 3000);
                });
        } else {
            loginOidc({
                provider,
                code,
                state: stateFromQuery ?? undefined,
            })
                .then(() => {
                    navigate("/", { replace: true });
                })
                .catch((e) => {
                    if (e.response && e.response.status === 404) {
                        showErrorMessage("Account not linked.");
                        navigate("/login");
                        return;
                    }

                    showError(e);
                    setError("Authentication failed. Redirecting to login...");
                    setTimeout(() => navigate("/login"), 3000);
                });
        }
    }, [provider, searchParams, loading]);

    const isLinking = !!user;

    return (
        <div className="flex min-h-svh flex-col items-center justify-center p-6 md:p-10">
            <div className="w-full max-w-sm text-center space-y-4">
                <h2 className="text-2xl font-bold">{isLinking ? "Linking Account" : "Completing Login"}</h2>
                {loading ? (
                    <div className="flex flex-col items-center gap-4">
                        <div className="size-10 rounded-full border-4 border-primary border-t-transparent animate-spin" />
                        <p className="text-muted-foreground animate-pulse">Initializing...</p>
                    </div>
                ) : error ? (
                    <p className="text-destructive font-medium">{error}</p>
                ) : (
                    <div className="flex flex-col items-center gap-4">
                        <div className="size-10 rounded-full border-4 border-primary border-t-transparent animate-spin" />
                        <p className="text-muted-foreground animate-pulse">
                            {isLinking
                                ? `Please wait while we link your ${provider} account...`
                                : `Please wait while we finalize your authentication with ${provider}...`}
                        </p>
                    </div>
                )}
            </div>
        </div>
    );
}
