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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { type UserDTOWithoutPassword, UserEndpointApi, UserRole } from "@/generated";
import { toast } from "sonner";
import { useEffect } from "react";
import { FormField } from "@/components/form-field";
import {handleSubmit} from "@/lib/forms.ts";

  const userSchema = z.object({
  id: z.number().optional(),
  name: z.string().min(2, "Name must be at least 2 characters"),
  email: z.string().email("Invalid email address"),
  roles: z.array(z.nativeEnum(UserRole)).min(1, "Select at least one role"),
  password: z.string().optional().or(z.literal("")),
}).superRefine((data, ctx) => {
  if (!data.id && (!data.password || data.password.length < 6)) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: "Password must be at least 6 characters",
      path: ["password"],
    });
  }
});

type UserFormValues = z.infer<typeof userSchema>;

interface UserEditDialogProps {
  user?: UserDTOWithoutPassword | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function UserEditDialog({
  user,
  open,
  onOpenChange,
  onSuccess,
}: UserEditDialogProps) {
  const isEdit = !!user;

  const form = useForm<UserFormValues>({
    resolver: zodResolver(userSchema),
    defaultValues: {
      id: undefined,
      name: "",
      email: "",
      roles: [UserRole.Default],
      password: "",
    },
  });

  const {
    control,
    reset,
    formState: { isSubmitting },
  } = form;

  useEffect(() => {
    if (user) {
      reset({
        id: user.id,
        name: user.name,
        email: user.email,
        roles: user.roles,
        password: "",
      });
    } else {
      reset({
        id: undefined,
        name: "",
        email: "",
        roles: [UserRole.Default],
        password: "",
      });
    }
  }, [user, reset, open]);

  const onSubmit = async (data: UserFormValues) => {
    try {
      const api = new UserEndpointApi();
      if (isEdit && user) {
        await api.apiV1UserPut({
          userDTOWithoutPassword: {
            id: user.id,
            name: data.name,
            email: data.email,
            roles: data.roles,
          },
        });
        toast.success("User updated successfully");
      } else {
        if (!data.password) {
          toast.error("Password is required for new users");
          return;
        }
        await api.apiV1UserPost({
          userDTOWithoutId: {
            name: data.name,
            email: data.email,
            roles: data.roles,
            password: data.password,
          },
        });
        toast.success("User created successfully");
      }
      onSuccess();
      onOpenChange(false);
    } catch (error) {
      console.error("Failed to save user", error);
      toast.error("Failed to save user");
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEdit ? "Edit User" : "Create User"}</DialogTitle>
          <DialogDescription>
            {isEdit
              ? "Update user details here."
              : "Fill in the details to create a new user."}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(form, onSubmit)} className="space-y-4 py-4">
          <FormField
            control={control}
            fieldName="name"
            label="Name"
            Input={(props) => <Input {...props} placeholder="John Doe" />}
          />
          <FormField
            control={control}
            fieldName="email"
            label="Email"
            Input={(props) => (
              <Input {...props} type="email" placeholder="john@example.com" />
            )}
          />
          <FormField
            control={control}
            fieldName="roles"
            label="Roles"
            Input={({ value, onChange, ...props }) => (
              <Select
                {...props}
                value={value[0]}
                onValueChange={(val) => onChange([val as UserRole])}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select a role" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={UserRole.Default}>USER</SelectItem>
                  <SelectItem value={UserRole.Admin}>ADMIN</SelectItem>
                </SelectContent>
              </Select>
            )}
          />
          {!isEdit && (
            <FormField
              control={control}
              fieldName="password"
              label="Password"
              Input={(props) => (
                <Input {...props} type="password" placeholder="••••••••" />
              )}
            />
          )}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Saving..." : "Save"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
