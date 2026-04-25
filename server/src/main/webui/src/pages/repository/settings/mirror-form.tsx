import {Controller, useFieldArray, type UseFormReturn} from "react-hook-form";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {Field, FieldDescription, FieldLabel} from "@/components/ui/field.tsx";
import {Switch} from "@/components/ui/switch.tsx";
import {Button} from "@/components/ui/button.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {Tabs, TabsContent, TabsList, TabsTrigger} from "@/components/ui/tabs.tsx";
import {Plus, RefreshCcw, Trash2} from "lucide-react";
import {Separator} from "@/components/ui/separator.tsx";
import {FormField} from "@/components/form-field.tsx";
import {useEffect, useState} from "react";
import {type PackageManager, type RepositoryOverviewDTO} from "@/generated";
import {repositoryApi} from "@/lib/api.ts";
import {showError} from "@/lib/errors.ts";

interface MirrorFormProps {
    form: UseFormReturn<any>;
    packageManager?: PackageManager;
    repositoryId?: number;
    path?: string;
    placeholder?: string;
}

export function MirrorForm({form, packageManager, repositoryId, path = "mirrorConfig", placeholder}: MirrorFormProps) {
    const [repositories, setRepositories] = useState<RepositoryOverviewDTO[]>([]);

    useEffect(() => {
        repositoryApi.apiV1RepositoryOverviewGet()
            .then(setRepositories)
            .catch(showError);
    }, []);

    const usableRepositories = repositories
        .filter((repo) => !packageManager || repo.repository.packageManager === packageManager)
        .filter((repo) => !repositoryId || repo.repository.id !== repositoryId);

    const getPath = (field: string) => path ? `${path}.${field}` : field;

    const {fields, append, remove} = useFieldArray({
        control: form.control,
        name: getPath("connections")
    });

    const isEnabled = form.watch(getPath("enabled"));

    return (
        <section id="replication" className="space-y-6">
            <h2 className="text-xl font-semibold flex items-center gap-2"><RefreshCcw/> Mirroring</h2>
            <Card>
                <CardContent className="p-6 space-y-6">
                    <Field orientation="horizontal" className="justify-between">
                        <div className="space-y-0.5">
                            <FieldLabel>Enable Mirroring</FieldLabel>
                            <FieldDescription>
                                Mirror artifacts from a remote repository when they are not found locally.
                            </FieldDescription>
                        </div>
                        <Switch
                            checked={form.watch(getPath("enabled"))}
                            onCheckedChange={(checked) => form.setValue(getPath("enabled"), checked)}
                        />
                    </Field>

                    {isEnabled && (
                        <>
                            <div className="pt-4 space-y-6">
                                <Separator/>
                                <Field orientation="horizontal" className="justify-between">
                                    <div className="space-y-0.5">
                                        <FieldLabel>Store Mirrored Artifacts</FieldLabel>
                                        <FieldDescription>
                                            Save artifacts fetched from the mirror to local storage.
                                        </FieldDescription>
                                    </div>
                                    <Switch
                                        checked={form.watch(getPath("storeArtifacts"))}
                                        onCheckedChange={(checked) => form.setValue(getPath("storeArtifacts"), checked)}
                                    />
                                </Field>

                                <Field orientation="horizontal" className="justify-between">
                                    <div className="space-y-0.5">
                                        <FieldLabel>Authorized Users Only</FieldLabel>
                                        <FieldDescription>
                                            Only use mirrors if the requestor is authorized to write to the local
                                            repository.
                                        </FieldDescription>
                                    </div>
                                    <Switch
                                        checked={form.watch(getPath("authorizedUsersOnly"))}
                                        onCheckedChange={(checked) => form.setValue(getPath("authorizedUsersOnly"), checked)}
                                    />
                                </Field>
                            </div>

                            <div className="pt-4 space-y-4">
                                <div className="flex items-center justify-between">
                                    <h3 className="text-lg font-medium">Connections</h3>
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={() => append({
                                            internal: false,
                                            url: "",
                                            authType: "NONE",
                                            username: "",
                                            password: "",
                                            repositoryId: 0
                                        })}
                                        className="gap-2"
                                    >
                                        <Plus className="size-4"/>
                                        Add Connection
                                    </Button>
                                </div>

                                {fields.length === 0 ? (
                                    <div
                                        className="border border-dashed rounded-xl p-8 flex items-center justify-center">
                                        <span className="text-muted-foreground">No connections configured.</span>
                                    </div>
                                ) : (
                                    <div className="space-y-4">
                                        {fields.map((field, index) => (
                                            <Card key={field.id}>
                                                <CardContent className="p-4 py-0 space-y-4 relative">
                                                    <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="icon"
                                                        className={"absolute right-4 -top-2"}
                                                        onClick={() => remove(index)}
                                                    >
                                                        <Trash2 className="size-4"/>
                                                    </Button>
                                                    <Tabs
                                                        value={form.watch(`${getPath("connections")}.${index}.internal`) ? "internal" : "external"}
                                                        onValueChange={(value) => {
                                                            const isInternal = value === "internal";
                                                            form.setValue(`${getPath("connections")}.${index}.internal`, isInternal);
                                                            form.setValue(`${getPath("connections")}.${index}.authType`, isInternal ? "APPLY_USER_AUTH" : "NONE");
                                                        }}
                                                    >
                                                        <TabsList className="grid grid-cols-2">
                                                            <TabsTrigger value="external">External (URL)</TabsTrigger>
                                                            <TabsTrigger value="internal">Internal</TabsTrigger>
                                                        </TabsList>

                                                        <TabsContent value="external" className="pt-4">
                                                            <FormField
                                                                className="flex-1"
                                                                control={form.control}
                                                                fieldName={`${getPath("connections")}.${index}.url` as any}
                                                                label="Mirror URL"
                                                                placeholder={placeholder}
                                                            />
                                                        </TabsContent>

                                                        <TabsContent value="internal" className="pt-4">
                                                            <Field className="flex-1">
                                                                <FieldLabel>Repository</FieldLabel>
                                                                <Controller
                                                                    name={`${getPath("connections")}.${index}.repositoryId`}
                                                                    control={form.control}
                                                                    render={({field}) => (
                                                                        <Select
                                                                            value={field.value?.toString()}
                                                                            onValueChange={(v) => field.onChange(parseInt(v))}
                                                                            disabled={usableRepositories.length === 0}
                                                                        >
                                                                            <SelectTrigger>
                                                                                {usableRepositories.length > 0
                                                                                    ? (<SelectValue placeholder="Select a repository"/>)
                                                                                    : (<span className={"text-xs"}>No repositories available</span>)}
                                                                            </SelectTrigger>
                                                                            <SelectContent>
                                                                                {usableRepositories.map((repo) => (
                                                                                    <SelectItem key={repo.repository.id}
                                                                                                value={repo.repository.id!.toString()}>
                                                                                        {repo.repository.name}
                                                                                    </SelectItem>
                                                                                ))}
                                                                            </SelectContent>
                                                                        </Select>
                                                                    )}
                                                                />
                                                            </Field>
                                                        </TabsContent>
                                                    </Tabs>

                                                    <Field>
                                                        <FieldLabel>Authentication Type</FieldLabel>
                                                        <Controller
                                                            name={`${getPath("connections")}.${index}.authType`}
                                                            control={form.control}
                                                            render={({field}) => (
                                                                <Select
                                                                    value={field.value}
                                                                    onValueChange={field.onChange}
                                                                >
                                                                    <SelectTrigger>
                                                                        <SelectValue/>
                                                                    </SelectTrigger>
                                                                    <SelectContent>
                                                                        {form.watch(`${getPath("connections")}.${index}.internal`) ? (
                                                                            <>
                                                                                <SelectItem value="APPLY_USER_AUTH">Apply
                                                                                    User Authentication</SelectItem>
                                                                                <SelectItem value="APPLY_SYSTEM_AUTH">Skip
                                                                                    authentication</SelectItem>
                                                                            </>
                                                                        ) : (
                                                                            <>
                                                                                <SelectItem
                                                                                    value="NONE">None</SelectItem>
                                                                                <SelectItem
                                                                                    value="BASIC">Basic</SelectItem>
                                                                                <SelectItem value="BEARER">Bearer
                                                                                    Token</SelectItem>
                                                                            </>
                                                                        )}
                                                                    </SelectContent>
                                                                </Select>
                                                            )}
                                                        />
                                                    </Field>

                                                    {form.watch(`${getPath("connections")}.${index}.authType`) === "BASIC" && (
                                                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                                            <FormField
                                                                control={form.control}
                                                                fieldName={`${getPath("connections")}.${index}.username` as any}
                                                                label="Username"
                                                            />
                                                            <FormField
                                                                control={form.control}
                                                                fieldName={`${getPath("connections")}.${index}.password` as any}
                                                                label="Password"
                                                                type="password"
                                                            />
                                                        </div>
                                                    )}

                                                    {form.watch(`${getPath("connections")}.${index}.authType`) === "BEARER" && (
                                                        <FormField
                                                            control={form.control}
                                                            fieldName={`${getPath("connections")}.${index}.password` as any}
                                                            label="Token"
                                                            type="password"
                                                        />
                                                    )}
                                                </CardContent>
                                            </Card>
                                        ))}
                                    </div>
                                )}
                            </div>
                        </>
                    )}
                </CardContent>
            </Card>
        </section>
    );
}
