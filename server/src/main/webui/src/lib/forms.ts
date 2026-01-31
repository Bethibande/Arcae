import type {FieldValues, UseFormReturn} from "react-hook-form";
import type {FormEventHandler} from "react";

export function handleSubmit<T1 extends FieldValues, T2, T3>(form: UseFormReturn<T1, T2, T3>, onSubmit: (data: T3) => void, onInvalid?: (errors: any) => void): FormEventHandler<HTMLFormElement> {
    return (e) => {
        e.stopPropagation();
        form.handleSubmit(onSubmit, onInvalid)(e);
    }
}