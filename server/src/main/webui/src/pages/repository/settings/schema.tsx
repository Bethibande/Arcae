import {z} from "zod";
import {ChronoUnit, PackageManager} from "@/generated";

export const cleanupPolicySchema = z.object({
    maxAgePolicy: z.object({
        enabled: z.boolean(),
        time: z.number().min(1),
        unit: z.enum(ChronoUnit),
    }),
    maxVersionCountPolicy: z.object({
        enabled: z.boolean(),
        maxVersions: z.number().min(1),
    })
})

export const permissionSchema = z.object({
    id: z.number().optional(),
    type: z.enum(["ANONYMOUS", "AUTHENTICATED", "USER"]),
    level: z.enum(["READ", "WRITE", "ADMIN"]),
    userName: z.string().optional(),
    userId: z.number().optional(),
})

export const repositorySchema = z.object({
    name: z.string().min(3).max(64),
    packageManager: z.enum(PackageManager),
    cleanupPolicies: cleanupPolicySchema,
    permissions: z.array(permissionSchema).default([]),
    backendId: z.number().min(1, "Please select a storage backend."),
})

export type RepositorySchema = z.input<typeof repositorySchema>;

export const mirrorConnectionSchema = z.discriminatedUnion("internal", [
    z.object({
        internal: z.literal(true),
        repositoryId: z.number(),
        authType: z.enum(["APPLY_USER_AUTH", "APPLY_SYSTEM_AUTH"]),
        username: z.string(),
        password: z.string(),
    }),

    z.object({
        internal: z.literal(false),
        url: z.url(),
        authType: z.enum(["NONE", "BASIC", "BEARER"]),
        username: z.string(),
        password: z.string(),
    })
])

export const mirrorConfigSchema = z.object({
    enabled: z.boolean(),
    storeArtifacts: z.boolean(),
    authorizedUsersOnly: z.boolean(),
    connections: z.array(mirrorConnectionSchema)
})

export const mavenSettingsSchema = z.object({
    allowRedeployments: z.boolean(),
    mirrorConfig: mirrorConfigSchema,
})

export const ociRoutingConfig = z.object({
    enabled: z.boolean(),
    targetService: z.string().nullable(),
    targetPort: z.coerce.number().min(1).max(65535).nullable(),
    gatewayName: z.string().nullable(),
    gatewayNamespace: z.string().nullable(),
})

export const ociSettingsSchema = z.object({
    allowRedeployments: z.boolean(),
    mirrorConfig: mirrorConfigSchema,
    routingConfig: ociRoutingConfig,
    externalHostname: z.string().nonempty(),
})

export type MavenSettingsSchema = z.infer<typeof mavenSettingsSchema>;
export type OciSettingsSchema = z.infer<typeof ociSettingsSchema>;

export const defaultMirrorConfig = {
    enabled: false,
    storeArtifacts: true,
    authorizedUsersOnly: true,
    connections: [],
}

export const defaultMavenSettings: MavenSettingsSchema = {
    allowRedeployments: false,
    mirrorConfig: defaultMirrorConfig,
}

export const defaultOciSettings: OciSettingsSchema = {
    allowRedeployments: true,
    mirrorConfig: defaultMirrorConfig,
    externalHostname: "",
    routingConfig: {
        enabled: false,
        gatewayName: "",
        gatewayNamespace: "",
        targetPort: 8080,
        targetService: "repository",
    },
}