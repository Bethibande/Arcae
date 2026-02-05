import {type Control, useFormContext} from "react-hook-form";
import {type DynamicFormValues} from "@/lib/repository-configs.ts";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {FormField} from "@/components/form-field.tsx";
import {Switch} from "@/components/ui/switch.tsx";
import {Input} from "@/components/ui/input.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {ChronoUnit} from "@/generated";

interface CleanupPoliciesFormProps {
    control: Control<DynamicFormValues, any, any>;
}

export function CleanupPoliciesForm({control}: CleanupPoliciesFormProps) {
    const {watch} = useFormContext<DynamicFormValues>();
    const maxAgeEnabled = watch("cleanupPolicies.maxAgePolicy.enabled");
    const maxVersionCountEnabled = watch("cleanupPolicies.maxVersionCountPolicy.enabled");

    const filteredChronoUnits = [
        ChronoUnit.Minutes,
        ChronoUnit.Hours,
        ChronoUnit.Days,
        ChronoUnit.Weeks,
        ChronoUnit.Months,
        ChronoUnit.Years,
    ];

    return (
        <div id="cleanup" className="space-y-6 pt-4">
            <h2 className="text-xl font-bold tracking-tight">Cleanup Policies</h2>
            <Card>
                <CardContent className="divide-y p-0">
                    <div className="p-6 space-y-4">
                        <div className="flex items-center justify-between">
                            <div className="space-y-0.5">
                                <label className="text-base font-semibold">Max Artifact Age</label>
                                <p className="text-sm text-muted-foreground">
                                    Automatically delete artifact versions older than a certain age.
                                </p>
                            </div>
                            <div className="flex-none">
                                <FormField
                                    fieldName="cleanupPolicies.maxAgePolicy.enabled"
                                    label={""}
                                    control={control}
                                    Input={({value, onChange}) => (
                                        <Switch checked={value} onCheckedChange={onChange}/>
                                    )}
                                />
                            </div>
                        </div>

                        {maxAgeEnabled && (
                            <div className="flex gap-4 pt-4 border-t">
                                <div className="flex-1">
                                    <FormField
                                        fieldName="cleanupPolicies.maxAgePolicy.time"
                                        label="Time"
                                        control={control}
                                        Input={(props) => <Input {...props} type="number" min="1"/>}
                                    />
                                </div>
                                <div className="flex-1">
                                    <FormField
                                        fieldName="cleanupPolicies.maxAgePolicy.unit"
                                        label="Unit"
                                        control={control}
                                        Input={({value, onChange}) => (
                                            <Select value={value} onValueChange={onChange}>
                                                <SelectTrigger>
                                                    <SelectValue placeholder="Select unit"/>
                                                </SelectTrigger>
                                                <SelectContent>
                                                    {filteredChronoUnits.map((unit) => (
                                                        <SelectItem key={unit} value={unit}>
                                                            {unit.charAt(0) + unit.slice(1).toLowerCase()}
                                                        </SelectItem>
                                                    ))}
                                                </SelectContent>
                                            </Select>
                                        )}
                                    />
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="p-6 space-y-4">
                        <div className="flex items-center justify-between">
                            <div className="space-y-0.5">
                                <label className="text-base font-semibold">Max Version Count</label>
                                <p className="text-sm text-muted-foreground">
                                    Keep only a specific number of versions for each artifact.
                                </p>
                            </div>
                            <div className="flex-none">
                                <FormField
                                    fieldName="cleanupPolicies.maxVersionCountPolicy.enabled"
                                    label={""}
                                    control={control}
                                    Input={({value, onChange}) => (
                                        <Switch checked={value} onCheckedChange={onChange}/>
                                    )}
                                />
                            </div>
                        </div>

                        {maxVersionCountEnabled && (
                            <div className="pt-4 border-t">
                                <FormField
                                    fieldName="cleanupPolicies.maxVersionCountPolicy.maxVersions"
                                    label="Max Versions"
                                    control={control}
                                    Input={(props) => <Input {...props} type="number" min="1"/>}
                                />
                            </div>
                        )}
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}
