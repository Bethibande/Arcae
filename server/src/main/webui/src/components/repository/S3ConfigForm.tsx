import { FormField } from "@/components/form-field.tsx";
import { Input } from "@/components/ui/input.tsx";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card.tsx";
import { Database } from "lucide-react";
import {type Control, type FieldPath, type FieldValues, useFormContext} from "react-hook-form";

interface S3ConfigFormProps<TFieldValues extends FieldValues> {
    control: Control<TFieldValues>;
    prefix: string;
}

export function S3ConfigForm<TFieldValues extends FieldValues>({ control, prefix }: S3ConfigFormProps<TFieldValues>) {
    const { formState } = useFormContext();
    return (
        <div id="storage" className="space-y-6 pt-4">
            <h2 className="text-xl font-bold tracking-tight">Storage (S3)</h2>
            <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                    <CardTitle className="text-base font-semibold flex items-center gap-2">
                        <Database className="size-4 text-blue-500" />
                        S3 Storage Configuration
                    </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4 pt-4">
                    <FormField
                        fieldName={`${prefix}.url` as FieldPath<TFieldValues>}
                        label="S3 Endpoint URL"
                        Input={props => <Input {...props} placeholder="https://s3.amazonaws.com" />}
                        control={control}
                    />
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <FormField
                            fieldName={`${prefix}.region` as FieldPath<TFieldValues>}
                            label="Bucket Region"
                            Input={props => <Input {...props} placeholder="us-east-1 (N. Virginia)" />}
                            control={control}
                        />
                        <FormField
                            fieldName={`${prefix}.bucket` as FieldPath<TFieldValues>}
                            label="Bucket Name"
                            Input={props => <Input {...props} placeholder="corp-artifacts-prod-01" />}
                            control={control}
                        />
                        <FormField
                            fieldName={`${prefix}.accessKey` as FieldPath<TFieldValues>}
                            label="Access Key ID"
                            Input={props => <Input {...props} placeholder="AKIA..." />}
                            control={control}
                        />
                        <FormField
                            fieldName={`${prefix}.secretKey` as FieldPath<TFieldValues>}
                            label="Secret Access Key"
                            Input={props => <Input {...props} type="password" placeholder="●●●●●●●" />}
                            control={control}
                        />
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}
