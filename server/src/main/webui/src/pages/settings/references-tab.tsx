import {useCallback, useEffect, useState} from "react";
import {systemApi} from "@/lib/api.ts";
import {SystemReferenceType} from "@/generated";
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card.tsx";
import {Button} from "@/components/ui/button.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from "@/components/ui/select.tsx";
import {ExternalLink, FileText, Plus, Save, Trash2, ArrowUp, ArrowDown} from "lucide-react";
import {toast} from "sonner";
import {Textarea} from "@/components/ui/textarea.tsx";
import {showError} from "@/lib/errors.ts";
import {useFieldArray, useForm} from "react-hook-form";
import {zodResolver} from "@hookform/resolvers/zod";
import * as z from "zod";
import {FormField} from "@/components/form-field.tsx";
import {useSystem} from "@/components/system-provider.tsx";

const referenceSchema = z.object({
    label: z.string().min(1, "Label is required"),
    type: z.nativeEnum(SystemReferenceType),
    value: z.string().min(1, "Content/URL is required"),
});

const formSchema = z.object({
    references: z.array(referenceSchema),
});

type FormValues = z.infer<typeof formSchema>;

export function ReferencesTab() {
    const {refreshReferences} = useSystem();
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);

    const {
        control,
        handleSubmit,
        reset,
        watch,
        formState: {isDirty},
    } = useForm<FormValues>({
        resolver: zodResolver(formSchema),
        defaultValues: {
            references: [],
        },
    });

    const {fields, append, remove, move} = useFieldArray({
        control,
        name: "references",
    });

    const watchedReferences = watch("references");

    const fetchReferences = useCallback(async () => {
        try {
            const data = await systemApi.apiV1SystemFooterRefsGet();
            reset({references: data});
        } catch (error) {
            showError(error)
        } finally {
            setLoading(false);
        }
    }, [reset]);

    useEffect(() => {
        fetchReferences();
    }, [fetchReferences]);

    const handleAdd = () => {
        append({label: "", type: SystemReferenceType.Url, value: ""});
    };

    const handleSave = async (data: FormValues) => {
        setSaving(true);
        try {
            await systemApi.apiV1SystemFooterRefsPut({systemReference: data.references});
            await refreshReferences();
            toast.success("Header references saved successfully");
            reset(data); // Reset isDirty
        } catch (error) {
            showError(error)
        } finally {
            setSaving(false);
        }
    };

    if (loading) {
        return <div className="flex items-center justify-center h-64">Loading...</div>;
    }

    return (
        <div className="space-y-6">
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between">
                        <div>
                            <CardTitle>Header References</CardTitle>
                            <CardDescription>
                                Configure links and text references shown in the application header.
                            </CardDescription>
                        </div>
                        <Button onClick={handleAdd} size="sm" type="button">
                            <Plus className="mr-2 h-4 w-4"/>
                            Add Reference
                        </Button>
                    </div>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleSubmit(handleSave)} className="space-y-6">
                        {fields.length === 0 ? (
                            <div className="text-center py-12 border-2 border-dashed rounded-lg text-muted-foreground">
                                No header references configured.
                            </div>
                        ) : (
                            <div className="space-y-8">
                                {fields.map((field, index) => (
                                    <div key={field.id}
                                         className="relative p-4 border rounded-lg space-y-4 bg-accent/30">
                                        <div className="absolute top-2 right-2 flex space-x-2">
                                            <Button
                                                variant="ghost"
                                                size="icon"
                                                type="button"
                                                disabled={index === 0}
                                                onClick={() => move(index, index - 1)}
                                            >
                                                <ArrowUp className="h-4 w-4"/>
                                            </Button>
                                            <Button
                                                variant="ghost"
                                                size="icon"
                                                type="button"
                                                disabled={index === fields.length - 1}
                                                onClick={() => move(index, index + 1)}
                                            >
                                                <ArrowDown className="h-4 w-4"/>
                                            </Button>
                                            <Button
                                                variant="ghost"
                                                size="icon"
                                                type="button"
                                                className="text-destructive hover:text-destructive hover:bg-destructive/10"
                                                onClick={() => remove(index)}
                                            >
                                                <Trash2 className="h-4 w-4"/>
                                            </Button>
                                        </div>

                                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                            <FormField
                                                control={control}
                                                fieldName={`references.${index}.label`}
                                                label="Label"
                                                placeholder="e.g. Privacy Policy"
                                            />

                                            <FormField fieldName={`references.${index}.type`}
                                                       label={"Type"}
                                                       control={control}
                                                       Input={(props) => (<Select onValueChange={props.onChange} value={props.value}>
                                                           <SelectTrigger>
                                                               <SelectValue/>
                                                           </SelectTrigger>
                                                           <SelectContent>
                                                               <SelectItem value={SystemReferenceType.Url}>
                                                                   <div className="flex items-center">
                                                                       <ExternalLink className="mr-2 h-4 w-4"/>
                                                                       External URL
                                                                   </div>
                                                               </SelectItem>
                                                               <SelectItem value={SystemReferenceType.Text}>
                                                                   <div className="flex items-center">
                                                                       <FileText className="mr-2 h-4 w-4"/>
                                                                       Internal Text (Perma Link)
                                                                   </div>
                                                               </SelectItem>
                                                           </SelectContent>
                                                       </Select>)}/>
                                        </div>

                                        <FormField
                                            control={control}
                                            fieldName={`references.${index}.value`}
                                            label={watchedReferences[index]?.type === SystemReferenceType.Url ? 'URL' : 'Content'}
                                            Input={watchedReferences[index]?.type === SystemReferenceType.Text ? ({
                                                                                                                      id,
                                                                                                                      ...props
                                                                                                                  }: {
                                                id: string
                                            }) => (
                                                <Textarea
                                                    {...props}
                                                    id={id}
                                                    placeholder="Enter the text content here..."
                                                    className="min-h-32"
                                                />
                                            ) : undefined}
                                            placeholder={watchedReferences[index]?.type === SystemReferenceType.Url ? "https://example.com" : undefined}
                                        />
                                    </div>
                                ))}
                            </div>
                        )}

                        <div className="flex justify-end pt-4 border-t">
                            <Button type="submit" disabled={saving || !isDirty}>
                                <Save className="mr-2 h-4 w-4"/>
                                {saving ? "Saving..." : "Save Changes"}
                            </Button>
                        </div>
                    </form>
                </CardContent>
            </Card>
        </div>
    );
}
