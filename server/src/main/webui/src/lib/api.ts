import {
    AccessTokenEndpointApi,
    ArtifactEndpointApi,
    AuthenticationEndpointApi,
    Configuration,
    JobEndpointApi,
    RepositoryEndpointApi,
    RepositoryPermissionEndpointApi,
    SetupEndpointApi,
    SystemEndpointApi,
    UserEndpointApi
} from "@/generated";
import {RefreshMiddleware} from "@/lib/middleware.ts";

export const apiConfig = new Configuration({
    middleware: [RefreshMiddleware],
});

export const accessTokenApi = new AccessTokenEndpointApi(apiConfig);
export const artifactApi = new ArtifactEndpointApi(apiConfig);
export const authApi = new AuthenticationEndpointApi(apiConfig);
export const jobApi = new JobEndpointApi(apiConfig);
export const repositoryApi = new RepositoryEndpointApi(apiConfig);
export const repositoryPermissionApi = new RepositoryPermissionEndpointApi(apiConfig);
export const setupApi = new SetupEndpointApi(apiConfig);
export const systemApi = new SystemEndpointApi(apiConfig);
export const userApi = new UserEndpointApi(apiConfig);
