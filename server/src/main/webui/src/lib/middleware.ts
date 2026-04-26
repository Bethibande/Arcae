import {
    AuthenticationEndpointApi,
    Configuration,
    type LoginResponse,
    type Middleware,
    type ResponseContext
} from "@/generated";

const refreshApi = new AuthenticationEndpointApi(new Configuration({
    basePath: "",
}))

export function refresh(): Promise<LoginResponse | null> {
    console.log("Trying to refresh user session")
    return refreshApi.apiV1AuthRefreshGet()
        .then(response => {
            console.log("Refreshed user session")
            return response
        }).catch(err => {
            console.error("Failed to refresh user session", err)
            return null
        })
}

export const RefreshMiddleware: Middleware = {
    post: async (context: ResponseContext): Promise<Response | void> => {
        if (context.response.status === 401) {
            const result = await refresh()
            if (!result || result.result !== "LOGGED_IN") {
                window.location.href = "/login";
                return
            }

            const retried = await context.fetch(context.url, context.init)
            if (retried.status === 401) {
                window.location.href = "/login";
                return
            }

            return retried
        }

        return context.response
    }
}
