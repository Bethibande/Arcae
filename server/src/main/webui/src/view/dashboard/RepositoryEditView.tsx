import {Navigate, useNavigate, useParams} from "react-router";
import {useEffect, useRef, useState} from "react";
import {PackageManager, UserRole} from "@/generated";
import {repositoryApi, repositoryPermissionApi} from "@/lib/api.ts";
import {showError} from "@/lib/errors.ts";
import {ChevronRight, Cloud, Globe, Lock, RefreshCw, Save, Settings, Trash2, Zap} from "lucide-react";
import {Button} from "@/components/ui/button.tsx";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {Input} from "@/components/ui/input.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {FormProvider, useForm} from "react-hook-form";
import {zodResolver} from "@hookform/resolvers/zod";
import {FormField} from "@/components/form-field.tsx";
import {handleSubmit} from "@/lib/forms.ts";
import {
    CONFIG_MAPPING,
    defaultCleanupPolicies,
    dynamicFormSchema,
    type DynamicFormValues,
    type PermissionValues
} from "@/lib/repository-configs.ts";
import {cn} from "@/lib/utils.ts";
import {PermissionsForm} from "@/components/repository/PermissionsForm.tsx";
import {useAuth} from "@/lib/auth.tsx";
import {CleanupPoliciesForm} from "@/components/repository/CleanupPoliciesForm.tsx";
import {Tooltip, TooltipContent, TooltipTrigger} from "@/components/ui/tooltip.tsx";

