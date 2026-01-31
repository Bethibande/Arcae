import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { type AccessTokenDTO, type AccessTokenDTOWithoutToken, AccessTokenEndpointApi } from "@/generated";
import { toast } from "sonner";
import { useEffect, useState } from "react";
import { FormField } from "@/components/form-field";
import { handleSubmit } from "@/lib/forms.ts";
import { Copy, Check } from "lucide-react";

const tokenSchema = z.object({
  id: z.number().optional(),
  name: z.string().min(1, "Name is required"),
  expiresAfter: z.string().optional().or(z.literal("")),
});

type TokenFormValues = z.infer<typeof tokenSchema>;

interface AccessTokenEditDialogProps {
  token?: AccessTokenDTOWithoutToken | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function AccessTokenEditDialog({
  token,
  open,
  onOpenChange,
  onSuccess,
}: AccessTokenEditDialogProps) {
  const isEdit = !!token;
  const [createdToken, setCreatedToken] = useState<AccessTokenDTO | null>(null);
  const [copied, setCopied] = useState(false);

  const form = useForm<TokenFormValues>({
    resolver: zodResolver(tokenSchema),
    defaultValues: {
      id: undefined,
      name: "",
      expiresAfter: "",
    },
  });

  const {
    control,
    reset,
    formState: { isSubmitting },
  } = form;

  useEffect(() => {
    if (open) {
      setCreatedToken(null);
      setCopied(false);
      if (token) {
        reset({
          id: token.id,
          name: token.name,
          expiresAfter: token.expiresAfter ? new Date(token.expiresAfter).toISOString().slice(0, 16) : "",
        });
      } else {
        reset({
          id: undefined,
          name: "",
          expiresAfter: "",
        });
      }
    }
  }, [token, reset, open]);

  const onSubmit = async (data: TokenFormValues) => {
    try {
      const api = new AccessTokenEndpointApi();
      const expiresAfter = data.expiresAfter ? new Date(data.expiresAfter) : undefined;

      if (isEdit && token) {
        await api.apiV1TokensPut({
          accessTokenDTOWithoutToken: {
            id: token.id,
            name: data.name,
            expiresAfter: expiresAfter,
          },
        });
        toast.success("Token updated successfully");
        onSuccess();
        onOpenChange(false);
      } else {
        const response = await api.apiV1TokensPost({
          accessTokenDTOWithoutId: {
            name: data.name,
            expiresAfter: expiresAfter,
          },
        });
        setCreatedToken(response);
        toast.success("Token created successfully");
        onSuccess();
      }
    } catch (error) {
      console.error("Failed to save token", error);
      toast.error("Failed to save token");
    }
  };

  const copyToClipboard = () => {
    if (createdToken?.token) {
      navigator.clipboard.writeText(createdToken.token);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
      toast.success("Token copied to clipboard");
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit Access Token" : "Create Access Token"}</DialogTitle>
          <DialogDescription>
            {isEdit
              ? "Update token details here."
              : createdToken 
                ? "This is your new personal access token. Make sure to copy it now. You won't be able to see it again!"
                : "Fill in the details to create a new access token."}
          </DialogDescription>
        </DialogHeader>

        {createdToken ? (
          <div className="space-y-4 py-4">
            <div className="flex items-center gap-2">
              <Input
                readOnly
                value={createdToken.token}
                className="font-mono bg-muted"
              />
              <Button size="icon" variant="outline" onClick={copyToClipboard}>
                {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
              </Button>
            </div>
            <DialogFooter>
              <Button onClick={() => onOpenChange(false)}>Close</Button>
            </DialogFooter>
          </div>
        ) : (
          <form onSubmit={handleSubmit(form, onSubmit)} className="space-y-4 py-4">
            <FormField
              control={control}
              fieldName="name"
              label="Name"
              Input={(props) => <Input {...props} placeholder="My Token" />}
            />
            <FormField
              control={control}
              fieldName="expiresAfter"
              label="Expiration Date (Optional)"
              Input={(props) => (
                <Input {...props} type="datetime-local" />
              )}
            />
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => onOpenChange(false)}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Saving..." : (isEdit ? "Save Changes" : "Create Token")}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
