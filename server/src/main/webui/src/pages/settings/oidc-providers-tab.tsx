import { useCallback, useEffect, useMemo, useState } from "react"
import {
  Edit,
  Globe,
  MoreHorizontal,
  Plus,
  RefreshCcw,
  Sparkles,
  Trash2,
} from "lucide-react"
import { Button } from "@/components/ui/button.tsx"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog.tsx"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu.tsx"
import { toast } from "sonner"
import { oidcApi } from "@/lib/api.ts"
import { showError } from "@/lib/errors.ts"
import { type OpenIDConnectProviderDTO } from "@/generated"
import { type ColumnDef, DataTable } from "@/components/ui/data-table.tsx"
import { useForm } from "react-hook-form"
import { FormField } from "@/components/form-field.tsx"
import { zodResolver } from "@hookform/resolvers/zod"
import * as z from "zod"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip.tsx"

const providerSchema = z.object({
  name: z.string().min(1, "Name is required."),
  clientId: z.string().min(1, "Client ID is required."),
  clientSecret: z.string().min(1, "Client Secret is required."),
  discoveryUrl: z
    .string()
    .url("Invalid discovery URL.")
    .optional()
    .or(z.literal("")),
  authorizationEndpoint: z.string().url("Invalid authorization endpoint."),
  tokenEndpoint: z.string().url("Invalid token endpoint."),
  userInfoEndpoint: z.string().url("Invalid user info endpoint."),
  issuer: z.string().url("Invalid issuer.").optional().or(z.literal("")),
  jwksUri: z.string().url("Invalid JWKS URI.").optional().or(z.literal("")),
})

const emptyProviderValues: ProviderFormValues = {
  name: "",
  clientId: "",
  clientSecret: "",
  discoveryUrl: "",
  authorizationEndpoint: "",
  tokenEndpoint: "",
  userInfoEndpoint: "",
  issuer: "",
  jwksUri: "",
}

type ProviderFormValues = z.infer<typeof providerSchema>

interface ProviderDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  provider: OpenIDConnectProviderDTO | null
  onSuccess: () => void
}

