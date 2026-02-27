import {z} from "zod";
import {type Control, Controller, type FieldPath, type FieldValues, useWatch} from "react-hook-form";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {S3ConfigForm, s3Schema} from "@/components/repository/S3ConfigForm.tsx";
import {FormField} from "@/components/form-field.tsx";
import {useEffect, useState} from "react";
import {SystemEndpointApi} from "@/generated";
import {Switch} from "@/components/ui/switch.tsx";
import {MirrorConfigForm, mirrorSchema} from "@/components/repository/MirrorConfigForm.tsx";

export const ociSchema = z.object({
    s3Config: s3Schema,
    routingConfig: z.object({
        enabled: z.boolean(),
        targetService: z.string().optional(),
        targetPort: z.coerce.number().min(1).max(65535).optional(),
        gatewayName: z.string().optional(),
        gatewayNamespace: z.string().optional(),
    }).refine(data => !data.enabled || (data.targetService && data.targetPort && data.gatewayName && data.gatewayNamespace), {
        message: "All routing fields are required when routing is enabled",
        path: ["enabled"]
    }),
    allowRedeployments: z.boolean().optional(),
    mirrorConfig: mirrorSchema,
});

export type OCIConfig = z.infer<typeof ociSchema>;

export const defaultOCIConfig: OCIConfig = {
    s3Config: {
        url: "",
        region: "",
        bucket: "",
        accessKey: "",
        secretKey: ""
    },
    routingConfig: {
        enabled: false,
        targetService: "",
        targetPort: 80,
        gatewayName: "",
        gatewayNamespace: ""
    },
    allowRedeployments: true,
    mirrorConfig: {
        connections: [],
        enabled: false,
        storeArtifacts: true,
        authorizedUsersOnly: false,
    }
};

interface OCIConfigFormProps<TFieldValues extends FieldValues> {
    control: Control<TFieldValues, any, any>;
    prefix: string;
}

export function OCIConfigForm<TFieldValues extends FieldValues>({ control, prefix }: OCIConfigFormProps<TFieldValues>) {
    const [k8sRoutingSupported, setK8sRoutingSupported] = useState(false);
    const routingToggleId = "oci-routing-toggle";
    const redeployToggleId = "oci-redeploy-toggle";

    useEffect(() => {
        new SystemEndpointApi().apiV1SystemK8sCapabilitiesGet()
            .then(capabilities => {
                setK8sRoutingSupported(capabilities.routing);
            })
            .catch(err => {
                console.error("Failed to fetch k8s capabilities", err);
            });
    }, []);

    const routingEnabled = useWatch({
        control,
        name: `${prefix}.routingConfig.enabled` as any
    });

    return (
        <div className="space-y-6">
            <div id="behavior" className="space-y-6 pt-4">
                <h2 className="text-xl font-bold tracking-tight">Behavioral Policies</h2>
                <Card>
                    <CardContent className="divide-y p-0">
                        <div className="flex items-center justify-between p-6">
                            <div className="space-y-0.5">
                                <label className="text-base font-semibold" htmlFor={redeployToggleId}>Allow re-deployments</label>
                                <p className="text-sm text-muted-foreground">
                                    Permit overwriting existing tags by pushing again (enable if you want to allow re-deployments).
                                </p>
                            </div>
                            <div className="flex-none">
                                <Controller
                                    name={`${prefix}.allowRedeployments` as any}
                                    control={control}
                                    render={({ field }) => (
                                        <Switch
                                            id={redeployToggleId}
                                            checked={!!field.value}
                                            onCheckedChange={field.onChange}
                                        />
                                    )}
                                />
                            </div>
                        </div>
                    </CardContent>
                </Card>
            </div>

            <S3ConfigForm control={control} prefix={`${prefix}.s3Config`} />

            <div id="external-access" className="space-y-6 pt-4">
                <h2 className="text-xl font-bold tracking-tight">External Access</h2>
                <Card>
                    <CardContent className="divide-y p-0">
                        <div className="p-6 space-y-4">
                            <FormField
                                label="External Host"
                                fieldName={`externalHost` as FieldPath<TFieldValues>}
                                control={control}
                                placeholder="oci.test.org:8080"
                            />
                            <p className="text-sm text-muted-foreground">
                                The expected host name for connecting to the OCI repository (e.g., host or host:port).
                            </p>
                        </div>

                        {k8sRoutingSupported && (
                            <div className="p-6 space-y-4">
                                <div className="flex items-center justify-between">
                                    <div className="space-y-0.5">
                                        <label className="text-base font-semibold" htmlFor={routingToggleId}>Kubernetes Gateway Routing</label>
                                        <p className="text-sm text-muted-foreground">
                                            Automatically create a Kubernetes HTTPRoute for this repository.
                                        </p>
                                    </div>
                                    <div className="flex-none">
                                        <Controller
                                            name={`${prefix}.routingConfig.enabled` as any}
                                            control={control}
                                            render={({field}) => (
                                                <Switch
                                                    id={routingToggleId}
                                                    checked={field.value}
                                                    onCheckedChange={field.onChange}
                                                />
                                            )}
                                        />
                                    </div>
                                </div>

                                {routingEnabled && (
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-4 border-t">
                                        <FormField
                                            label="Target Service Name"
                                            fieldName={`${prefix}.routingConfig.targetService` as any}
                                            control={control}
                                            placeholder="my-service"
                                        />
                                        <FormField
                                            label="Target Service Port"
                                            fieldName={`${prefix}.routingConfig.targetPort` as any}
                                            control={control}
                                            type="number"
                                            placeholder="8080"
                                        />
                                        <FormField
                                            label="Gateway Name"
                                            fieldName={`${prefix}.routingConfig.gatewayName` as any}
                                            control={control}
                                            placeholder="external-gateway"
                                        />
                                        <FormField
                                            label="Gateway Namespace"
                                            fieldName={`${prefix}.routingConfig.gatewayNamespace` as any}
                                            control={control}
                                            placeholder="istio-system"
                                        />
                                    </div>
                                )}
                            </div>
                        )}
                    </CardContent>
                </Card>
            </div>

            <MirrorConfigForm
                control={control}
                prefix={`${prefix}.mirrorConfig`}
                urlPlaceholder="https://registry-1.docker.io"
            />
        </div>
    );
}
