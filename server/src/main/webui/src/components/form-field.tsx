import type {FunctionComponent} from "react";
import {type Control, Controller, type ControllerRenderProps, type FieldPath, type FieldValues} from "react-hook-form";
import {Field, FieldError, FieldLabel} from "@/components/ui/field.tsx";

interface InputProps<TFieldValues extends FieldValues = FieldValues, TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>> extends ControllerRenderProps<TFieldValues, TName> {
    id: string;
}

export interface FormFieldProps<TFieldValues extends FieldValues = FieldValues, TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>, TContext = any, TTransformedValues = TFieldValues> {
    fieldName: TName,
    label: string,
    Input: FunctionComponent<InputProps<TFieldValues, TName>>,
    control: Control<TFieldValues, TContext, TTransformedValues>
}

export function FormField<TFieldValues extends FieldValues = FieldValues, TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,  TContext = any, TTransformedValues = TFieldValues>(props: FormFieldProps<TFieldValues, TName, TContext, TTransformedValues>) {
    const {fieldName, label, Input, control} = props;

    return (
        <Controller name={fieldName}
                    control={control}
                    render={({field, fieldState}) => (
                        <Field data-invalid={fieldState.invalid}>
                            <FieldLabel htmlFor={fieldName}>{label}</FieldLabel>

                            <Input {...field}
                                   id={fieldName}
                                   aria-invalid={fieldState.invalid}/>

                            {fieldState.invalid && (
                                <FieldError errors={[fieldState.error]}/>
                            )}
                        </Field>
                    )}/>
    );
}