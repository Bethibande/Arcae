import { z } from "zod";
import { type Control, type FieldPath, type FieldValues } from "react-hook-form";
import { Card, CardContent } from "@/components/ui/card.tsx";
import { s3Schema, S3ConfigForm } from "@/components/repository/S3ConfigForm.tsx";
import { FormField } from "@/components/form-field.tsx";

export const ociSchema = z.object({
    s3Config: s3Schema,
});

export type OCIConfig = z.infer<typeof ociSchema>;

export const defaultOCIConfig: OCIConfig = {
    s3Config: {
        url: "",
        region: "",
        bucket: "",
        accessKey: "",
        secretKey: ""
    }
};

interface OCIConfigFormProps<TFieldValues extends FieldValues> {
    control: Control<TFieldValues, any, any>;
    prefix: string;
}

export function OCIConfigForm<TFieldValues extends FieldValues>({ control, prefix }: OCIConfigFormProps<TFieldValues>) {
    return (
        <div className="space-y-6">
            <S3ConfigForm control={control} prefix={`${prefix}.s3Config`} />

            <div id="external-access" className="space-y-6 pt-4">
                <h2 className="text-xl font-bold tracking-tight">External Access</h2>
                <Card>
                    <CardContent className="p-6 space-y-4">
                        <FormField
                            label="External Host"
                            fieldName={`externalHost` as FieldPath<TFieldValues>}
                            control={control}
                            placeholder="oci.test.org:8080"
                        />
                        <p className="text-sm text-muted-foreground">
                            The expected host name for connecting to the OCI repository (e.g., host or host:port).
                        </p>
                    </CardContent>
                </Card>
            </div>
        </div>
    );
}
