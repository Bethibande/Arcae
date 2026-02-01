import { z } from "zod";
import { FormField } from "@/components/form-field.tsx";
import { S3ConfigForm } from "@/components/repository/S3ConfigForm.tsx";
import { Switch } from "@/components/ui/switch.tsx";
import { Card, CardContent } from "@/components/ui/card.tsx";
import {type Control, type FieldPath, type FieldValues, useFieldArray, useFormContext} from "react-hook-form";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {Button} from "@/components/ui/button.tsx";
import {Plus, Trash2} from "lucide-react";

export const s3Schema = z.object({
    url: z.string().url().or(z.string().startsWith("http://")),
    region: z.string().min(1),
    bucket: z.string().min(1),
    accessKey: z.string().min(1),
    secretKey: z.string().min(1),
});

export const connectionSchema = z.object({
    url: z.string().url().or(z.string().startsWith("http://")).or(z.string().length(0)),
    authType: z.enum(["NONE", "BASIC", "BEARER"]),
    username: z.string(),
    password: z.string(),
});

export const mirrorSchema = z.object({
    connections: z.array(connectionSchema),
    enabled: z.boolean(),
    storeArtifacts: z.boolean(),
});

export const mavenSchema = z.object({
    allowRedeployments: z.boolean(),
    s3Config: s3Schema,
    mirrorConfig: mirrorSchema,
});

export type MavenConfig = z.infer<typeof mavenSchema>;

export const defaultMavenConfig: MavenConfig = {
    allowRedeployments: false,
    s3Config: {
        url: "",
        region: "",
        bucket: "",
        accessKey: "",
        secretKey: ""
    },
    mirrorConfig: {
        connections: [],
        enabled: false,
        storeArtifacts: true,
    }
};

interface MavenConfigFormProps<TFieldValues extends FieldValues> {
    control: Control<TFieldValues>;
    prefix: string;
}

