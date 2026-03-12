import type {FunctionComponent, Ref} from "react";
import {Database, Globe, type LucideIcon, RefreshCcw, ShieldAlert} from "lucide-react";
import {PackageManager, type RepositoryDTO} from "@/generated";
import {MavenSettingsForm} from "@/pages/repository/settings/maven-form.tsx";
import {OciSettingsForm} from "@/pages/repository/settings/oci-form.tsx";

export interface ExtraFormProps {
    repository: RepositoryDTO,
    value: string,
    save: Ref<() => Promise<string | null>>
}

export interface SidebarItem {
    id: string;
    label: string;
    icon: LucideIcon;
}

export interface ExtraForm {
    component: FunctionComponent<ExtraFormProps>;
    sidebarItems?: SidebarItem[];
}

export const ExtraForms: Record<PackageManager, ExtraForm> = {
    [PackageManager.Maven]: {
        component: MavenSettingsForm,
        sidebarItems: [
            { id: "behavioral", label: "Behavioral Policies", icon: ShieldAlert },
            { id: "storage", label: "Storage (S3)", icon: Database },
            { id: "replication", label: "Mirroring", icon: RefreshCcw },
        ]
    },
    [PackageManager.Oci]: {
        component: OciSettingsForm,
        sidebarItems: [
            { id: "behavioral", label: "Behavioral Policies", icon: ShieldAlert },
            { id: "storage", label: "Storage (S3)", icon: Database },
            { id: "replication", label: "Mirroring", icon: RefreshCcw },
            { id: "external", label: "External Access", icon: Globe },
        ]
    }
}
