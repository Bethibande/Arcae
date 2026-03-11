import {useNavigate, useParams} from "react-router";
import {useEffect, useRef, useState} from "react";
import {ChronoUnit, PackageManager, type RepositoryDTO} from "@/generated";
import {repositoryApi, repositoryPermissionApi} from "@/lib/api.ts";
import {showError} from "@/lib/errors.ts";
import {FormProvider, useForm} from "react-hook-form";
import {zodResolver} from "@hookform/resolvers/zod";
import {type RepositorySchema, repositorySchema} from "@/pages/repository/settings/schema.tsx";
import {toast} from "sonner";
import {type ExtraForm, ExtraForms, type SidebarItem} from "@/pages/repository/settings/extra-form.tsx";
import {ChevronRight, Lock, Save, Settings, Trash2} from "lucide-react";
import {Button} from "@/components/ui/button.tsx";
import {Field, FieldDescription, FieldLabel} from "@/components/ui/field.tsx";
import {Input} from "@/components/ui/input.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {cn} from "@/lib/utils.ts";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {Switch} from "@/components/ui/switch.tsx";
import {Separator} from "@/components/ui/separator.tsx";
import {PermissionsForm} from "@/pages/repository/settings/permissions-form.tsx";

const defaultValues = {
    name: "",
    packageManager: PackageManager.Maven,
    cleanupPolicies: {
        maxAgePolicy: {
            enabled: false,
            time: 8,
            unit: ChronoUnit.Hours
        },
        maxVersionCountPolicy: {
            enabled: false,
            maxVersions: 1
        }
    },
    permissions: []
}


