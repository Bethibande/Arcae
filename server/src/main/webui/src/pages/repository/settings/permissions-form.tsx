import {useFieldArray, useFormContext} from "react-hook-form";
import {type RepositorySchema} from "@/pages/repository/settings/schema.tsx";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {Button} from "@/components/ui/button.tsx";
import {Lock, Plus, Trash2} from "lucide-react";
import {Field} from "@/components/ui/field.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {Input} from "@/components/ui/input.tsx";
import {UserSearch} from "@/components/user-search.tsx";

export function PermissionsForm() {
    const {control, watch, setValue} = useFormContext<RepositorySchema>();
    const {fields, append, remove} = useFieldArray({
        control,
        name: "permissions"
    });

    const addRule = () => {
        append({
            type: "ANONYMOUS",
            level: "READ"
        });
    };

    return (
        <section id="permissions" className="space-y-6">
            <div className="flex items-center justify-between">
                <h2 className="text-xl font-semibold flex items-center gap-2"><Lock/> Permissions</h2>
                <Button variant="outline" size="sm" onClick={addRule} className="gap-2">
                    <Plus className="size-4"/>
                    Add Rule
                </Button>
            </div>

            <Card>
                <CardContent className="p-0">
                    {fields.length === 0 ? (
                        <div className="p-12 text-center text-muted-foreground border-dashed border-2 m-6 rounded-xl">
                            No permission rules defined. Default permissions apply.
                        </div>
                    ) : (
                        <div className="divide-y">
                            {fields.map((field, index) => {
                                const type = watch(`permissions.${index}.type`);
                                return (
                                    <div key={field.id}>
                                        <div className="grid grid-cols-[1.5fr_2fr_1.5fr_auto] gap-6 pt-5 p-2 px-6 items-center group transition-colors hover:bg-muted/5">
                                            <p>Who</p>
                                            <p>Target</p>
                                            <p>Access Level</p>
                                            <span className="w-8"></span>
                                        </div>
                                        <div className="grid grid-cols-[1.5fr_2fr_1.5fr_auto] gap-6 pb-4 px-6 items-center group transition-colors hover:bg-muted/5">
                                            <Field>
                                                <Select
                                                    value={type}
                                                    onValueChange={(val) => {
                                                        setValue(`permissions.${index}.type`, val as any);
                                                        if (val !== "USER") {
                                                            setValue(`permissions.${index}.userName`, undefined);
                                                            setValue(`permissions.${index}.userId`, undefined);
                                                        }
                                                    }}
                                                >
                                                    <SelectTrigger className="bg-muted/10">
                                                        <SelectValue/>
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value="ANONYMOUS">Anonymous</SelectItem>
                                                        <SelectItem value="AUTHENTICATED">Authenticated</SelectItem>
                                                        <SelectItem value="USER">Specific User</SelectItem>
                                                    </SelectContent>
                                                </Select>
                                            </Field>

                                            <Field>
                                                {type === "USER" ? (
                                                    <UserSearch
                                                        value={watch(`permissions.${index}.userName`)}
                                                        onSelect={(user) => {
                                                            setValue(`permissions.${index}.userName`, user.name);
                                                            setValue(`permissions.${index}.userId`, user.id);
                                                        }}
                                                        onClear={() => {
                                                            setValue(`permissions.${index}.userName`, undefined);
                                                            setValue(`permissions.${index}.userId`, undefined);
                                                        }}
                                                    />
                                                ) : (
                                                    <Input
                                                        disabled
                                                        value={type === "ANONYMOUS" ? "All visitors" : "Any logged in user"}
                                                        className="bg-muted/10 opacity-70"
                                                    />
                                                )}
                                            </Field>

                                            <Field>
                                                <Select
                                                    value={watch(`permissions.${index}.level`)}
                                                    onValueChange={(val) => setValue(`permissions.${index}.level`, val as any)}
                                                >
                                                    <SelectTrigger className="bg-muted/10">
                                                        <SelectValue/>
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value="READ">Read</SelectItem>
                                                        <SelectItem value="WRITE">Write</SelectItem>
                                                    </SelectContent>
                                                </Select>
                                            </Field>

                                            <div className="flex justify-end">
                                                <Button
                                                    variant="ghost"
                                                    size="icon"
                                                    onClick={() => remove(index)}
                                                    className="text-muted-foreground hover:text-destructive transition-colors h-9 w-9"
                                                >
                                                    <Trash2 className="size-4"/>
                                                </Button>
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </CardContent>
            </Card>
        </section>
    );
}
