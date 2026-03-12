import {toast} from "sonner";

export async function showHttpErrorAndContinue(response: Response): Promise<Response> {
    if (!response.ok) {
        showHttpError(response)
        return response
    }

    return response
}

export async function getHttpErrorMessage(response: Response): Promise<string> {
    const contentType = response.headers.get("Content-Type") || ""

    try {
        if (contentType.startsWith("application/json")) {
            const body = await response.json()
            return body?.message ?? body?.title ?? response.statusText
        } else {
            const text = await response.text()
            return text.trim() || response.statusText
        }
    } catch {
        return response.statusText
    }
}

export function showHttpError(response: Response) {
    getHttpErrorMessage(response).then(showErrorMessage)
}

export async function getErrorMessage(error: unknown): Promise<string> {
    if (error && typeof error === "object" && "response" in error && error.response instanceof Response) {
        return getHttpErrorMessage(error.response)
    } else if (error instanceof Error) {
        return error.message
    }
    return String(error)
}

export function showError(error: unknown) {
    getErrorMessage(error).then(showErrorMessage)
}

export function showErrorMessage(message: string) {
    toast.error(message, {
        position: "top-center",
        duration: 5000,
    })
}