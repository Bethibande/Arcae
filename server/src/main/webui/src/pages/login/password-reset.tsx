import {useState} from "react"
import {authApi} from "@/lib/api"
import {cn} from "@/lib/utils"
import {Button} from "@/components/ui/button"
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card"
import {Field, FieldGroup, FieldLabel} from "@/components/ui/field"
import {Input} from "@/components/ui/input"
import {InputOTP, InputOTPGroup, InputOTPSeparator, InputOTPSlot} from "@/components/ui/input-otp"
import {Link, useNavigate} from "react-router"
import {toast} from "sonner"
import {ArrowLeft, Loader2} from "lucide-react"

export default function PasswordResetPage() {
  const [step, setStep] = useState<1 | 2>(1)
  const [email, setEmail] = useState("")
  const [token, setToken] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const handleRequestReset = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    try {
      await authApi.apiV1AuthResetRequestPost({ email })
      toast.success("Reset token sent! It might take a moment to arrive in your inbox.")
      setStep(2)
    } catch {
      toast.error("Failed to request password reset. Please try again.")
    } finally {
      setLoading(false)
    }
  }

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault()
    if (newPassword !== confirmPassword) {
      toast.error("Passwords do not match.")
      return
    }

    setLoading(true)
    try {
      await authApi.apiV1AuthResetPost({
        passwordResetCredentials: {
          email,
          token,
          newPassword,
        },
      })
      toast.success("Password reset successfully! You can now log in with your new password.")
      navigate("/login")
    } catch {
      toast.error("Failed to reset password. The token might be invalid or expired.")
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-svh items-center justify-center p-6 md:p-10">
      <div className="w-full max-w-lg">
        <div className={cn("flex flex-col gap-6")}>
          <Card>
            <CardHeader className="text-center">
              <CardTitle className="text-xl">
                {step === 1 ? "Reset Password" : "Enter Reset Token"}
              </CardTitle>
              <CardDescription>
                {step === 1
                  ? "Enter your email address to receive a reset token."
                  : "Check your email for the reset token and enter it below along with your new password."}
              </CardDescription>
            </CardHeader>
            <CardContent>
              {step === 1 ? (
                <form onSubmit={handleRequestReset}>
                  <FieldGroup>
                    <Field>
                      <FieldLabel htmlFor="email">Email</FieldLabel>
                      <Input
                        id="email"
                        type="email"
                        placeholder="me@example.com"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                        disabled={loading}
                      />
                    </Field>
                    <Button type="submit" className="w-full" disabled={loading}>
                      {loading ? (
                        <>
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          Sending...
                        </>
                      ) : (
                        "Send Reset Token"
                      )}
                    </Button>
                    <div className="text-center text-sm">
                      <Link to="/login" className="inline-flex items-center hover:underline text-muted-foreground">
                        <ArrowLeft className="mr-2 h-4 w-4" />
                        Back to login
                      </Link>
                    </div>
                  </FieldGroup>
                </form>
              ) : (
                <form onSubmit={handleResetPassword}>
                  <FieldGroup>
                    <Field>
                      <FieldLabel>Reset Token</FieldLabel>
                      <div className="flex justify-center">
                        <InputOTP
                          maxLength={9}
                          value={token}
                          onChange={(val) => setToken(val)}
                          disabled={loading}
                        >
                          <InputOTPGroup className={"*:data-[slot=input-otp-slot]:h-12 *:data-[slot=input-otp-slot]:w-11 *:data-[slot=input-otp-slot]:text-xl"}>
                            <InputOTPSlot index={0} />
                            <InputOTPSlot index={1} />
                            <InputOTPSlot index={2} />
                          </InputOTPGroup>
                          <InputOTPSeparator className="mx-2" />
                          <InputOTPGroup className={"*:data-[slot=input-otp-slot]:h-12 *:data-[slot=input-otp-slot]:w-11 *:data-[slot=input-otp-slot]:text-xl"}>
                            <InputOTPSlot index={3} />
                            <InputOTPSlot index={4} />
                            <InputOTPSlot index={5} />
                          </InputOTPGroup>
                          <InputOTPSeparator className="mx-2" />
                          <InputOTPGroup className={"*:data-[slot=input-otp-slot]:h-12 *:data-[slot=input-otp-slot]:w-11 *:data-[slot=input-otp-slot]:text-xl"}>
                            <InputOTPSlot index={6} />
                            <InputOTPSlot index={7} />
                            <InputOTPSlot index={8} />
                          </InputOTPGroup>
                        </InputOTP>
                      </div>
                    </Field>
                    <Field>
                      <FieldLabel htmlFor="newPassword">New Password</FieldLabel>
                      <Input
                        id="newPassword"
                        type="password"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        required
                        disabled={loading}
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
                        disabled={loading}
                      />
                    </Field>
                    <Button type="submit" className="w-full" disabled={loading}>
                      {loading ? (
                        <>
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          Resetting...
                        </>
                      ) : (
                        "Reset Password"
                      )}
                    </Button>
                    <div className="text-center text-sm">
                      <button
                        type="button"
                        onClick={() => setStep(1)}
                        className="inline-flex items-center hover:underline text-muted-foreground"
                        disabled={loading}
                      >
                        <ArrowLeft className="mr-2 h-4 w-4" />
                        Change email
                      </button>
                    </div>
                  </FieldGroup>
                </form>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
