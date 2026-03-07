import {type Middleware, type ResponseContext} from "@/generated";
import {showError, showHttpError} from "@/lib/errors.ts";

export function refresh(): Promise<boolean> {
    console.log("Trying to refresh user session")
    return fetch("/api/v1/auth/refresh", {})
        .then(response => {
            if (response.status === 400) {
                console.log("Failed to refresh user session")
                return false
            } else if (!response.ok) {
                showHttpError(response)
                return false
            }
            console.log("Refreshed user session")
            return true
        }).catch(err => {
            showError(err)
            return false
        })
}

export const RefreshMiddleware: Middleware = {
    post: async (context: ResponseContext): Promise<Response | void> => {
        if (context.response.status === 401) {
            const result = await refresh()
            if (!result) {
                window.location.href = "/login";
                return
            }

            return context.fetch(context.url, context.init)
        }

        return context.response
    }
}
