import {AuthenticationEndpointApi, ResponseError, type UserDTOWithoutPassword} from "@/generated";
import {showError, showErrorMessage} from "@/lib/errors.ts";
import {createContext, type ReactNode, useContext, useEffect, useState} from "react";
import i18next from "i18next";

export interface LoginResult {
    user?: UserDTOWithoutPassword;
    error?: Response;
}

async function login(username: string, password: string): Promise<LoginResult> {
    try {
        const response = await new AuthenticationEndpointApi().apiV1AuthLoginPost({
            credentials: {
                username: username,
                password: password
            }
        });

        const user = response as UserDTOWithoutPassword;
        console.log("Logged in as " + user.name)
        return {user};
    } catch (e) {
        if (e instanceof ResponseError) {
            console.error('Login failed:', e.response.status);
            return {user: undefined, error: e.response};
        }
        console.log("Unexpected error: ", e);
        return {user: undefined, error: undefined};
    }
}

async function logout() {
    await new AuthenticationEndpointApi().apiV1AuthLogoutPost()
}

export interface AuthStatus {
    user?: UserDTOWithoutPassword;
    pending: boolean;
    updateAuthState: () => void;
    login: (username: string, password: string) => Promise<LoginResult>;
    logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthStatus | undefined>(undefined);

export const AuthProvider = ({children}: { children: ReactNode }) => {
    const [auth, setAuth] = useState<UserDTOWithoutPassword | undefined>(undefined);
    const [pending, setPending] = useState<boolean>(true);

    function updateAuthState() {
        fetch("/api/v1/auth/me", {
            headers: {
                "Accept": "application/json",
            }
        }).then(response => {
            if (response.status === 404) {
                setAuth(undefined);
                setPending(false);
            } else if (response.ok) {
                response.json().then(user => {
                    setAuth(user)
                    setPending(false)
                })
            } else {
                showErrorMessage(i18next.t("login.error.load"))
            }
        }).catch(showError)
    }

    useEffect(updateAuthState, [])

    async function login0(username: string, password: string) {
        return login(username, password).then(result => {
            setAuth(result.user)
            return result
        })
    }

    async function logout0() {
        await logout().then(() => setAuth(undefined))
    }

    return (
        <AuthContext.Provider value={{user: auth, pending: pending, updateAuthState, login: login0, logout: logout0}}>
            {!pending && children}
        </AuthContext.Provider>
    )
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within a AuthProvider');
    }
    return context;
}
