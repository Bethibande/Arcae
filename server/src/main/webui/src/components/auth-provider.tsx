import React, {createContext, useCallback, useContext, useEffect, useState} from "react"
import {type Credentials, ResponseError, type UserDTOWithoutPassword} from "@/generated"
import {refresh as refreshSession} from "@/lib/middleware"
import {authApi as api} from "@/lib/api"

interface AuthContextType {
  user: UserDTOWithoutPassword | null
  loading: boolean
  login: (credentials: Credentials) => Promise<void>
  logout: () => Promise<void>
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserDTOWithoutPassword | null>(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    try {
      const userData = await api.apiV1AuthMeGet()
      setUser(userData)
    } catch (error) {
      if (error instanceof ResponseError && error.response.status === 404) {
        // Try to refresh once to possibly extend the session
        const success = await refreshSession()
        if (success) {
          try {
            const userData = await api.apiV1AuthMeGet()
            setUser(userData)
            return
          } catch (retryError) {
            // Ignore if it still fails
          }
        }
      }
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  const login = async (credentials: Credentials) => {
    try {
      await api.apiV1AuthLoginPost({ credentials })
      await refresh()
    } catch (error) {
      throw error
    }
  }

  const logout = async () => {
    try {
      await api.apiV1AuthLogoutPost()
    } finally {
      setUser(null)
    }
  }

  useEffect(() => {
    refresh()
  }, [refresh])

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, refresh }}>
      {children}
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
