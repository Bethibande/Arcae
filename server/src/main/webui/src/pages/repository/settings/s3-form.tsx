import {type UseFormReturn} from "react-hook-form";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {Database} from "lucide-react";
import {FormField} from "@/components/form-field.tsx";

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
                    <FormField
                        control={form.control}
                        fieldName={getPath("url")}
                        label="S3 Endpoint URL"
                        placeholder="https://s3.amazonaws.com"
                    />
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <FormField
                            control={form.control}
                            fieldName={getPath("region")}
                            label="Bucket Region"
                            placeholder="us-east-1"
                        />
                        <FormField
                            control={form.control}
                            fieldName={getPath("bucket")}
                            label="Bucket Name"
                            autoComplete={"off"}
                            placeholder="my-bucket"
                        />
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <FormField
                            control={form.control}
                            fieldName={getPath("accessKey")}
                            autoComplete={"off"}
                            label="Access Key ID"
                        />
                        <FormField
                            control={form.control}
                            fieldName={getPath("secretKey")}
                            autoComplete={"off"}
                            label="Secret Access Key"
                            type="password"
                        />
                    </div>
                </CardContent>
            </Card>
        </section>
    );
}
