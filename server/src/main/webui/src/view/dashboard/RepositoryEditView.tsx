import {useNavigate, useParams} from "react-router";
import {useEffect, useState} from "react";
import {PackageManager, RepositoryEndpointApi} from "@/generated";
import {showError} from "@/lib/errors.ts";
import {ChevronRight, Save} from "lucide-react";
import {Button} from "@/components/ui/button.tsx";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card.tsx";
import {Input} from "@/components/ui/input.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {useForm} from "react-hook-form";
import {zodResolver} from "@hookform/resolvers/zod";
import {FormField} from "@/components/form-field.tsx";
import {handleSubmit} from "@/lib/forms.ts";
import {FieldGroup} from "@/components/ui/field.tsx";
import {CONFIG_MAPPING, dynamicFormSchema, type DynamicFormValues} from "@/lib/repository-configs.ts";

export default function RepositoryEditView() {
    const {id} = useParams();
    const navigate = useNavigate();
    const isEdit = id !== undefined;

    const [loading, setLoading] = useState(isEdit);

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

    if (loading) {
        return <div className="p-8 text-center">Loading...</div>;
    }

    return (
        <div className="flex flex-col gap-8 p-8 max-w-3xl mx-auto w-full">
            {/* Breadcrumbs & Title */}
            <div className="space-y-4">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <span className="cursor-pointer hover:text-foreground" onClick={() => navigate("/")}>Repositories</span>
                    <ChevronRight className="size-4"/>
                    <span className="text-foreground">{isEdit ? "Edit" : "New"} Repository</span>
                </div>
                <div className="flex justify-between items-end">
                    <div>
                        <h1 className="text-3xl font-bold tracking-tight">{isEdit ? "Edit" : "Create"} Repository</h1>
                        <p className="text-muted-foreground mt-1">
                            {isEdit ? "Update repository configuration." : "Configure a new repository instance."}
                        </p>
                    </div>
                </div>
            </div>

            <form onSubmit={handleSubmit(form, onSubmit)}>
                <Card>
                    <CardHeader>
                        <CardTitle>General Information</CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-6">
                        <FieldGroup>
                            <FormField
                                fieldName="name"
                                label="Name"
                                Input={props => <Input {...props} placeholder="my-repository"/>}
                                control={form.control}
                            />

                            <FormField
                                fieldName="packageManager"
                                label="Package Manager"
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
                        </FieldGroup>

                        {pmConfig && (
                            <pmConfig.FormComponent control={form.control} prefix={pmConfig.configKey} />
                        )}

                        <div className="flex justify-end gap-3 pt-4">
                            <Button type="button" variant="outline" onClick={() => navigate("/")}>
                                Cancel
                            </Button>
                            <Button type="submit">
                                <Save className="size-4 mr-2"/>
                                {isEdit ? "Save Changes" : "Create Repository"}
                            </Button>
                        </div>
                    </CardContent>
                </Card>
            </form>
        </div>
    );
}
