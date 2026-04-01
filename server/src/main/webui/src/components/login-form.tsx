import {useEffect, useState} from "react"
import {useAuth} from "@/components/auth-provider"
import {cn} from "@/lib/utils"
import {Link} from "react-router"
import {Button} from "@/components/ui/button"
import {Card, CardContent, CardDescription, CardHeader, CardTitle,} from "@/components/ui/card"
import {Field, FieldGroup, FieldLabel,} from "@/components/ui/field"
import {Input} from "@/components/ui/input"
import {oidcApi} from "@/lib/api.ts";
import {type OpenIdConnectLoginItem} from "@/generated";
import {showError} from "@/lib/errors.ts";

export function LoginForm({
  className,
  ...props
}: React.ComponentProps<"div">) {
  const { login } = useAuth()
  const [user, setUser] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [oidcItems, setOidcItems] = useState<OpenIdConnectLoginItem[]>([])
  const [oidcLoading, setOidcLoading] = useState(false)

  useEffect(() => {
    oidcApi.apiV1OidcLoginGet()
        .then(setOidcItems)
        .catch(console.error)
  }, [])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await login({ username: user, password })
    } catch {
      setError("Invalid email or password")
    } finally {
      setLoading(false)
    }
  }

  const handleOidcLogin = async (provider: string) => {
    setOidcLoading(true)
    try {
      const url = await oidcApi.apiV1OidcLoginProviderGet({ provider })
      window.location.href = url
    } catch (e) {
      showError(e)
    } finally {
      setOidcLoading(false)
    }
  }

  return (
    <div className={cn("flex flex-col gap-6", className)} {...props}>
      <Card>
        <CardHeader className="text-center">
          <CardTitle className="text-xl">Welcome back</CardTitle>
          <CardDescription>
            Login with your account
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit}>
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="email">Username or Email</FieldLabel>
                <Input
                  id="email"
                  type="text"
                  placeholder="me@example.com"
                  value={user}
                  onChange={(e) => setUser(e.target.value)}
                  required
                />
              </Field>
              <Field>
                <div className="flex items-center">
                  <FieldLabel htmlFor="password">Password</FieldLabel>
                  <Link
                    to="/login/reset"
                    className="ml-auto text-sm underline-offset-4 hover:underline"
                  >
                    Forgot password?
                  </Link>
                </div>
                <Input
                  id="password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </Field>
              {error && (
                <p className="text-sm font-medium text-destructive">{error}</p>
              )}
              <Field>
                <Button type="submit" disabled={loading || oidcLoading}>
                  {loading ? "Logging in..." : "Login"}
                </Button>
              </Field>
            </FieldGroup>
          </form>

          {oidcItems.length > 0 && (
              <div className="mt-6 space-y-4">
                <div className="relative flex justify-center text-xs uppercase">
                  <span className="px-2 text-muted-foreground">Or continue with</span>
                  <div className="absolute inset-0 -z-10 flex items-center">
                    <span className="w-full border-t"/>
                  </div>
                </div>
                <div className="grid gap-2">
                  {oidcItems.map((item) => (
                      <Button
                          key={item.label}
                          variant="outline"
                          type="button"
                          onClick={() => handleOidcLogin(item.label!)}
                          disabled={loading || oidcLoading}
                      >
                        {item.label}
                      </Button>
                  ))}
                </div>
              </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
