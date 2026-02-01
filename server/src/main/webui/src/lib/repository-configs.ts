import { PackageManager, PermissionLevel, UserSelectionType } from "@/generated";
import { defaultMavenConfig, MavenConfigForm, mavenSchema } from "@/components/repository/MavenConfigForm.tsx";
import { z } from "zod";
import {type Control, type FieldValues} from "react-hook-form";

export interface PackageManagerConfig<T extends FieldValues = any> {
    schema: z.ZodType<any>;
    defaultValues: any;
    FormComponent: React.ComponentType<{ control: Control<T>; prefix: string }>;
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

export const dynamicFormSchema = z.object({
    name: z.string().min(3).max(64),
    packageManager: z.nativeEnum(PackageManager),
    mavenConfig: mavenSchema,
    permissions: z.array(permissionSchema),
});

export type DynamicFormValues = z.infer<typeof dynamicFormSchema>;
export type PermissionValues = z.infer<typeof permissionSchema>;