export default function RepositoryEditView() {
    const {user} = useAuth();
    const {id} = useParams();
    const navigate = useNavigate();

    if (!user?.roles?.includes(UserRole.Admin)) {
        return <Navigate to="/" replace/>;
    }
    const isEdit = id !== undefined;

    const [loading, setLoading] = useState(isEdit);
    const [activeSection, setActiveSection] = useState("general");
    const isScrolling = useRef(false);

    const form = useForm<DynamicFormValues, any, any>({
        resolver: zodResolver(dynamicFormSchema) as any,
        defaultValues: {
            name: "",
            packageManager: PackageManager.Maven,
            mavenConfig: CONFIG_MAPPING[PackageManager.Maven].defaultValues,
            ociConfig: CONFIG_MAPPING[PackageManager.Oci].defaultValues,
            permissions: [],
            cleanupPolicies: defaultCleanupPolicies
        }
    });

    const selectedPackageManager = form.watch("packageManager");
    const pmConfig = CONFIG_MAPPING[selectedPackageManager];

    const [initialPermissions, setInitialPermissions] = useState<PermissionValues[]>([]);
    const [initialMetadata, setInitialMetadata] = useState<Record<string, any>>({});

    useEffect(() => {
        if (isEdit) {
            Promise.all([
                repositoryApi.apiV1RepositoryIdGet({id: parseInt(id!)}),
                repositoryPermissionApi.apiV1RepositoryIdPermissionsGet({id: parseInt(id!)})
            ])
                .then(([repo, permissions]) => {
                    if (repo) {
                        form.setValue("name", repo.name);
                        form.setValue("packageManager", repo.packageManager);
                        if (repo.cleanupPolicies) {
                            form.setValue("cleanupPolicies", {
                                ...defaultCleanupPolicies,
                                ...repo.cleanupPolicies,
                                maxAgePolicy: {
                                    ...defaultCleanupPolicies.maxAgePolicy,
                                    ...(repo.cleanupPolicies.maxAgePolicy || {})
                                },
                                maxVersionCountPolicy: {
                                    ...defaultCleanupPolicies.maxVersionCountPolicy,
                                    ...(repo.cleanupPolicies.maxVersionCountPolicy || {})
                                }
                            });
                        }

                        const mappedPermissions: PermissionValues[] = permissions.map((p) => ({
                            id: p.id,
                            level: p.level,
                            type: p.type,
                            userId: p.userId ?? undefined,
                            userName: p.userName ? `User ${p.userName}` : undefined
                        }));
                        form.setValue("permissions", mappedPermissions);
                        setInitialPermissions([...mappedPermissions]);

                        if (repo.metadata) {
                            setInitialMetadata(repo.metadata as any);
                            if ((repo.metadata as any)["HOST_NAME"]) {
                                form.setValue("externalHost", (repo.metadata as any)["HOST_NAME"]);
                            }
                        } else {
                            setInitialMetadata({});
                        }

                        if (repo.settings) {
                            try {
                                const settings = JSON.parse(repo.settings);
                                const currentPmConfig = CONFIG_MAPPING[repo.packageManager];
                                if (currentPmConfig) {
                                    const mergedSettings = {
                                        ...currentPmConfig.defaultValues,
                                        ...settings,
                                    };

                                    // Ensure nested objects are also merged with defaults for Maven or OCI
                                    if (repo.packageManager === PackageManager.Maven || repo.packageManager === PackageManager.Oci) {
                                        mergedSettings.s3Config = {
                                            ...(currentPmConfig.defaultValues.s3Config || {}),
                                            ...(settings.s3Config || {})
                                        };
                                        if (repo.packageManager === PackageManager.Maven) {
                                            mergedSettings.mirrorConfig = {
                                                ...(currentPmConfig.defaultValues.mirrorConfig || {}),
                                                ...(settings.mirrorConfig || {})
                                            };
                                        }
                                        if (repo.packageManager === PackageManager.Oci) {
                                            mergedSettings.routingConfig = {
                                                ...(currentPmConfig.defaultValues.routingConfig || {}),
                                                ...(settings.routingConfig || {})
                                            };
                                        }
                                    }

                                    form.setValue(currentPmConfig.configKey as keyof DynamicFormValues, mergedSettings);
                                }
                            } catch (e) {
                                console.error("Failed to parse settings", e);
                            }
                        }
                    }
                    setLoading(false);
                })
                .catch(err => {
                    showError(err);
                    setLoading(false);
                });
        }
    }, [id, isEdit, form]);

    const onSubmit = async (data: DynamicFormValues) => {
        const currentPmConfig = CONFIG_MAPPING[data.packageManager];
        const config = currentPmConfig ? data[currentPmConfig.configKey as keyof DynamicFormValues] : undefined;

        const settings = config ? JSON.stringify(config) : undefined;

        // Preserve existing metadata and override only the keys managed by this form
        const mergedMetadata: Record<string, any> = isEdit ? { ...initialMetadata } : {};
        if (data.packageManager === PackageManager.Oci) {
            if (data.externalHost && data.externalHost.trim().length > 0) {
                mergedMetadata["HOST_NAME"] = data.externalHost.trim();
            }
        }
        // Avoid sending undefined which would wipe metadata server-side
        const metadata = mergedMetadata;

        try {
            let repoId: number;
            if (isEdit) {
                repoId = parseInt(id!)
                await repositoryApi.apiV1RepositoryPut({
                    repositoryDTO: {
                        id: repoId,
                        name: data.name,
                        packageManager: data.packageManager,
                        settings,
                        metadata,
                        cleanupPolicies: data.cleanupPolicies
                    }
                });
            } else {
                const repo = await repositoryApi.apiV1RepositoryPost({
                    repositoryDTOWithoutId: {
                        name: data.name,
                        packageManager: data.packageManager,
                        settings,
                        metadata,
                        cleanupPolicies: data.cleanupPolicies
                    }
                });
                repoId = repo.id!;
            }

            // Handle permissions
            const currentPermissions = data.permissions || [];
            const toDelete = initialPermissions.filter(ip => ip.id !== undefined && !currentPermissions.find(cp => cp.id === ip.id));
            const toUpdate = currentPermissions.filter(cp => cp.id !== undefined);
            const toCreate = currentPermissions.filter(cp => cp.id === undefined);

            const promises: Promise<any>[] = [];

            for (const p of toDelete) {
                promises.push(repositoryPermissionApi.apiV1RepositoryPermissionIdDelete({id: p.id!}));
            }

            for (const p of toUpdate) {
                promises.push(repositoryPermissionApi.apiV1RepositoryPermissionPut({
                    permissionScopeDTO: {
                        id: p.id!,
                        repositoryId: repoId,
                        level: p.level,
                        type: p.type,
                        userId: p.userId ?? undefined
                    }
                }));
            }

            for (const p of toCreate) {
                promises.push(repositoryPermissionApi.apiV1RepositoryPermissionPost({
                    permissionScopeDTOWithoutId: {
                        repositoryId: repoId,
                        level: p.level,
                        type: p.type,
                        userId: p.userId ?? undefined
                    }
                }));
            }

            await Promise.all(promises);

            navigate("/");
        } catch (err) {
            showError(err);
        }
    };

    const sections = [
        {id: "general", label: "General", icon: Settings},
        ...(selectedPackageManager === PackageManager.Maven ? [{
            id: "replication",
            label: "Replication/Mirroring",
            icon: RefreshCw
        }] : []),
        ...(selectedPackageManager === PackageManager.Maven || selectedPackageManager === PackageManager.Oci ? [{
            id: "behavior",
            label: "Behavioral Policies",
            icon: Zap
        }] : []),
        {id: "storage", label: "Storage (S3)", icon: Cloud},
        ...(selectedPackageManager === PackageManager.Oci ? [{
            id: "external-access",
            label: "External Access",
            icon: Globe
        }] : []),
        {id: "cleanup", label: "Cleanup Policies", icon: Trash2},
        {id: "permissions", label: "Permissions", icon: Lock},
    ];

    useEffect(() => {
        const observer = new IntersectionObserver(
            (entries) => {
                if (isScrolling.current) return;

                // Find the section that is most visible
                const visibleSection = entries.find((entry) => entry.isIntersecting);
                if (visibleSection) {
                    setActiveSection(visibleSection.target.id);
                }
            },
            {
                root: document.getElementById("repository-edit-content"),
                rootMargin: "-20% 0px -70% 0px", // Adjust to trigger when section is in top part of view
                threshold: 0
            }
        );

        sections.forEach((section) => {
            const element = document.getElementById(section.id);
            if (element) observer.observe(element);
        });

        return () => observer.disconnect();
    }, [loading]);

    if (loading) {
        return <div className="p-8 text-center">Loading...</div>;
    }

    const scrollToSection = (sectionId: string) => {
        setActiveSection(sectionId);
        isScrolling.current = true;
        const element = document.getElementById(sectionId);
        if (element) {
            element.scrollIntoView({behavior: "smooth"});
            // Reset isScrolling after animation roughly finishes
            setTimeout(() => {
                isScrolling.current = false;
            }, 800);
        }
    };

    return (
        <div className="flex flex-col h-full w-full overflow-hidden">
            <div className="flex-none border-b bg-background p-4 flex justify-between items-center">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <span className="cursor-pointer hover:text-foreground"
                          onClick={() => navigate("/")}>Repositories</span>
                    <ChevronRight className="size-4"/>
                    <span className="text-foreground">{isEdit ? "Edit" : "New"} Repository</span>
                </div>
                <div className="flex gap-3">
                    <Button type="button" variant="ghost" onClick={() => navigate("/")}>
                        Cancel
                    </Button>
                    <Button onClick={form.handleSubmit(onSubmit, (errors) => {
                        console.log("Form errors", errors);
                        showError(new Error("Form is invalid. Please check the fields."));
                    })}>
                        <Save className="size-4 mr-2"/>
                        {isEdit ? "Save Changes" : "Create Repository"}
                    </Button>
                </div>
            </div>

            <div className="flex flex-1 min-h-0 overflow-hidden">
                <FormProvider {...form}>
                    {/* Sidebar */}
                    <div className="w-64 border-r bg-muted/10 p-4 space-y-4 shrink-0 overflow-y-auto">
                        <div className="px-2 py-1">
                            <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Repository
                                Settings</h3>
                        </div>
                        <nav className="space-y-1">
                            {sections.map((section) => (
                                <button
                                    key={section.id}
                                    onClick={() => scrollToSection(section.id)}
                                    className={cn(
                                        "w-full flex items-center gap-3 px-3 py-2 text-sm font-medium rounded-md transition-colors",
                                        activeSection === section.id
                                            ? "bg-primary text-primary-foreground shadow-sm"
                                            : "text-muted-foreground hover:text-foreground hover:bg-muted"
                                    )}
                                >
                                    <section.icon className="size-4"/>
                                    {section.label}
                                </button>
                            ))}
                        </nav>
                    </div>

                    {/* Content */}
                    <div id="repository-edit-content" className="flex-1 overflow-y-auto p-8">
                        <form onSubmit={handleSubmit(form, onSubmit, (errors) => {
                            console.log("Form errors", errors);
                            showError(new Error("Form is invalid. Please check the fields."));
                        })} className="max-w-4xl mx-auto space-y-12 pb-24">
                            <div id="general" className="space-y-6">
                                <h2 className="text-xl font-bold tracking-tight">Repository Identity</h2>
                                <Card>
                                    <CardContent className="grid grid-cols-1 md:grid-cols-2 gap-6 p-6">
                                        <FormField
                                            fieldName="name"
                                            label="Repository Name"
                                            Input={props => <Input {...props} placeholder="production-maven-central"/>}
                                            control={form.control}
                                        />

                                        <FormField
                                            fieldName="packageManager"
                                            label="Artifact Type"
                                            Input={({value, onChange}) => (
                                                <Tooltip>
                                                    <TooltipTrigger asChild>
                                                        <div>
                                                            <Select value={value}
                                                                    onValueChange={onChange}
                                                                    disabled={isEdit}>
                                                                <SelectTrigger className={"w-full"}>
                                                                    <SelectValue placeholder="Select package manager"/>
                                                                </SelectTrigger>
                                                                <SelectContent>
                                                                    {Object.values(PackageManager).map((pm) => (
                                                                        <SelectItem key={pm} value={pm}>
                                                                            {pm}
                                                                        </SelectItem>
                                                                    ))}
                                                                </SelectContent>
                                                            </Select>
                                                        </div>
                                                    </TooltipTrigger>
                                                    {isEdit && (
                                                        <TooltipContent>
                                                            The artifact type cannot be changed after the repository has
                                                            been created.
                                                        </TooltipContent>
                                                    )}
                                                </Tooltip>
                                            )}
                                            control={form.control}
                                        />
                                    </CardContent>
                                </Card>
                            </div>

                            {pmConfig && (
                                <pmConfig.FormComponent control={form.control} prefix={pmConfig.configKey}/>
                            )}

                            <CleanupPoliciesForm control={form.control}/>

                            <PermissionsForm control={form.control}/>
                        </form>
                    </div>
                </FormProvider>
            </div>
        </div>
    );
}
