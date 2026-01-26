import {Button} from "@/components/ui/button.tsx";
import {Card, CardContent, CardFooter, CardHeader, CardTitle} from "@/components/ui/card.tsx";
import {Field, FieldError, FieldGroup, FieldLabel} from "@/components/ui/field.tsx";
import {Input} from "@/components/ui/input.tsx";
import {showErrorMessage, showHttpError} from "@/lib/errors.ts";
import {toast} from "sonner";
import {useNavigate} from "react-router";
import {useAuth} from "@/lib/auth.tsx";
import {z} from "zod";
import {Controller, useForm} from "react-hook-form";
import {zodResolver} from "@hookform/resolvers/zod";
import i18next from "i18next";
import {getLastPath} from "@/lib/path-restore.ts";

export const LoginViewTranslationsEN = {
    "login": "Login",
    "username": "Username",
    "password": "Password",
    "welcome": "Welcome {{user}}",
    "error.credentials": "Invalid username or password",
}

function t(key: string, args?: Record<any, any>): string {
    return i18next.t(key, {ns: "views.login", ...args})
}

export default function LoginView() {
    const formSchema = z.object({
        username: z.string().min(3),
        password: z.string().min(3)
    })

    const form = useForm<z.infer<typeof formSchema>>({
        resolver: zodResolver(formSchema),
        defaultValues: {
            username: "",
            password: ""
        }
    })

    const navigate = useNavigate()
    const {login} = useAuth()

    function onSubmit(data: z.infer<typeof formSchema>) {
        login(data.username, data.password).then(({user, error}) => {
            console.log(user, error)
            if (user) {
                toast.success(t("welcome", {user: user.name}), {position: "top-center"})
                navigate(getLastPath())
            }
            if (error) {
                if (error.status === 401) {
                    showErrorMessage(t("error.credentials"))
                } else {
                    showHttpError(error)
                }
            }
        })
    }

    return (
        <div className={"w-full h-full flex items-center justify-center"}>
            <form onSubmit={form.handleSubmit(onSubmit)}>
                <Card className={"w-96"}>
                    <CardHeader>
                        <CardTitle>{t("login")}</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <FieldGroup>
                            <Controller name={"username"} control={form.control} render={({field, fieldState}) => {
                                return (
                                    <Field data-invalid={fieldState.invalid}>
                                        <FieldLabel htmlFor={"username"}>{t("username")}</FieldLabel>
                                        <Input {...field} id={"username"} type={"text"}
                                               aria-invalid={fieldState.invalid}
                                               placeholder={"Jon doe"}/>
                                        {fieldState.invalid && (
                                            <FieldError errors={[fieldState.error]}/>
                                        )}
                                    </Field>
                                )
                            }}/>
                            <Controller name={"password"} control={form.control} render={({field, fieldState}) => {
                                return (
                                    <Field data-invalid={fieldState.invalid}>
                                        <FieldLabel htmlFor={"password"}>{t("password")}</FieldLabel>
                                        <Input {...field} id={"password"} type={"password"}
                                               aria-invalid={fieldState.invalid}
                                               placeholder={"●●●●●●●●●"}/>
                                        {fieldState.invalid && (
                                            <FieldError errors={[fieldState.error]}/>
                                        )}
                                    </Field>
                                )
                            }}/>
                        </FieldGroup>
                    </CardContent>
                    <CardFooter>
                        <Button type={"submit"} className={"w-full"}>{t("login")}</Button>
                    </CardFooter>
                </Card>
            </form>
        </div>
    )
}