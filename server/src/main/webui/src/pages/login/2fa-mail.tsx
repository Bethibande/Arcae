import {useEffect, useState} from "react"
import {authApi} from "@/lib/api"
import {useAuth} from "@/components/auth-provider"
import {Navigate, useNavigate} from "react-router"
import {cn} from "@/lib/utils"
import {Button} from "@/components/ui/button"
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card"
import {Field, FieldGroup, FieldLabel} from "@/components/ui/field"
import {InputOTP, InputOTPGroup, InputOTPSlot} from "@/components/ui/input-otp"
import {toast} from "sonner"
import {Loader2, Mail, RefreshCw} from "lucide-react"

export default function TwoFAMailPage() {
  const { user, loading: authLoading, login2fa } = useAuth()
  const [token, setToken] = useState("")
  const [loading, setLoading] = useState(false)
  const [cooldown, setCooldown] = useState(60)
  const navigate = useNavigate()

  useEffect(() => {
    // Initial send
    authApi.apiV1Auth2faMailGet()
      .catch((e) => {
        console.error("Failed to send 2FA mail:", e)
        toast.error("Failed to send 2FA email. Please try again.")
      })
  }, [])

  useEffect(() => {
    if (cooldown > 0) {
      const timer = setInterval(() => setCooldown((prev) => prev - 1), 1000)
      return () => clearInterval(timer)
    }
  }, [cooldown > 0])

  const handleResend = async () => {
    if (cooldown > 0) return
    setLoading(true)
    try {
      await authApi.apiV1Auth2faMailGet()
      toast.success("A new code has been sent to your email.")
      setCooldown(60)
    } catch {
      toast.error("Failed to resend 2FA email. Please try again later.")
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = async (e?: React.FormEvent) => {
    e?.preventDefault()
    if (token.length !== 6) return

    setLoading(true)
    try {
      await login2fa(token)
      toast.success("Login successful!")
      navigate("/")
    } catch (e) {
      console.error(e)
      toast.error("Invalid or expired 2FA code.")
    } finally {
      setLoading(false)
    }
  }

  // Effect to auto-submit when 6 digits are entered
  useEffect(() => {
    if (token.length === 6) {
      handleSubmit()
    }
  }, [token])

  if (authLoading) {
    return (
      <div className="flex min-h-svh items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (user) {
    return <Navigate to="/" replace />
  }

  return (
    <div className="flex min-h-svh items-center justify-center p-6 md:p-10">
      <div className="w-full max-w-sm">
        <div className={cn("flex flex-col gap-6")}>
          <Card>
            <CardHeader className="text-center">
              <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-primary/10">
                <Mail className="h-6 w-6 text-primary" />
              </div>
              <CardTitle className="text-xl">Two-Step Verification</CardTitle>
              <CardDescription>
                We've sent a 6-digit verification code to your email address.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit}>
                <FieldGroup>
                  <Field>
                    <FieldLabel className="text-center">Verification Code</FieldLabel>
                    <div className="flex justify-center">
                      <InputOTP
                        maxLength={6}
                        value={token}
                        onChange={(val) => setToken(val)}
                        disabled={loading}
                      >
                        <InputOTPGroup className={"*:data-[slot=input-otp-slot]:h-12 *:data-[slot=input-otp-slot]:w-11 *:data-[slot=input-otp-slot]:text-xl"}>
                          <InputOTPSlot index={0} />
                          <InputOTPSlot index={1} />
                          <InputOTPSlot index={2} />
                          <InputOTPSlot index={3} />
                          <InputOTPSlot index={4} />
                          <InputOTPSlot index={5} />
                        </InputOTPGroup>
                      </InputOTP>
                    </div>
                  </Field>
                  <Button type="submit" className="w-full" disabled={loading || token.length !== 6}>
                    {loading ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        Verifying...
                      </>
                    ) : (
                      "Verify"
                    )}
                  </Button>
                  <div className="text-center text-sm">
                    <button
                      type="button"
                      onClick={handleResend}
                      disabled={loading || cooldown > 0}
                      className="inline-flex items-center text-muted-foreground hover:text-primary transition-colors disabled:opacity-50"
                    >
                      {cooldown > 0 ? (
                        `Resend code in ${cooldown}s`
                      ) : (
                        <>
                          <RefreshCw className="mr-2 h-4 w-4" />
                          Resend code
                        </>
                      )}
                    </button>
                  </div>
                </FieldGroup>
              </form>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
