import { ChronoUnit, PackageManager, PermissionLevel, UserSelectionType } from "@/generated";
import { defaultMavenConfig, MavenConfigForm, mavenSchema } from "@/components/repository/MavenConfigForm.tsx";
import { z } from "zod";
import {type Control, type FieldValues} from "react-hook-form";

export interface PackageManagerConfig<T extends FieldValues = any> {
    schema: z.ZodType<any>;
    defaultValues: any;
    FormComponent: React.ComponentType<{ control: Control<T, any, any>; prefix: string }>;
    configKey: string;
}

export const CONFIG_MAPPING: Record<PackageManager, PackageManagerConfig<DynamicFormValues>> = {
    [PackageManager.Maven]: {
        schema: mavenSchema,
        defaultValues: defaultMavenConfig,
        FormComponent: MavenConfigForm,
        configKey: "mavenConfig"
    }
};

export const permissionSchema = z.object({
    id: z.number().optional(),
    level: z.nativeEnum(PermissionLevel),
    type: z.nativeEnum(UserSelectionType),
    userId: z.number().optional(),
    userName: z.string().optional(), // For UI display
}).refine(data => data.type !== UserSelectionType.User || data.userId !== undefined, {
    message: "User must be selected",
    path: ["userId"]
});

export const maxAgeCleanupPolicySchema = z.object({
    enabled: z.boolean(),
    time: z.coerce.number().min(1),
    unit: z.nativeEnum(ChronoUnit),
});

export const maxVersionCountPolicySchema = z.object({
    enabled: z.boolean(),
    maxVersions: z.coerce.number().min(1),
});

export const cleanupPoliciesSchema = z.object({
    maxAgePolicy: maxAgeCleanupPolicySchema,
    maxVersionCountPolicy: maxVersionCountPolicySchema,
});

export const dynamicFormSchema = z.object({
    name: z.string().min(3).max(64),
    packageManager: z.nativeEnum(PackageManager),
    mavenConfig: mavenSchema,
    permissions: z.array(permissionSchema),
    cleanupPolicies: cleanupPoliciesSchema,
});

export type DynamicFormValues = z.infer<typeof dynamicFormSchema>;
export type PermissionValues = z.infer<typeof permissionSchema>;

export const defaultCleanupPolicies: z.infer<typeof cleanupPoliciesSchema> = {
    maxAgePolicy: {
        enabled: false,
        time: 30,
        unit: ChronoUnit.Days,
    },
    maxVersionCountPolicy: {
        enabled: false,
        maxVersions: 10,
    },
};