export function MirrorConfigForm<TFieldValues extends FieldValues>({ control, prefix }: MavenConfigFormProps<TFieldValues>) {
    const { watch } = useFormContext();
    const enabled = watch(`${prefix}.enabled`);

    const { fields, append, remove } = useFieldArray({
        control,
        name: `${prefix}.connections` as any,
    });

    return (
        <div id="replication" className="space-y-6 pt-4">
            <h2 className="text-xl font-bold tracking-tight">Replication/Mirroring</h2>
            <Card>
                <CardContent className="p-6 space-y-4">
                    <div className="flex items-center justify-between">
                        <div className="space-y-0.5">
                            <label className="text-base font-semibold">Enable Mirroring</label>
                            <p className="text-sm text-muted-foreground">
                                Mirror artifacts from a remote repository when they are not found locally.
                            </p>
                        </div>
                        <div className="flex-none">
                            <FormField
                                label={""}
                                fieldName={`${prefix}.enabled` as FieldPath<TFieldValues>}
                                Input={({ value, onChange }) => (
                                    <Switch
                                        checked={value}
                                        onCheckedChange={onChange}
                                    />
                                )}
                                control={control}
                            />
                        </div>
                    </div>

                    {enabled && (
                        <div className="space-y-4 pt-4 border-t">
                            <div className="flex items-center justify-between py-2">
                                <div className="space-y-0.5">
                                    <label className="text-sm font-medium">Store Mirrored Artifacts</label>
                                    <p className="text-xs text-muted-foreground">
                                        Save artifacts fetched from the mirror to local storage.
                                    </p>
                                </div>
                                <div className="flex-none">
                                    <FormField
                                        label={""}
                                        fieldName={`${prefix}.storeArtifacts` as FieldPath<TFieldValues>}
                                        Input={({ value, onChange }) => (
                                            <Switch
                                                checked={value}
                                                onCheckedChange={onChange}
                                            />
                                        )}
                                        control={control}
                                    />
                                </div>
                            </div>

                            <div className="space-y-4">
                                <div className="flex items-center justify-between">
                                    <h3 className="text-sm font-medium">Connections</h3>
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={() => append({ url: "", authType: "NONE", username: "", password: "" })}
                                    >
                                        <Plus className="h-4 w-4 mr-2" />
                                        Add Connection
                                    </Button>
                                </div>

                                {fields.map((field, index) => (
                                    <ConnectionForm
                                        key={field.id}
                                        control={control}
                                        prefix={`${prefix}.connections.${index}`}
                                        onRemove={() => remove(index)}
                                    />
                                ))}

                                {fields.length === 0 && (
                                    <div className="text-center py-4 border border-dashed rounded-lg text-muted-foreground text-sm">
                                        No connections configured.
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}

interface ConnectionFormProps<TFieldValues extends FieldValues> {
    control: Control<TFieldValues>;
    prefix: string;
    onRemove: () => void;
}

function ConnectionForm<TFieldValues extends FieldValues>({ control, prefix, onRemove }: ConnectionFormProps<TFieldValues>) {
    const { watch } = useFormContext();
    const authType = watch(`${prefix}.authType`);

    return (
        <div className="space-y-4 p-4 border rounded-lg relative">
            <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                className="absolute top-2 right-2 text-muted-foreground hover:text-destructive"
                onClick={onRemove}
            >
                <Trash2 className="h-4 w-4" />
            </Button>

            <FormField
                label="Mirror URL"
                fieldName={`${prefix}.url` as FieldPath<TFieldValues>}
                control={control}
                placeholder="https://repo1.maven.org/maven2/"
            />

            <FormField
                label="Authentication Type"
                fieldName={`${prefix}.authType` as FieldPath<TFieldValues>}
                control={control}
                Input={({ value, onChange }) => (
                    <Select value={value} onValueChange={onChange}>
                        <SelectTrigger>
                            <SelectValue placeholder="Select Auth Type" />
                        </SelectTrigger>
                        <SelectContent>
                            <SelectItem value="NONE">None</SelectItem>
                            <SelectItem value="BASIC">Basic Auth</SelectItem>
                            <SelectItem value="BEARER">Bearer Token</SelectItem>
                        </SelectContent>
                    </Select>
                )}
            />

            {authType === "BASIC" && (
                <div className="grid grid-cols-2 gap-4">
                    <FormField
                        label="Username"
                        fieldName={`${prefix}.username` as FieldPath<TFieldValues>}
                        control={control}
                    />
                    <FormField
                        label="Password"
                        fieldName={`${prefix}.password` as FieldPath<TFieldValues>}
                        control={control}
                        type="password"
                        placeholder="●●●●●●●"
                    />
                </div>
            )}

            {authType === "BEARER" && (
                <FormField
                    label="Token"
                    fieldName={`${prefix}.password` as FieldPath<TFieldValues>}
                    control={control}
                    type="password"
                    placeholder="●●●●●●●"
                />
            )}
        </div>
    );
}

export function MavenConfigForm<TFieldValues extends FieldValues>({ control, prefix }: MavenConfigFormProps<TFieldValues>) {
    return (
        <>
            <div id="behavior" className="space-y-6 pt-4">
                <h2 className="text-xl font-bold tracking-tight">Behavioral Policies</h2>
                <Card>
                    <CardContent className="divide-y p-0">
                        <div className="flex items-center justify-between p-6">
                            <div className="space-y-0.5">
                                <label className="text-base font-semibold">Allow Redeploy</label>
                                <p className="text-sm text-muted-foreground">
                                    Permit overwriting of existing artifacts in this repository. Dangerous for production.
                                </p>
                            </div>
                            <div className="flex-none">
                                <FormField
                                    label={""}
                                    fieldName={`${prefix}.allowRedeployments` as FieldPath<TFieldValues>}
                                    Input={({ value, onChange }) => (
                                        <Switch
                                            checked={value}
                                            onCheckedChange={onChange}
                                        />
                                    )}
                                    control={control}
                                />
                            </div>
                        </div>
                    </CardContent>
                </Card>
            </div>

            <MirrorConfigForm control={control} prefix={`${prefix}.mirrorConfig`} />

            <S3ConfigForm control={control} prefix={`${prefix}.s3Config`} />
        </>
    );
}
