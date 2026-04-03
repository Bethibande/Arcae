import {Mail, Send, Sparkles} from "lucide-react";
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card.tsx";
import {Button} from "@/components/ui/button.tsx";
import {toast} from "sonner";
import {mailApi} from "@/lib/api.ts";
import {FormField} from "@/components/form-field.tsx";
import {Controller, useForm} from "react-hook-form";
import {zodResolver} from "@hookform/resolvers/zod";
import * as z from "zod";
import {Switch} from "@/components/ui/switch.tsx";
import {Field, FieldContent, FieldDescription, FieldLabel} from "@/components/ui/field.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {MailEncryption} from "@/generated";
import {useEffect, useState} from "react";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog.tsx";
import {Input} from "@/components/ui/input.tsx";
import {Tooltip, TooltipContent, TooltipTrigger} from "@/components/ui/tooltip.tsx";

const mailSchema = z.object({
    enabled: z.boolean(),
    from: z.email("Invalid email address.").or(z.literal("")),
    host: z.string().or(z.literal("")),
    port: z.number().int().min(1).max(65535),
    password: z.string().or(z.literal("")),
    tls: z.enum(MailEncryption),
});

type MailFormValues = z.input<typeof mailSchema>;

interface TestMailDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
}

function TestMailDialog({open, onOpenChange}: TestMailDialogProps) {
    const [recipient, setRecipient] = useState("");
    const [loading, setLoading] = useState(false);

    const handleSend = async () => {
        if (!recipient || !recipient.includes("@")) {
            toast.error("Please enter a valid email address.");
            return;
        }

        setLoading(true);
        try {
            const result = await mailApi.apiV1MailTestPut({to: recipient});
            if (result.success) {
                toast.success("Test email sent successfully.");
                onOpenChange(false);
            } else {
                toast.error(`Failed to send test email: ${result.error}`);
            }
        } catch (error) {
            console.error("Failed to send test email", error);
            toast.error("Failed to send test email.");
        } finally {
            setLoading(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Send Test Email</DialogTitle>
                    <DialogDescription>
                        Enter an email address to receive a test message and verify your SMTP configuration.
                    </DialogDescription>
                </DialogHeader>
                <div className="space-y-4 py-4">
                    <Field>
                        <FieldLabel htmlFor="test-recipient">Recipient Email</FieldLabel>
                        <Input
                            id="test-recipient"
                            placeholder="test@example.com"
                            value={recipient}
                            onChange={(e) => setRecipient(e.target.value)}
                        />
                    </Field>
                </div>
                <DialogFooter>
                    <Button variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
                        Cancel
                    </Button>
                    <Button onClick={handleSend} disabled={loading}>
                        {loading ? "Sending..." : "Send Test"}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

export function MailTab() {
    const [loading, setLoading] = useState(false);
    const [fetching, setFetching] = useState(true);
    const [testDialogOpen, setTestDialogOpen] = useState(false);

    const {
        handleSubmit,
        control,
        reset,
        watch,
        setValue,
        getValues,
    } = useForm<MailFormValues>({
        resolver: zodResolver(mailSchema),
        defaultValues: {
            enabled: false,
            from: "",
            host: "",
            port: 25,
            password: "",
            tls: MailEncryption.None,
        },
    });

    const enabled = watch("enabled");

    useEffect(() => {
        const fetchConfig = async () => {
            try {
                const config = await mailApi.apiV1MailConfigGet();
                reset({
                    enabled: config.enabled,
                    from: config.from ?? "",
                    host: config.host ?? "",
                    port: config.port,
                    password: config.password ?? "",
                    tls: config.encryption ?? MailEncryption.None,
                });
            } catch (error) {
                console.error("Failed to fetch mail config", error);
                toast.error("Failed to load mail configuration.");
            } finally {
                setFetching(false);
            }
        };

        fetchConfig();
    }, [reset]);

    const onSubmit = async (values: MailFormValues) => {
        setLoading(true);
        try {
            await mailApi.apiV1MailConfigPut({
                sMTPConfig: {
                    enabled: values.enabled,
                    from: values.from === "" ? undefined : values.from,
                    host: values.host === "" ? undefined : values.host,
                    port: values.port,
                    password: values.password === "" ? undefined : values.password,
                    encryption: values.tls,
                }
            });
            toast.success("Mail configuration updated successfully.");
        } catch (error) {
            console.error("Failed to update mail config", error);
            toast.error("Failed to update mail configuration.");
        } finally {
            setLoading(false);
        }
    };

    const handleAutoDiscover = async () => {
        const from = getValues("from");
        if (!from || !from.includes("@")) {
            toast.error("Please enter a valid from address first.");
            return;
        }

        setLoading(true);
        try {
            const settings = await mailApi.apiV1MailAutodiscoverGet({ from: from });
            if (settings) {
                setValue("host", settings.host ?? "");
                setValue("port", settings.port);
                setValue("tls", settings.tls ?? MailEncryption.None);
                toast.success("Mail settings discovered successfully.");
            } else {
                toast.error("Could not discover mail settings for this address.");
            }
        } catch (error) {
            console.error("Auto-discovery failed", error);
            toast.error("Auto-discovery failed.");
        } finally {
            setLoading(false);
        }
    };

    if (fetching) {
        return (
            <div className="flex items-center justify-center p-12">
                <span className="text-muted-foreground">Loading configuration...</span>
            </div>
        );
    }

    return (
        <section className="space-y-6">
            <div>
                <h2 className="text-xl font-semibold flex items-center gap-2">
                    <Mail className="size-5"/> Mail Settings
                </h2>
                <p className="text-sm text-muted-foreground mt-1">
                    Configure SMTP settings for system notifications and user invites.
                </p>
            </div>

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
                <Card>
                    <CardContent className="p-6">
                        <Controller
                            name="enabled"
                            control={control}
                            render={({field}) => (
                                <Field orientation="horizontal" className="justify-between">
                                    <FieldContent>
                                        <FieldLabel>Enable Mailer</FieldLabel>
                                        <FieldDescription>
                                            Turn on system-wide email functionality.
                                        </FieldDescription>
                                    </FieldContent>
                                    <Switch
                                        className={"mt-auto mb-auto"}
                                        checked={field.value}
                                        onCheckedChange={field.onChange}
                                    />
                                </Field>
                            )}
                        />
                    </CardContent>
                </Card>

                {enabled && (
                    <>
                        <Card>
                            <CardHeader>
                                <CardTitle>Sender Configuration</CardTitle>
                                <CardDescription>
                                    Configure the sender email address and credentials.
                                </CardDescription>
                            </CardHeader>
                            <CardContent className="space-y-4">
                                <div className="flex items-end gap-2">
                                    <FormField
                                        control={control}
                                        fieldName="from"
                                        label="From Address"
                                        placeholder="noreply@example.com"
                                        className="flex-1"
                                    />
                                    <Tooltip>
                                        <TooltipTrigger asChild>
                                            <Button
                                                type="button"
                                                variant="outline"
                                                size="icon"
                                                onClick={handleAutoDiscover}
                                            >
                                                <Sparkles className="size-4"/>
                                            </Button>
                                        </TooltipTrigger>
                                        <TooltipContent>
                                            <p>Automatically discover mail server settings. Works only if supported by the server.</p>
                                        </TooltipContent>
                                    </Tooltip>
                                </div>
                                <FormField
                                    control={control}
                                    fieldName="password"
                                    label="SMTP Password"
                                    type="password"
                                    placeholder="••••••••"
                                />
                            </CardContent>
                        </Card>

                        <Card>
                            <CardHeader>
                                <CardTitle>Server Configuration</CardTitle>
                                <CardDescription>
                                    SMTP server connection details and security.
                                </CardDescription>
                            </CardHeader>
                            <CardContent className="space-y-4">
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                    <FormField
                                        control={control}
                                        fieldName="host"
                                        label="SMTP Host"
                                        placeholder="smtp.example.com"
                                    />
                                    <FormField
                                        control={control}
                                        fieldName="port"
                                        label="SMTP Port"
                                        type="number"
                                        placeholder="25"
                                    />
                                </div>
                                <Controller
                                    name="tls"
                                    control={control}
                                    render={({field}) => (
                                        <Field>
                                            <FieldLabel>Encryption</FieldLabel>
                                            <Select
                                                value={field.value}
                                                onValueChange={field.onChange}
                                            >
                                                <SelectTrigger>
                                                    <SelectValue placeholder="Select TLS mode"/>
                                                </SelectTrigger>
                                                <SelectContent>
                                                    <SelectItem value={MailEncryption.None}>None</SelectItem>
                                                    <SelectItem value={MailEncryption.Ssl}>SSL</SelectItem>
                                                    <SelectItem value={MailEncryption.StartTls}>Start TLS</SelectItem>
                                                </SelectContent>
                                            </Select>
                                        </Field>
                                    )}
                                />
                            </CardContent>
                        </Card>
                    </>
                )}

                <div className="flex justify-end gap-3">
                    <Button
                        type="button"
                        variant="outline"
                        onClick={() => setTestDialogOpen(true)}
                        disabled={loading || !enabled}
                        className="gap-2"
                    >
                        <Send className="size-4"/>
                        Send Test Message
                    </Button>
                    <Button type="submit" disabled={loading}>
                        Save Changes
                    </Button>
                </div>
            </form>

            <TestMailDialog
                open={testDialogOpen}
                onOpenChange={setTestDialogOpen}
            />
        </section>
    );
}
