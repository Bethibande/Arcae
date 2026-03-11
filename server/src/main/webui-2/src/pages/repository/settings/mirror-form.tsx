import {useFieldArray, type UseFormReturn} from "react-hook-form";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {Field, FieldDescription, FieldLabel} from "@/components/ui/field.tsx";
import {Switch} from "@/components/ui/switch.tsx";
import {Input} from "@/components/ui/input.tsx";
import {Button} from "@/components/ui/button.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {Plus, RefreshCcw, Trash2} from "lucide-react";
import {Separator} from "@/components/ui/separator.tsx";

interface MirrorFormProps {
    form: UseFormReturn<any>;
    path?: string;
}

export function MirrorForm({ form, path = "mirrorConfig" }: MirrorFormProps) {
    const getPath = (field: string) => path ? `${path}.${field}` : field;
    
    const { fields, append, remove } = useFieldArray({
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
                                            Only use mirrors if the requestor is authorized to write to the local repository.
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
                                        onClick={() => append({ url: "", authType: "NONE", username: "", password: "" })}
                                        className="gap-2"
                                    >
                                        <Plus className="size-4" />
                                        Add Connection
                                    </Button>
                                </div>

                                {fields.length === 0 ? (
                                    <div className="border border-dashed rounded-xl p-8 flex items-center justify-center">
                                        <span className="text-muted-foreground">No connections configured.</span>
                                    </div>
                                ) : (
                                    <div className="space-y-4">
                                        {fields.map((field, index) => (
                                            <Card key={field.id}>
                                                <CardContent className="p-4 py-0 space-y-4">
                                                    <div className="flex items-start justify-between gap-4">
                                                        <Field className="flex-1">
                                                            <FieldLabel className={"items-center justify-between"}>
                                                                Mirror URL
                                                                <Button
                                                                    type="button"
                                                                    variant="ghost"
                                                                    size="icon"
                                                                    onClick={() => remove(index)}
                                                                >
                                                                    <Trash2 className="size-4" />
                                                                </Button>
                                                            </FieldLabel>
                                                            <Input
                                                                {...form.register(`${getPath("connections")}.${index}.url`)}
                                                                placeholder="https://registry-1.docker.io"
                                                            />
                                                        </Field>
                                                    </div>

                                                    <Field>
                                                        <FieldLabel>Authentication Type</FieldLabel>
                                                        <Select
                                                            value={form.watch(`${getPath("connections")}.${index}.authType`)}
                                                            onValueChange={(value) => form.setValue(`${getPath("connections")}.${index}.authType`, value)}
                                                        >
                                                            <SelectTrigger>
                                                                <SelectValue />
                                                            </SelectTrigger>
                                                            <SelectContent>
                                                                <SelectItem value="NONE">None</SelectItem>
                                                                <SelectItem value="BASIC">Basic</SelectItem>
                                                                <SelectItem value="BEARER">Bearer Token</SelectItem>
                                                            </SelectContent>
                                                        </Select>
                                                    </Field>

                                                    {form.watch(`${getPath("connections")}.${index}.authType`) === "BASIC" && (
                                                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                                            <Field>
                                                                <FieldLabel>Username</FieldLabel>
                                                                <Input
                                                                    {...form.register(`${getPath("connections")}.${index}.username`)}
                                                                />
                                                            </Field>
                                                            <Field>
                                                                <FieldLabel>Password</FieldLabel>
                                                                <Input
                                                                    {...form.register(`${getPath("connections")}.${index}.password`)}
                                                                    type="password"
                                                                />
                                                            </Field>
                                                        </div>
                                                    )}

                                                    {form.watch(`${getPath("connections")}.${index}.authType`) === "BEARER" && (
                                                        <Field>
                                                            <FieldLabel>Token</FieldLabel>
                                                            <Input
                                                                {...form.register(`${getPath("connections")}.${index}.password`)}
                                                                type="password"
                                                            />
                                                        </Field>
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
