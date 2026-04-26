import React, {createContext, useCallback, useContext, useEffect, useRef, useState} from "react"
import {
    type ApiV1OidcLoginCompleteProviderPostRequest,
    type Credentials,
    LoginResult,
    ResponseError,
    type UserDTOWithoutPassword
} from "@/generated"
import {refresh as refreshSession} from "@/lib/middleware"
import {authApi as api, oidcApi} from "@/lib/api"
import {useNavigate} from "react-router";

interface AuthContextType {
    user: UserDTOWithoutPassword | null
    loading: boolean
    login: (credentials: Credentials) => Promise<void>
    login2fa: (token: string) => Promise<void>
    loginOidc: (credentials: ApiV1OidcLoginCompleteProviderPostRequest) => Promise<void>
    logout: () => Promise<void>
    refresh: () => Promise<void>
}

const sessionExpirationKey = "session-expiration"

function storeSessionExpiration(date: Date | string | null) {
    if (date) {
        const d = typeof date === "string" ? date : date.toISOString()
        localStorage.setItem(sessionExpirationKey, d)
    } else {
        localStorage.removeItem(sessionExpirationKey)
    }
}

function getSessionExpiration(): Date | null {
    const expirationString = localStorage.getItem(sessionExpirationKey)
    if (expirationString) {
        const date = new Date(expirationString)
        if (isNaN(date.getTime())) return null
        return date
    }
    return null
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({children}: { children: React.ReactNode }) {
    const [user, setUser] = useState<UserDTOWithoutPassword | null>(null)
    const [loading, setLoading] = useState(true)
    const navigate = useNavigate()
    const refreshTimer = useRef<number | null>(null)

    const clearRefreshTimer = () => {
        if (refreshTimer.current) {
            clearTimeout(refreshTimer.current)
            refreshTimer.current = null
        }
    }

    const scheduleRefresh = useCallback((expiry: Date | null) => {
        clearRefreshTimer()
        if (!expiry) return

        const now = new Date().getTime()
        const expiryTime = expiry.getTime()
        // Refresh 1 minute before expiration, or immediately if less than 1 minute left
        const delay = Math.max(0, expiryTime - now - 60000)

        refreshTimer.current = setTimeout(() => {
            console.log("Proactively refreshing session...")
            refresh()
        }, delay)
    }, [])

    const refresh = useCallback(async () => {
        // If we have an expiration date, check if we need to refresh session first
        const expiration = getSessionExpiration()
        if (expiration && expiration.getTime() - new Date().getTime() < 60000) {
            console.log("Session expiring soon, refreshing...")
            const result = await refreshSession()
            if (result && result.result === LoginResult.LoggedIn && result.sessionExpiry) {
                storeSessionExpiration(result.sessionExpiry)
            }
        }

        try {
            const userData = await api.apiV1AuthMeGet()
            setUser(userData)
            scheduleRefresh(getSessionExpiration())
        } catch (error) {
            if (error instanceof ResponseError && (error.response.status === 401 || error.response.status === 404)) {
                // Try to refresh once to possibly extend the session
                const result = await refreshSession()
                if (result && result.result === LoginResult.LoggedIn) {
                    try {
                        const userData = await api.apiV1AuthMeGet()
                        setUser(userData)
                        if (result.sessionExpiry) {
                            storeSessionExpiration(result.sessionExpiry)
                        }
                        scheduleRefresh(getSessionExpiration())
                        return
                    } catch (retryError) {
                        // Ignore if it still fails
                    }
                }
            }
            setUser(null)
            storeSessionExpiration(null)
            clearRefreshTimer()
        } finally {
            setLoading(false)
        }
    }, [scheduleRefresh])

    const login = async (credentials: Credentials) => {
        try {
            const result = await api.apiV1AuthLoginPost({credentials})
            if (result.result === LoginResult.Requires2Fa) {
                navigate("/login/2fa/mail")
                return
            }

            if (result.sessionExpiry) {
                storeSessionExpiration(result.sessionExpiry)
            }

            await refresh()
        } catch (error) {
            throw error
        }
    }

    const login2fa = async (token: string) => {
        try {
            const result = await api.apiV1Auth2faMailPost({twoFAToken: {token}})
            if (result.sessionExpiry) {
                storeSessionExpiration(result.sessionExpiry)
            }
            await refresh()
        } catch (error) {
            throw error
        }
    }

    const loginOidc = async (credentials: ApiV1OidcLoginCompleteProviderPostRequest) => {
        try {
            const result = await oidcApi.apiV1OidcLoginCompleteProviderPost(credentials)
            if (result.sessionExpiry) {
                storeSessionExpiration(result.sessionExpiry)
            }
            await refresh()
        } catch (error) {
            throw error
        }
    }

    const logout = async () => {
        try {
            await api.apiV1AuthLogoutPost()
            storeSessionExpiration(null)
            clearRefreshTimer()
        } finally {
            setUser(null)
        }
    }

    useEffect(() => {
        refresh()
        return () => clearRefreshTimer()
    }, [refresh])

    return (
        <AuthContext.Provider value={{user, loading, login, login2fa, loginOidc, logout, refresh}}>
            {!loading && (children)}
        </AuthContext.Provider>
    )
}

export const useAuth = () => {
    const context = useContext(AuthContext)
    if (context === undefined) {
        throw new Error("useAuth must be used within an AuthProvider")
    }
    return context
}
