import {
    AccessTokenEndpointApi,
    ArtifactEndpointApi,
    AuthenticationEndpointApi,
    Configuration,
    JobEndpointApi,
    MailEndpointApi,
    MavenRepositoryEndpointApi,
    OCIRepositoryEndpointApi,
    OpenIDConnectEndpointApi,
    RepositoryEndpointApi,
    RepositoryPermissionEndpointApi,
    S3RepositoryBackendEndpointApi,
    SetupEndpointApi,
    SystemEndpointApi,
    UserEndpointApi,
} from "@/generated"
import {RefreshMiddleware} from "@/lib/middleware"

const configuration = new Configuration({
  basePath: "",
  middleware: [RefreshMiddleware],
})

export const authApi = new AuthenticationEndpointApi(configuration)
export const accessTokenApi = new AccessTokenEndpointApi(configuration)
export const artifactApi = new ArtifactEndpointApi(configuration)
export const jobApi = new JobEndpointApi(configuration)
export const mavenRepositoryApi = new MavenRepositoryEndpointApi(configuration)
export const mailApi = new MailEndpointApi(configuration)
export const ociRepositoryApi = new OCIRepositoryEndpointApi(configuration)
export const repositoryApi = new RepositoryEndpointApi(configuration)
export const repositoryPermissionApi = new RepositoryPermissionEndpointApi(configuration)
export const setupApi = new SetupEndpointApi(configuration)
export const systemApi = new SystemEndpointApi(configuration)
export const userApi = new UserEndpointApi(configuration)
export const oidcApi = new OpenIDConnectEndpointApi(configuration)
export const s3BackendApi = new S3RepositoryBackendEndpointApi(configuration)
