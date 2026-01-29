import { z } from "zod";
import { FormField } from "@/components/form-field.tsx";
import { FieldGroup } from "@/components/ui/field.tsx";
import { S3ConfigForm } from "@/components/repository/S3ConfigForm.tsx";
import { Switch } from "@/components/ui/switch.tsx";
import type { Control, FieldPath, FieldValues } from "react-hook-form";

export const s3Schema = z.object({
    url: z.string().url().or(z.string().length(0)).or(z.string().startsWith("http://")),
    region: z.string().min(1),
    bucket: z.string().min(1),
    accessKey: z.string().min(1),
    secretKey: z.string().min(1),
});

export const mavenSchema = z.object({
    allowRedeployments: z.boolean(),
    s3Config: s3Schema
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
    }
};

interface MavenConfigFormProps<TFieldValues extends FieldValues> {
    control: Control<TFieldValues>;
    prefix: string;
}

export function MavenConfigForm<TFieldValues extends FieldValues>({ control, prefix }: MavenConfigFormProps<TFieldValues>) {
    return (
        <div className="space-y-6 pt-4 border-t">
            <h2 className="text-lg font-semibold">Maven Configuration</h2>
            <FieldGroup>
                <FormField
                    fieldName={`${prefix}.allowRedeployments` as FieldPath<TFieldValues>}
                    label="Allow Redeployments"
                    Input={({ value, onChange, id }) => (
                        <div className="flex items-center space-x-2 h-9">
                            <Switch
                                id={id}
                                checked={value}
                                onCheckedChange={onChange}
                            />
                            <label htmlFor={"mavenConfig.allowRedeployments"} className="text-sm text-muted-foreground">
                                Allow overwriting existing artifacts
                            </label>
                        </div>
                    )}
                    control={control}
                />

                <S3ConfigForm control={control} prefix={`${prefix}.s3Config`} />
            </FieldGroup>
        </div>
    );
}
