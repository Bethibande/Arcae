import {useForm} from "react-hook-form";
import {defaultOciSettings, ociSettingsSchema, type OciSettingsSchema} from "@/pages/repository/settings/schema.tsx";
import {zodResolver} from "@hookform/resolvers/zod";
import {useEffect, useImperativeHandle} from "react";
import {type ExtraFormProps} from "@/pages/repository/settings/extra-form.tsx";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {Field, FieldDescription, FieldLabel} from "@/components/ui/field.tsx";
import {Switch} from "@/components/ui/switch.tsx";
import {S3Form} from "@/pages/repository/settings/s3-form.tsx";
import {MirrorForm} from "@/pages/repository/settings/mirror-form.tsx";
import {Separator} from "@/components/ui/separator.tsx";
import {Globe, ShieldAlert} from "lucide-react";
import {FormField} from "@/components/form-field.tsx";

import {PackageManager} from "@/generated";

export function OciSettingsForm({repository, value, save}: ExtraFormProps) {
    const form = useForm<any>({
        resolver: zodResolver(ociSettingsSchema),
        defaultValues: value ? JSON.parse(value) as OciSettingsSchema : defaultOciSettings
    });

    useEffect(() => {
        if (value) {
            form.reset(JSON.parse(value) as any);
        }
    }, [value, form]);

    useImperativeHandle(save, () => {
        return async () => {
            const values = form.getValues();
            const isValid = await form.trigger();
            if (!isValid) return null;
            return JSON.stringify(values);
        };
    });

    return (
        <div className="space-y-16">
            <section id="behavioral" className="space-y-6">
                <h2 className="text-xl font-semibold flex items-center gap-2"><ShieldAlert/> Behavioral Policies</h2>
                <Card>
                    <CardContent className="p-6">
                        <Field orientation="horizontal" className="justify-between">
                            <div className="space-y-0.5">
                                <FieldLabel>Allow re-deployments</FieldLabel>
                                <FieldDescription>
                                    Permit overwriting existing tags by pushing again (enable if you want to allow
                                    re-deployments).
                                </FieldDescription>
                            </div>
                            <Switch
                                checked={form.watch("allowRedeployments")}
                                onCheckedChange={(checked) => form.setValue("allowRedeployments", checked)}
                            />
                        </Field>
                    </CardContent>
                </Card>
            </section>

            <S3Form form={form}/>

            <MirrorForm form={form} packageManager={PackageManager.Oci} repositoryId={repository.id} placeholder="https://registry-1.docker.io" />

            <section id="external" className="space-y-6">
                <h2 className="text-xl font-semibold flex items-center gap-2"><Globe/> External Access</h2>
                <Card>
                <CardContent className="p-0 space-y-6">
                        <div className="p-6">
                            <FormField
                                control={form.control}
                                fieldName="externalHostname"
                                label="External Hostname"
                                placeholder="oci.example.com"
                            />
                            <FieldDescription className="mt-2">The hostname this repository is accessible through.</FieldDescription>
                        </div>

                        <Separator/>

                        <div className="p-6 pt-4 space-y-6">
                            <Field orientation="horizontal" className="justify-between">
                                <div className="space-y-0.5">
                                    <FieldLabel>Kubernetes gateway routing</FieldLabel>
                                    <FieldDescription>
                                        Automatically configures a HTTPRoute resource for the repository.
                                    </FieldDescription>
                                </div>
                                <Switch
                                    checked={form.watch("routingConfig.enabled")}
                                    onCheckedChange={(checked) => form.setValue("routingConfig.enabled", checked)}
                                />
                            </Field>

                            {form.watch("routingConfig.enabled") && (
                                <>
                                    <Separator/>
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 p-4">
                                        <FormField
                                            control={form.control}
                                            fieldName="routingConfig.targetService"
                                            label="Target Service"
                                            placeholder="repository-server"
                                        />
                                        <FormField
                                            control={form.control}
                                            fieldName="routingConfig.targetPort"
                                            label="Target Port"
                                            type="number"
                                            placeholder="8080"
                                        />
                                        <FormField
                                            control={form.control}
                                            fieldName="routingConfig.gatewayName"
                                            label="Gateway Name"
                                            placeholder="public-gateway"
                                        />
                                        <FormField
                                            control={form.control}
                                            fieldName="routingConfig.gatewayNamespace"
                                            label="Gateway Namespace"
                                            placeholder="istio-system"
                                        />
                                    </div>
                                </>
                            )}
                        </div>
                </CardContent>
                </Card>
            </section>
        </div>
    );
}