export default function RepositorySettingsPage() {
    const mainForm = useForm<RepositorySchema>({
        resolver: zodResolver(repositorySchema),
        defaultValues
    })

    const {id} = useParams()

    const navigate = useNavigate()
    const [activeSection, setActiveSection] = useState("general");

    const [repository, setRepository] = useState<RepositoryDTO | undefined>(undefined)

    useEffect(() => {
        if (id && id !== "new") {
            const repoId = parseInt(id);
            Promise.all([
                repositoryApi.apiV1RepositoryIdGet({ id: repoId }),
                repositoryPermissionApi.apiV1RepositoryIdPermissionsGet({ id: repoId })
            ]).then(([repo, perms]) => {
                setRepository(repo);
                mainForm.reset({
                    ...repo,
                    permissions: perms
                });
            }).catch(showError);
        }
    }, [id])

    const packageManager = mainForm.watch("packageManager")
    const extraForm: ExtraForm = ExtraForms[packageManager]
    const saveRef = useRef<() => string | null>(null)

    const sidebarItems: SidebarItem[] = [
        { id: "general", label: "General", icon: Settings },
        ...(extraForm?.sidebarItems ?? []),
        { id: "cleanup", label: "Cleanup Policies", icon: Trash2 },
        { id: "permissions", label: "Permissions", icon: Lock },
    ];


    const onSave = async () => {
        const isValid = await mainForm.trigger();
        const extraData = saveRef.current?.()

        if (!isValid || extraData === null) {
            return; // Validation failed
        }

        const mainData = mainForm.getValues();

        const payload = {
            ...mainData,
            settings: extraData
        } as RepositoryDTO;

        try {
            let repoId: number;
            if (id && id !== "new") {
                repoId = parseInt(id);
                await repositoryApi.apiV1RepositoryPut({
                    repositoryDTO: payload
                });
            } else {
                const created = await repositoryApi.apiV1RepositoryPost({
                    repositoryDTOWithoutId: payload
                });
                repoId = created.id!;
            }

            // Sync permissions
            const currentPerms = await repositoryPermissionApi.apiV1RepositoryIdPermissionsGet({ id: repoId });
            const desiredPerms = mainData.permissions || [];

            // Delete removed or changed perms
            for (const current of currentPerms) {
                const stillExists = desiredPerms.some(p => p.id === current.id);
                if (!stillExists) {
                    await repositoryPermissionApi.apiV1RepositoryPermissionIdDelete({ id: current.id! });
                }
            }

            // Create or update perms
            for (const desired of desiredPerms) {
                if (!desired.id) {
                    await repositoryPermissionApi.apiV1RepositoryPermissionPost({
                        permissionScopeDTOWithoutId: {
                            ...desired,
                            repositoryId: repoId
                        } as any
                    });
                } else {
                    const current = currentPerms.find(p => p.id === desired.id);
                    if (current && (current.type !== desired.type || current.level !== desired.level || current.userName !== desired.userName)) {
                        await repositoryPermissionApi.apiV1RepositoryPermissionPut({
                            permissionScopeDTO: {
                                ...desired,
                                repositoryId: repoId
                            } as any
                        });
                    }
                }
            }

            if (id === "new") {
                toast.success("Repository created successfully");
                navigate(`/`);
            } else {
                navigate(`/`)
                toast.success("Repository updated successfully");
            }
        } catch (e) {
            showError(e);
        }
    }

    const scrollToSection = (sectionId: string) => {
        setActiveSection(sectionId);
        const element = document.getElementById(sectionId);
        if (element) {
            element.scrollIntoView({ behavior: "smooth", block: "start" });
        }
    };

    return (
        <div className="text-foreground h-full flex flex-col overflow-hidden">
            {/* Header */}
            <header className="flex items-center justify-between px-8 py-4 border-b bg-background z-20 shrink-0">
                <div className="flex items-center gap-2 text-sm">
                    <span className="text-muted-foreground cursor-pointer hover:text-foreground" onClick={() => navigate("/")}>Repositories</span>
                    <ChevronRight className="size-4 text-muted-foreground"/>
                    <span className="font-medium">{id === "new" ? "New Repository" : "Edit Repository"}</span>
                </div>
                <div className="flex items-center gap-4">
                    <Button variant="ghost" onClick={() => navigate("/")}>
                        Cancel
                    </Button>
                    <Button onClick={onSave} className="gap-2 px-6">
                        <Save className="size-4"/>
                        Save Changes
                    </Button>
                </div>
            </header>

            <FormProvider {...mainForm}>
                <div className="flex flex-1 overflow-hidden">
                    {/* Sidebar */}
                    <aside className="w-80 max-h-full border-r p-6 space-y-8 overflow-y-auto">
                        <div>
                            <h2 className="text-xs uppercase tracking-[0.2em] font-bold text-muted-foreground mb-6 px-4">Repository Settings</h2>
                            <nav className="space-y-1">
                                {sidebarItems.map((item) => {
                                    const Icon = item.icon;
                                    return (
                                        <button
                                            key={item.id}
                                            onClick={() => scrollToSection(item.id)}
                                            className={cn(
                                                "w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-all group",
                                                activeSection === item.id
                                                    ? "bg-primary text-primary-foreground shadow"
                                                    : "text-muted-foreground hover:text-foreground hover:bg-accent"
                                            )}
                                        >
                                            <Icon className={cn("size-4", activeSection === item.id ? "text-primary-foreground" : "text-muted-foreground group-hover:text-foreground")} />
                                            {item.label}
                                        </button>
                                    );
                                })}
                            </nav>
                        </div>
                    </aside>

                    {/* Main Content */}
                    <main className="flex-1 flex justify-center overflow-y-auto p-12">
                        <div className="w-full max-w-4xl space-y-16 h-fit">
                            {/* General Section */}
                            <section id="general" className="space-y-6">
                                <h2 className="text-xl font-semibold flex items-center gap-2"><Settings/> General Settings</h2>
                                <Card>
                                    <CardContent className="p-6">
                                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                            <Field>
                                                <FieldLabel>Repository Name</FieldLabel>
                                                <Input {...mainForm.register("name")} placeholder="my-repo" />
                                            </Field>
                                            <Field>
                                                <FieldLabel>Package Manager</FieldLabel>
                                                <Select
                                                    value={mainForm.watch("packageManager")}
                                                    onValueChange={(value) => mainForm.setValue("packageManager", value as PackageManager)}
                                                    disabled={id !== "new"}
                                                >
                                                    <SelectTrigger>
                                                        <SelectValue placeholder="Select a package manager" />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        {Object.values(PackageManager).map((pm) => (
                                                            <SelectItem key={pm} value={pm}>{pm}</SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                                {id !== "new" && (
                                                    <p className="text-xs text-muted-foreground mt-1">Package manager cannot be changed after creation.</p>
                                                )}
                                            </Field>
                                        </div>
                                    </CardContent>
                                </Card>
                            </section>


                            {/* Specific Forms (Behavioral, Storage, etc.) */}
                            {extraForm && (
                                <extraForm.component
                                    repository={repository ?? (defaultValues as RepositoryDTO)}
                                    value={repository?.settings ?? ""}
                                    save={saveRef}
                                />
                            )}

                            {/* Cleanup Policies */}
                            <section id="cleanup" className="space-y-6">
                                <h2 className="text-xl font-semibold flex items-center gap-2"><Trash2/> Cleanup Policies</h2>
                                <Card>
                                    <CardContent className="p-0 space-y-6">
                                        <div className="space-y-6 p-6">
                                            <Field orientation="horizontal" className="justify-between items-center">
                                                <div className="space-y-0.5">
                                                    <FieldLabel className="text-base font-semibold">Max Artifact Age</FieldLabel>
                                                    <FieldDescription>
                                                        Automatically delete artifact versions older than a certain age.
                                                    </FieldDescription>
                                                </div>
                                                <Switch
                                                    checked={mainForm.watch("cleanupPolicies.maxAgePolicy.enabled")}
                                                    onCheckedChange={(checked) => mainForm.setValue("cleanupPolicies.maxAgePolicy.enabled", checked)}
                                                />
                                            </Field>

                                            {mainForm.watch("cleanupPolicies.maxAgePolicy.enabled") && (
                                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-2">
                                                    <Field>
                                                        <FieldLabel>Time</FieldLabel>
                                                        <Input
                                                            type="number"
                                                            {...mainForm.register("cleanupPolicies.maxAgePolicy.time", { valueAsNumber: true })}
                                                            placeholder="30"
                                                        />
                                                    </Field>
                                                    <Field>
                                                        <FieldLabel>Unit</FieldLabel>
                                                        <Select
                                                            value={mainForm.watch("cleanupPolicies.maxAgePolicy.unit")}
                                                            onValueChange={(value) => mainForm.setValue("cleanupPolicies.maxAgePolicy.unit", value as ChronoUnit)}
                                                        >
                                                            <SelectTrigger>
                                                                <SelectValue />
                                                            </SelectTrigger>
                                                            <SelectContent>
                                                                <SelectItem value={ChronoUnit.Hours}>Hours</SelectItem>
                                                                <SelectItem value={ChronoUnit.Days}>Days</SelectItem>
                                                                <SelectItem value={ChronoUnit.Weeks}>Weeks</SelectItem>
                                                                <SelectItem value={ChronoUnit.Months}>Months</SelectItem>
                                                                <SelectItem value={ChronoUnit.Years}>Years</SelectItem>
                                                            </SelectContent>
                                                        </Select>
                                                    </Field>
                                                </div>
                                            )}
                                        </div>

                                        <Separator />

                                        <div className="space-y-6 p-6 pt-4">
                                            <Field orientation="horizontal" className="justify-between items-center">
                                                <div className="space-y-0.5">
                                                    <FieldLabel className="text-base font-semibold">Max Version Count</FieldLabel>
                                                    <FieldDescription>
                                                        Keep only a specific number of versions for each artifact.
                                                    </FieldDescription>
                                                </div>
                                                <Switch
                                                    checked={mainForm.watch("cleanupPolicies.maxVersionCountPolicy.enabled")}
                                                    onCheckedChange={(checked) => mainForm.setValue("cleanupPolicies.maxVersionCountPolicy.enabled", checked)}
                                                />
                                            </Field>

                                            {mainForm.watch("cleanupPolicies.maxVersionCountPolicy.enabled") && (
                                                <div className="pt-2">
                                                    <Field>
                                                        <FieldLabel>Max Versions</FieldLabel>
                                                        <Input
                                                            type="number"
                                                            {...mainForm.register("cleanupPolicies.maxVersionCountPolicy.maxVersions", { valueAsNumber: true })}
                                                            placeholder="10"
                                                        />
                                                    </Field>
                                                </div>
                                            )}
                                        </div>
                                    </CardContent>
                                </Card>
                            </section>

                            <PermissionsForm />
                        </div>
                    </main>
                </div>
            </FormProvider>
        </div>
    )
}
