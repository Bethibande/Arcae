import {useNavigate, useParams} from "react-router";
import {useEffect, useRef, useState} from "react";
import {PackageManager, RepositoryEndpointApi} from "@/generated";
import {showError} from "@/lib/errors.ts";
import {ChevronRight, Cloud, Lock, RefreshCw, Save, Settings, Trash2} from "lucide-react";
import {Button} from "@/components/ui/button.tsx";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {Input} from "@/components/ui/input.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {useForm} from "react-hook-form";
import {zodResolver} from "@hookform/resolvers/zod";
import {FormField} from "@/components/form-field.tsx";
import {handleSubmit} from "@/lib/forms.ts";
import {CONFIG_MAPPING, dynamicFormSchema, type DynamicFormValues} from "@/lib/repository-configs.ts";
import {cn} from "@/lib/utils.ts";

export default function RepositoryEditView() {
    const {id} = useParams();
    const navigate = useNavigate();
    const isEdit = id !== undefined;

    const [loading, setLoading] = useState(isEdit);
    const [activeSection, setActiveSection] = useState("general");
    const isScrolling = useRef(false);

    const form = useForm<DynamicFormValues>({
        resolver: zodResolver(dynamicFormSchema),
        defaultValues: {
            name: "",
            packageManager: PackageManager.Maven3,
            mavenConfig: CONFIG_MAPPING[PackageManager.Maven3].defaultValues
        }
    });

    const selectedPackageManager = form.watch("packageManager");
    const pmConfig = CONFIG_MAPPING[selectedPackageManager];

    useEffect(() => {
        if (isEdit) {
            new RepositoryEndpointApi()
                .apiV1RepositoryGet()
                .then(repos => {
                    const repo = repos.find(r => r.id === parseInt(id!));
                    if (repo) {
                        form.setValue("name", repo.name);
                        form.setValue("packageManager", repo.packageManager);
                        if (repo.settings) {
                            try {
                                const settings = JSON.parse(repo.settings);
                                const currentPmConfig = CONFIG_MAPPING[repo.packageManager];
                                if (currentPmConfig) {
                                    form.setValue(currentPmConfig.configKey as any, settings);
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
        const api = new RepositoryEndpointApi();

        const currentPmConfig = CONFIG_MAPPING[data.packageManager];
        const config = currentPmConfig ? (data as any)[currentPmConfig.configKey] : undefined;

        const settings = config ? JSON.stringify(config) : undefined;

        try {
            if (isEdit) {
                await api.apiV1RepositoryPut({
                    repositoryDTO: {
                        id: parseInt(id!),
                        name: data.name,
                        packageManager: data.packageManager,
                        settings
                    }
                });
            } else {
                await api.apiV1RepositoryPost({
                    repositoryDTOWithoutId: {
                        name: data.name,
                        packageManager: data.packageManager,
                        settings
                    }
                });
            }
            navigate("/");
        } catch (err) {
            showError(err);
        }
    };

    const sections = [
        {id: "general", label: "General", icon: Settings},
        {id: "storage", label: "Storage (S3)", icon: Cloud},
        {id: "replication", label: "Replication/Mirroring", icon: RefreshCw},
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
        <div className="flex flex-col h-full w-full">
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
                    <Button onClick={form.handleSubmit(onSubmit)}>
                        <Save className="size-4 mr-2"/>
                        {isEdit ? "Save Changes" : "Create Repository"}
                    </Button>
                </div>
            </div>

            <div className="flex flex-1 overflow-hidden">
                {/* Sidebar */}
                <div className="w-64 border-r bg-muted/10 p-4 space-y-4">
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
                    <form onSubmit={handleSubmit(form, onSubmit)} className="max-w-4xl mx-auto space-y-12 pb-24">
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
                                            <Select value={value} onValueChange={onChange}>
                                                <SelectTrigger>
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
                                        )}
                                        control={form.control}
                                    />
                                </CardContent>
                            </Card>
                        </div>

                        {pmConfig && (
                            <pmConfig.FormComponent control={form.control} prefix={pmConfig.configKey}/>
                        )}

                        <div id="replication" className="space-y-6 pt-4">
                            <h2 className="text-xl font-bold tracking-tight">Replication/Mirroring</h2>
                            <Card className="opacity-50">
                                <CardContent className="p-6">
                                    <p className="text-sm text-muted-foreground">Replication and mirroring settings are
                                        not yet available.</p>
                                </CardContent>
                            </Card>
                        </div>

                        <div id="cleanup" className="space-y-6 pt-4">
                            <h2 className="text-xl font-bold tracking-tight">Cleanup Policies</h2>
                            <Card className="opacity-50">
                                <CardContent className="p-6">
                                    <p className="text-sm text-muted-foreground">Cleanup policies are not yet
                                        available.</p>
                                </CardContent>
                            </Card>
                        </div>

                        <div id="permissions" className="space-y-6 pt-4">
                            <h2 className="text-xl font-bold tracking-tight">Permissions</h2>
                            <Card className="opacity-50">
                                <CardContent className="p-6">
                                    <p className="text-sm text-muted-foreground">Permissions management is not yet
                                        available.</p>
                                </CardContent>
                            </Card>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
}
