import { FormField } from "@/components/form-field.tsx";
import { Input } from "@/components/ui/input.tsx";
import { FieldGroup } from "@/components/ui/field.tsx";
import type {Control, FieldPath, FieldValues} from "react-hook-form";

interface S3ConfigFormProps<TFieldValues extends FieldValues> {
    control: Control<TFieldValues>;
    prefix: string;
}

export function S3ConfigForm<TFieldValues extends FieldValues>({ control, prefix }: S3ConfigFormProps<TFieldValues>) {
    return (
        <FieldGroup className="border p-4 rounded-lg bg-muted/30">
            <h3 className="text-sm font-semibold mb-2">S3 Configuration</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <FormField
                    fieldName={`${prefix}.url` as FieldPath<TFieldValues>}
                    label="S3 URL"
                    Input={props => <Input {...props} placeholder="https://s3.amazonaws.com" />}
                    control={control}
                />
                <FormField
                    fieldName={`${prefix}.region` as FieldPath<TFieldValues>}
                    label="Region"
                    Input={props => <Input {...props} placeholder="us-east-1" />}
                    control={control}
                />
                <FormField
                    fieldName={`${prefix}.bucket` as FieldPath<TFieldValues>}
                    label="Bucket"
                    Input={props => <Input {...props} placeholder="my-bucket" />}
                    control={control}
                />
                <FormField
                    fieldName={`${prefix}.accessKey` as FieldPath<TFieldValues>}
                    label="Access Key"
                    Input={props => <Input {...props} placeholder="AKIA..." />}
                    control={control}
                />
                <FormField
                    fieldName={`${prefix}.secretKey` as FieldPath<TFieldValues>}
                    label="Secret Key"
                    Input={props => <Input {...props} type="password" placeholder="●●●●●●●" />}
                    control={control}
                />
            </div>
        </FieldGroup>
    );
}
