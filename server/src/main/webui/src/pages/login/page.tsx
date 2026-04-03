import {LoginForm} from "@/components/login-form"
import {useAuth} from "@/components/auth-provider"
import {Navigate} from "react-router"
import {useEffect, useState} from "react";
import {setupApi} from "@/lib/api.ts";

export default function LoginPage() {
  const { user, loading } = useAuth()
  const [setupComplete, setSetupComplete] = useState<boolean | null>(null)

  useEffect(() => {
    setupApi.apiV1SetupCompleteGet()
        .then(setSetupComplete)
        .catch(() => setSetupComplete(true)) // Assume complete on error to avoid loop
  }, [])

  if (loading || setupComplete === null) {
    return (
      <div className="flex min-h-svh items-center justify-center">
        <p className="text-muted-foreground animate-pulse">Loading...</p>
      </div>
    )
  }

  if (setupComplete === false) {
    return <Navigate to="/setup" replace />
  }

  if (user) {
    return <Navigate to="/" replace />
  }

  return (
    <div className="flex min-h-svh items-center justify-center p-6 md:p-10">
      <div className="w-full max-w-sm">
        <LoginForm />
      </div>
    </div>
  )
}
