import type {FunctionComponent} from "react";
import {type Control, Controller, type ControllerRenderProps, type FieldPath, type FieldValues} from "react-hook-form";
import {Field, FieldError, FieldLabel} from "@/components/ui/field.tsx";
import {Input as UIInput} from "@/components/ui/input.tsx";

interface InputProps<TFieldValues extends FieldValues = FieldValues, TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>> extends ControllerRenderProps<TFieldValues, TName> {
    id: string;
}

export interface FormFieldProps<TFieldValues extends FieldValues = FieldValues, TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>, TContext = any, TTransformedValues = TFieldValues> {
    fieldName: TName,
    label: string,
    Input?: FunctionComponent<InputProps<TFieldValues, TName>> | any,
    control: Control<TFieldValues, TContext, TTransformedValues>,
    placeholder?: string,
    type?: string,
    className?: string
}

export function FormField<TFieldValues extends FieldValues = FieldValues, TName extends FieldPath<TFieldValues> = FieldPath<TFieldValues>,  TContext = any, TTransformedValues = TFieldValues>(props: FormFieldProps<TFieldValues, TName, TContext, TTransformedValues>) {
    const {fieldName, label, Input, control, placeholder, type, className} = props;

    return (
        <Controller name={fieldName}
                    control={control}
                    render={({field, fieldState}) => (
                        <Field data-invalid={fieldState.invalid} className={className}>
                            <FieldLabel htmlFor={fieldName}>{label}</FieldLabel>

                            {Input ? (
                                <Input {...field}
                                       id={fieldName}
                                       aria-invalid={fieldState.invalid}/>
                            ) : (
                                <UIInput {...field}
                                         id={fieldName}
                                         type={type}
                                         placeholder={placeholder}
                                         aria-invalid={fieldState.invalid}/>
                            )}

                            {fieldState.invalid && (
                                <FieldError errors={[fieldState.error]}/>
                            )}
                        </Field>
                    )}/>
    );
}