import {useForm} from "react-hook-form";
import {
    defaultMavenSettings,
    type MavenSettingsSchema,
    mavenSettingsSchema
} from "@/pages/repository/settings/schema.tsx";
import {zodResolver} from "@hookform/resolvers/zod";
import {useEffect, useImperativeHandle} from "react";
import {type ExtraFormProps} from "@/pages/repository/settings/extra-form.tsx";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {Field, FieldDescription, FieldLabel} from "@/components/ui/field.tsx";
import {Switch} from "@/components/ui/switch.tsx";
import {MirrorForm} from "@/pages/repository/settings/mirror-form.tsx";
import {ShieldAlert} from "lucide-react";

import {PackageManager} from "@/generated";

export function MavenSettingsForm({ repository, value, save }: ExtraFormProps) {
    const form = useForm<MavenSettingsSchema>({
        resolver: zodResolver(mavenSettingsSchema),
        defaultValues: value ? JSON.parse(value) as MavenSettingsSchema : defaultMavenSettings
    });

    useEffect(() => {
        if (value) {
            form.reset(JSON.parse(value) as MavenSettingsSchema);
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
                                    Permit overwriting existing tags by pushing again (enable if you want to allow re-deployments).
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

            <MirrorForm form={form} packageManager={PackageManager.Maven} repositoryId={repository.id} placeholder="https://repo1.maven.org/maven2" />
        </div>
    );
}
