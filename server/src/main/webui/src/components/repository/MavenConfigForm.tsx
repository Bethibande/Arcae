import { z } from "zod";
import { FormField } from "@/components/form-field.tsx";
import { S3ConfigForm } from "@/components/repository/S3ConfigForm.tsx";
import { Switch } from "@/components/ui/switch.tsx";
import { Card, CardContent } from "@/components/ui/card.tsx";
import {type Control, type FieldPath, type FieldValues, useFormContext} from "react-hook-form";

export const s3Schema = z.object({
    url: z.string().url().or(z.string().startsWith("http://")),
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
    const { formState } = useFormContext();
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

            <S3ConfigForm control={control} prefix={`${prefix}.s3Config`} />
        </>
    );
}
