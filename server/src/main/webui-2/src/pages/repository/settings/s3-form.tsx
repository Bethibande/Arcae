import {type UseFormReturn} from "react-hook-form";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {Field, FieldLabel} from "@/components/ui/field.tsx";
import {Input} from "@/components/ui/input.tsx";
import {Database} from "lucide-react";

interface S3FormProps {
    form: UseFormReturn<any>;
    path?: string;
}

export function S3Form({form, path = "s3Config"}: S3FormProps) {
    const getPath = (field: string) => path ? `${path}.${field}` : field;

    return (
        <section id="storage" className="space-y-6">
            <h2 className="text-xl font-semibold flex items-center gap-2"><Database/> Storage (S3)</h2>
            <Card>
                <CardContent className="p-6 space-y-6">
                    <Field>
                        <FieldLabel>S3 Endpoint URL</FieldLabel>
                        <Input {...form.register(getPath("url"))} placeholder="https://s3.amazonaws.com"/>
                    </Field>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <Field>
                            <FieldLabel>Bucket Region</FieldLabel>
                            <Input {...form.register(getPath("region"))} placeholder="us-east-1"/>
                        </Field>
                        <Field>
                            <FieldLabel>Bucket Name</FieldLabel>
                            <Input {...form.register(getPath("bucket"))} placeholder="my-bucket"/>
                        </Field>
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <Field>
                            <FieldLabel>Access Key ID</FieldLabel>
                            <Input {...form.register(getPath("accessKey"))} />
                        </Field>
                        <Field>
                            <FieldLabel>Secret Access Key</FieldLabel>
                            <Input {...form.register(getPath("secretKey"))} type="password"/>
                        </Field>
                    </div>
                </CardContent>
            </Card>
        </section>
    );
}