function ProviderDialog({
  open,
  onOpenChange,
  provider,
  onSuccess,
}: ProviderDialogProps) {
  const [loading, setLoading] = useState(false)
  const [fetchingOptions, setFetchingOptions] = useState(false)
  const isEdit = !!provider

  const { handleSubmit, control, reset, getValues, setValue } =
    useForm<ProviderFormValues>({
      resolver: zodResolver(providerSchema),
      defaultValues: emptyProviderValues,
    })

  useEffect(() => {
    if (provider) {
      reset({
        name: provider.name,
        clientId: provider.clientId,
        clientSecret: provider.clientSecret,
        discoveryUrl: provider.discoveryUrl ?? "",
        authorizationEndpoint: provider.authorizationEndpoint,
        tokenEndpoint: provider.tokenEndpoint,
        userInfoEndpoint: provider.userInfoEndpoint,
        issuer: provider.issuer ?? "",
        jwksUri: provider.jwksUri ?? "",
      })
    } else {
      reset(emptyProviderValues)
    }
  }, [provider, reset, open])

  const handleAutoConfig = async () => {
    const url = getValues("discoveryUrl")
    if (!url) {
      toast.error("Please enter a discovery URL first.")
      return
    }

    setFetchingOptions(true)
    try {
      const options = await oidcApi.apiV1OidcGet({ configUri: url })
      if (options.authorizationEndpoint)
        setValue("authorizationEndpoint", options.authorizationEndpoint)
      if (options.tokenEndpoint)
        setValue("tokenEndpoint", options.tokenEndpoint)
      if (options.userinfoEndpoint)
        setValue("userInfoEndpoint", options.userinfoEndpoint)
      if (options.issuer) setValue("issuer", options.issuer)
      if (options.jwksUri) setValue("jwksUri", options.jwksUri)
      toast.success("OIDC options fetched successfully.")
    } catch (e) {
      showError(e)
    } finally {
      setFetchingOptions(false)
    }
  }

  const onSubmit = async (values: ProviderFormValues) => {
    setLoading(true)
    try {
      if (isEdit && provider) {
        await oidcApi.apiV1OidcProviderPut({
          openIDConnectProviderDTO: {
            ...values,
            id: provider.id,
          },
        })
        toast.success("OIDC Provider updated successfully.")
      } else {
        await oidcApi.apiV1OidcProviderPost({
          openIDConnectProviderDTOWithoutId: values,
        })
        toast.success("OIDC Provider created successfully.")
      }
      onSuccess()
      onOpenChange(false)
    } catch (e) {
      showError(e)
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90svh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit" : "Create"} OIDC Provider</DialogTitle>
          <DialogDescription>
            {isEdit
              ? "Update the OIDC provider configuration."
              : "Add a new OpenID Connect provider for authentication."}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <FormField
            control={control}
            fieldName="name"
            label="Name"
            placeholder="e.g. Google, Keycloak"
          />
          <FormField
            control={control}
            fieldName="clientId"
            label="Client ID"
            placeholder="Client ID"
          />
          <FormField
            control={control}
            fieldName="clientSecret"
            label="Client Secret"
            type="password"
            placeholder="Client Secret"
          />
          <div className="flex items-end gap-2">
            <div className="flex-1">
              <FormField
                control={control}
                fieldName="discoveryUrl"
                label="Discovery URL"
                placeholder="https://example.com/.well-known/openid-configuration"
              />
            </div>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  type="button"
                  variant="outline"
                  onClick={handleAutoConfig}
                  disabled={fetchingOptions}
                  className="mb-0"
                  size="icon"
                >
                  {fetchingOptions ? (
                    <RefreshCcw className={"size-4 animate-spin"} />
                  ) : (
                    <Sparkles className="size-4" />
                  )}
                </Button>
              </TooltipTrigger>
              <TooltipContent>
                <p>
                  Automatically configure OIDC provider settings based on the
                  provider's metadata.
                </p>
              </TooltipContent>
            </Tooltip>
          </div>
          <FormField
            control={control}
            fieldName="authorizationEndpoint"
            label="Authorization Endpoint"
            placeholder="https://example.com/oauth2/authorize"
          />
          <FormField
            control={control}
            fieldName="tokenEndpoint"
            label="Token Endpoint"
            placeholder="https://example.com/oauth2/token"
          />
          <FormField
            control={control}
            fieldName="userInfoEndpoint"
            label="User Info Endpoint"
            placeholder="https://example.com/oauth2/userinfo"
          />
          <FormField
            control={control}
            fieldName="issuer"
            label="Issuer"
            placeholder="https://example.com"
          />
          <FormField
            control={control}
            fieldName="jwksUri"
            label="JWKS URI"
            placeholder="https://example.com/oauth2/jwks"
            description="ID tokens are validated only if the issuer and JWKS URI are both set. Leave them empty for plain OAuth2 providers (such as GitHub) that don't issue ID tokens."
          />
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={loading}>
              {isEdit ? "Save Changes" : "Create"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

interface ProviderDeleteDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onConfirm: () => void
  providerName: string
}

function ProviderDeleteDialog({
  open,
  onOpenChange,
  onConfirm,
  providerName,
}: ProviderDeleteDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Delete OIDC Provider</DialogTitle>
          <DialogDescription>
            Are you sure you want to delete the provider{" "}
            <strong>{providerName}</strong>? This action cannot be undone.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button variant="destructive" onClick={onConfirm}>
            Delete
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function OidcProvidersTab() {
  const [providers, setProviders] = useState<OpenIDConnectProviderDTO[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [selectedProvider, setSelectedProvider] =
    useState<OpenIDConnectProviderDTO | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [providerToDelete, setProviderToDelete] =
    useState<OpenIDConnectProviderDTO | null>(null)

  const loadProviders = useCallback(async () => {
    setLoading(true)
    try {
      // We'll just load the first page for now, as there probably aren't many OIDC providers
      const result = await oidcApi.apiV1OidcProvidersGet({ p: 0, s: 100 })
      setProviders(result.data ?? [])
    } catch (e) {
      showError(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadProviders()
  }, [loadProviders])

  const handleDelete = useCallback(async () => {
    if (!providerToDelete) return
    try {
      await oidcApi.apiV1OidcProvidersIdDelete({ id: providerToDelete.id! })
      toast.success("OIDC Provider deleted.")
      setDeleteOpen(false)
      loadProviders()
    } catch (e) {
      showError(e)
    }
  }, [providerToDelete, loadProviders])

  const columns = useMemo<ColumnDef<OpenIDConnectProviderDTO>[]>(
    () => [
      {
        header: "Name",
        cell: (provider) => (
          <div className="flex items-center gap-3">
            <div className="flex size-8 items-center justify-center rounded-full bg-primary/10 text-primary">
              <Globe className="size-4" />
            </div>
            <span className="font-medium">{provider.name}</span>
          </div>
        ),
      },
      {
        header: "Client ID",
        accessorKey: "clientId",
      },
      {
        header: "",
        className: "w-0",
        cell: (provider) => (
          <div className="flex justify-end">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="h-8 w-8">
                  <MoreHorizontal className="size-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className={"w-fit"}>
                <DropdownMenuItem
                  onClick={() => {
                    setSelectedProvider(provider)
                    setOpen(true)
                  }}
                >
                  <Edit className="mr-2 size-4" /> Edit Provider
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={() => {
                    setProviderToDelete(provider)
                    setDeleteOpen(true)
                  }}
                  className="text-destructive focus:bg-destructive/10 focus:text-destructive"
                >
                  <Trash2 className="mr-2 size-4" /> Delete Provider
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        ),
      },
    ],
    []
  )

  return (
    <section className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="flex items-center gap-2 text-xl font-semibold">
            <Globe className="size-5" /> OIDC Providers
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Manage OpenID Connect providers for user authentication.
          </p>
        </div>
        <Button
          onClick={() => {
            setSelectedProvider(null)
            setOpen(true)
          }}
          className="gap-2"
        >
          <Plus className="size-4" /> Add Provider
        </Button>
      </div>

      <DataTable
        columns={columns}
        data={providers}
        loading={loading}
        emptyMessage="No OIDC providers configured."
      />

      <ProviderDialog
        open={open}
        onOpenChange={setOpen}
        provider={selectedProvider}
        onSuccess={loadProviders}
      />

      <ProviderDeleteDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        onConfirm={handleDelete}
        providerName={providerToDelete?.name ?? ""}
      />
    </section>
  )
}
