import {Controller, type Control, useFieldArray, useFormContext} from "react-hook-form";
import {type DynamicFormValues} from "@/lib/repository-configs.ts";
import {Button} from "@/components/ui/button.tsx";
import {Plus, Trash2, User} from "lucide-react";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {PermissionLevel, UserSelectionType} from "@/generated";
import {UserSearch} from "@/components/UserSearch.tsx";
import {FormField} from "@/components/form-field.tsx";

interface PermissionsFormProps {
    control: Control<DynamicFormValues>;
}

export function PermissionsForm({control}: PermissionsFormProps) {
    const {fields, append, remove} = useFieldArray({
        control,
        name: "permissions"
    });
    const {setValue} = useFormContext<DynamicFormValues>();

    const addPermission = () => {
        append({
            level: PermissionLevel.Read,
            type: UserSelectionType.Anonymous
        });
    };

    return (
        <div id="permissions" className="space-y-6 pt-4">
            <div className="flex items-center justify-between">
                <h2 className="text-xl font-bold tracking-tight">Permissions</h2>
                <Button type="button" variant="outline" size="sm" onClick={addPermission}>
                    <Plus className="size-4 mr-2"/>
                    Add Rule
                </Button>
            </div>

            {fields.length === 0 ? (
                <Card>
                    <CardContent className="p-6 text-center">
                        <p className="text-sm text-muted-foreground">No custom permissions defined. Default access applies.</p>
                    </CardContent>
                </Card>
            ) : (
                <Card className="overflow-visible">
                    <CardContent className="p-0 divide-y">
                        {fields.map((field, index) => (
                            <div key={field.id} className="p-4 flex flex-col md:flex-row gap-4 items-end">
                                <div className="flex-1 grid grid-cols-1 md:grid-cols-12 gap-4 min-w-0">
                                    <div className="md:col-span-3">
                                        <FormField
                                            control={control}
                                            fieldName={`permissions.${index}.type`}
                                            label="Who"
                                            Input={({value, onChange}) => (
                                                <Select
                                                    value={value}
                                                    onValueChange={(val) => {
                                                        onChange(val);
                                                        // Reset user fields if type changes
                                                        setValue(`permissions.${index}.userId`, undefined);
                                                        setValue(`permissions.${index}.userName`, undefined);
                                                    }}
                                                >
                                                    <SelectTrigger>
                                                        <SelectValue/>
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value={UserSelectionType.Anonymous}>Anonymous (Everyone)</SelectItem>
                                                        <SelectItem value={UserSelectionType.Authenticated}>Authenticated Users</SelectItem>
                                                        <SelectItem value={UserSelectionType.User}>Specific User</SelectItem>
                                                    </SelectContent>
                                                </Select>
                                            )}
                                        />
                                    </div>

                                    <div className="md:col-span-6">
                                        <Controller
                                            control={control}
                                            name={`permissions.${index}.type`}
                                            render={({field: typeField}) => (
                                                <FormField
                                                    control={control}
                                                    fieldName={`permissions.${index}.userId`}
                                                    label={typeField.value === UserSelectionType.User ? "Target User" : "Target"}
                                                    Input={({value, onChange}) => (
                                                        <div className="h-10 min-w-0">
                                                            {typeField.value === UserSelectionType.User ? (
                                                                value ? (
                                                                    <div className="flex items-center gap-2 px-3 py-2 border rounded-md bg-muted/50 h-10">
                                                                        <User className="size-4 text-muted-foreground"/>
                                                                        <span className="text-sm flex-1 truncate">
                                                                            <Controller
                                                                                control={control}
                                                                                name={`permissions.${index}.userName`}
                                                                                render={({field: nameField}) => <>{nameField.value}</>}
                                                                            />
                                                                        </span>
                                                                        <Button
                                                                            type="button"
                                                                            variant="ghost"
                                                                            size="sm"
                                                                            className="h-6 w-6 p-0"
                                                                            onClick={() => {
                                                                                onChange(undefined);
                                                                                setValue(`permissions.${index}.userName`, undefined);
                                                                            }}
                                                                        >
                                                                            <Trash2 className="size-3"/>
                                                                        </Button>
                                                                    </div>
                                                                ) : (
                                                                    <UserSearch
                                                                        onSelect={(user) => {
                                                                            onChange(user.id);
                                                                            setValue(`permissions.${index}.userName`, user.name);
                                                                        }}
                                                                    />
                                                                )
                                                            ) : (
                                                                <div className="px-3 py-2 border rounded-md bg-muted/20 text-sm text-muted-foreground h-9 flex items-center">
                                                                    {typeField.value === UserSelectionType.Anonymous ? "All visitors" : "Any logged in user"}
                                                                </div>
                                                            )}
                                                        </div>
                                                    )}
                                                />
                                            )}
                                        />
                                    </div>

                                    <div className="md:col-span-3">
                                        <FormField
                                            control={control}
                                            fieldName={`permissions.${index}.level`}
                                            label="Access Level"
                                            Input={({value, onChange}) => (
                                                <Select
                                                    value={value}
                                                    onValueChange={onChange}
                                                >
                                                    <SelectTrigger className="w-full">
                                                        <SelectValue/>
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value={PermissionLevel.Read}>Read</SelectItem>
                                                        <SelectItem value={PermissionLevel.Write}>Write</SelectItem>
                                                    </SelectContent>
                                                </Select>
                                            )}
                                        />
                                    </div>
                                </div>

                                <div className="mb-[2px]">
                                    <Button
                                        type="button"
                                        variant="ghost"
                                        size="icon"
                                        className="text-destructive hover:text-destructive hover:bg-destructive/10"
                                        onClick={() => remove(index)}
                                    >
                                        <Trash2 className="size-4"/>
                                    </Button>
                                </div>
                            </div>
                        ))}
                    </CardContent>
                </Card>
            )}
        </div>
    );
}
