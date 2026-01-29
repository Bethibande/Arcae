import { PackageManager } from "@/generated";
import { defaultMavenConfig, MavenConfigForm, mavenSchema } from "@/components/repository/MavenConfigForm.tsx";
import { z } from "zod";

export interface PackageManagerConfig {
    schema: z.ZodType<any>;
    defaultValues: any;
    FormComponent: React.ComponentType<{ control: any; prefix: string }>;
    configKey: string;
}

export const CONFIG_MAPPING: Record<PackageManager, PackageManagerConfig> = {
    [PackageManager.Maven3]: {
        schema: mavenSchema,
        defaultValues: defaultMavenConfig,
        FormComponent: MavenConfigForm,
        configKey: "mavenConfig"
    }
};

export const dynamicFormSchema = z.object({
    name: z.string().min(3).max(64),
    packageManager: z.nativeEnum(PackageManager),
    mavenConfig: mavenSchema.optional(),
});

export type DynamicFormValues = z.infer<typeof dynamicFormSchema>;
