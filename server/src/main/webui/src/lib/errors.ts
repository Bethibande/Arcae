import {toast} from "sonner";

export async function showHttpErrorAndContinue(response: Response): Promise<Response> {
    if (!response.ok) {
        showHttpError(response)
        return response
    }

    return response
}

export function showHttpError(response: Response) {
    const contentLength = parseInt(response.headers.get("Content-Length") || "0")
    const contentType = response.headers.get("Content-Type") || ""

    if (contentLength > 0 && contentType.startsWith("application/json")) {
        response.json().then(() => {
            showErrorMessage(response.statusText)
        })
    } else {
        showErrorMessage(response.statusText)
    }
}

export function showError(error: any) {
    if (error.response) {
        showHttpError(error.response)
    } else {
        showErrorMessage(error.message)
    }
}

export function showErrorMessage(message: string) {
    toast.error(message, {
        position: "top-center",
        duration: 5000,
    })
}