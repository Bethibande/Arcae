import {Card, CardContent, CardFooter, CardHeader, CardTitle} from "@/components/ui/card.tsx";
import i18next from "i18next";
import {FieldGroup} from "@/components/ui/field.tsx";
import {z} from "zod";
import {useForm} from "react-hook-form";
import {zodResolver} from "@hookform/resolvers/zod";
import {FormField} from "@/components/form-field.tsx";
import {Input} from "@/components/ui/input.tsx";
import {Button} from "@/components/ui/button.tsx";
import {handleSubmit} from "@/lib/forms.ts";
import {SetupEndpointApi} from "@/generated";
import {useAuth} from "@/lib/auth.tsx";
import {showError} from "@/lib/errors.ts";
import {useNavigate} from "react-router";

export const SetupUserTranslationsEN = {
    "title": "Create admin user",
    "passwords.dontMatch": "Password don't match",
    "username": "Username",
    "email": "E-Mail",
    "password": "Password",
    "passwordRepeat": "Repeat Password",
    "cancel": "Cancel",
    "create": "Create",
}

function t(key: string, args?: Record<any, any>): string {
    return i18next.t(key, {ns: "views.onboarding.user", ...args})
}

export function SetupUserView() {
    const formSchema = z.object({
        name: z.string().min(3).max(64),
        email: z.string().email().max(512),
        password: z.string().min(8).max(256),
        passwordRepeat: z.string()
    }).superRefine(({password, passwordRepeat}, ctx) => {
        if (password !== passwordRepeat) {
            ctx.addIssue({
                code: "custom",
                message: t("passwords.dontMatch"),
                path: ["passwordRepeat"]
            })
        }
    })

    const defaultValues: z.input<typeof formSchema> = {
        name: "",
        email: "",
        password: "",
        passwordRepeat: ""
    }

    const form = useForm({
        resolver: zodResolver(formSchema),
        defaultValues
    })

    const {login} = useAuth()
    const navigate = useNavigate()

    function submit(data: z.output<typeof formSchema>) {
        new SetupEndpointApi().apiV1SetupUserPost({
            userDTOWithoutId: data
        }).then(() => {
            login(data.name, data.password)
                .then(() => navigate("/"))
                .catch(showError)
        }).catch(showError)
    }

    return (
        <div className={"w-full h-full flex items-center justify-center"}>
            <form onSubmit={handleSubmit(form, submit)}>
                <Card className={"w-96"}>
                    <CardHeader>
                        <CardTitle>{t("title")}</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <FieldGroup>
                            <FormField fieldName={"name"}
                                       label={t("username")}
                                       Input={props => (<Input {...props} placeholder={"admin"}/>)}
                                       control={form.control}/>
                            <FormField fieldName={"email"}
                                       label={t("email")}
                                       Input={props => (
                                           <Input {...props} type={"email"} placeholder={"admin@my.org"}/>)}
                                       control={form.control}/>
                            <FormField fieldName={"password"}
                                       label={t("password")}
                                       Input={props => (<Input {...props} type={"password"} placeholder={"●●●●●●●"}/>)}
                                       control={form.control}/>
                            <FormField fieldName={"passwordRepeat"}
                                       label={t("passwordRepeat")}
                                       Input={props => (<Input {...props} type={"password"} placeholder={"●●●●●●●"}/>)}
                                       control={form.control}/>
                        </FieldGroup>
                    </CardContent>
                    <CardFooter>
                        <div className={"flex gap-2 w-full justify-end"}>
                            <Button variant={"outline"}>{t("cancel")}</Button>
                            <Button>{t("create")}</Button>
                        </div>
                    </CardFooter>
                </Card>
            </form>
        </div>
    )
}
