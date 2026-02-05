import type {FieldValues, UseFormReturn} from "react-hook-form";
import type {FormEventHandler} from "react";

export function handleSubmit<
    TFieldValues extends FieldValues = FieldValues,
    TContext = any,
    TTransformedValues extends FieldValues | undefined = undefined
>(
    form: UseFormReturn<TFieldValues, TContext, TTransformedValues>,
    onSubmit: (data: TTransformedValues extends undefined ? TFieldValues : TTransformedValues) => void,
    onInvalid?: (errors: any) => void
): FormEventHandler<HTMLFormElement> {
    return (e) => {
        e.stopPropagation();
        form.handleSubmit(onSubmit as any, onInvalid)(e);
    }
}