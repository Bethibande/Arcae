import { useState } from "react";
import { Input } from "@/components/ui/input.tsx";
import { Button } from "@/components/ui/button.tsx";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog.tsx";
import { Eye, EyeOff } from "lucide-react";
import { cn } from "@/lib/utils.ts";
import { getErrorMessage } from "@/lib/errors.ts";

interface PasswordConfirmDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onConfirm: (password: string) => Promise<void>;
    loading: boolean;
}

export function PasswordConfirmDialog({ open, onOpenChange, onConfirm, loading }: PasswordConfirmDialogProps) {
    const [password, setPassword] = useState("");
    const [show, setShow] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleConfirm = async () => {
        setError(null);
        try {
            await onConfirm(password);
        } catch (e) {
            if (e && typeof e === "object" && "response" in e && (e.response as Response).status === 403) {
                setError("Wrong password. Please try again.");
            } else {
                const message = await getErrorMessage(e);
                setError(message);
            }
        }
    };

    const handleOpenChange = (v: boolean) => {
        if (!v) {
            setPassword("");
            setShow(false);
            setError(null);
        }
        onOpenChange(v);
    };

    return (
        <Dialog open={open} onOpenChange={handleOpenChange}>
            <DialogContent>
                <DialogHeader>
                    <DialogTitle>Confirm your password</DialogTitle>
                    <DialogDescription>
                        Please enter your current password to continue.
                    </DialogDescription>
                </DialogHeader>
                <div className="space-y-4">
                    <div className="relative">
                        <Input
                            type={show ? "text" : "password"}
                            placeholder="Current password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            onKeyDown={(e) => e.key === "Enter" && handleConfirm()}
                            className={cn("pr-10", error && "border-destructive")}
                        />
                        <button
                            type="button"
                            className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                            onClick={() => setShow((s) => !s)}
                        >
                            {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                        </button>
                    </div>
                    {error && <p className="text-sm font-medium text-destructive">{error}</p>}
                </div>
                <DialogFooter>
                    <Button variant="outline" onClick={() => handleOpenChange(false)} disabled={loading}>
                        Cancel
                    </Button>
                    <Button onClick={handleConfirm} disabled={loading || !password}>
                        {loading ? "Confirming..." : "Confirm"}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
