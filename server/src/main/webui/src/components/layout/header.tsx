import {Link, NavLink, useNavigate} from "react-router";
import {Box, ExternalLink, LogOut, Settings, User} from "lucide-react";
import {Button} from "@/components/ui/button.tsx";
import {useAuth} from "@/components/auth-provider.tsx";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger
} from "@/components/ui/dropdown-menu.tsx";
import {useSystem} from "@/components/system-provider.tsx";
import {SystemReferenceType} from "@/generated";

export function Header() {
    const {user, loading, logout} = useAuth();
    const {references} = useSystem();
    const navigate = useNavigate();

    return (
        <header className="flex h-16 items-center justify-between border-b px-6 shrink-0">
            <div className="flex items-center gap-8">
                <Link to="/" className="flex items-center gap-4 mr-4">
                    <div
                        className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                        <Box className="size-5"/>
                    </div>
                    <span className="text-lg font-bold tracking-tight text-foreground">Arcae</span>
                </Link>

                <nav className="hidden md:flex items-center gap-6">
                    {references.map((ref, index) => {
                        const isExternal = ref.type === SystemReferenceType.Url;
                        const href = isExternal ? ref.value : `/ref/${encodeURIComponent(ref.label)}`;

                        if (isExternal) {
                            return (
                                <a
                                    key={index}
                                    href={href}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="text-sm font-medium text-muted-foreground hover:text-foreground transition-colors flex items-center gap-1"
                                >
                                    {ref.label}
                                    <ExternalLink className="size-3" />
                                </a>
                            );
                        }

                        return (
                            <Link
                                key={index}
                                to={href}
                                className="text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
                            >
                                {ref.label}
                            </Link>
                        );
                    })}
                </nav>
            </div>

            <div className="flex items-center gap-4">
                {(!loading && user) && (
                    <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                            <div
                                className="flex items-center gap-3 pl-1 cursor-pointer hover:bg-accent p-1.5 rounded-lg transition-colors text-foreground">
                                <div className="flex flex-col items-end">
                                    <span className="text-sm font-semibold leading-none">{user.name}</span>
                                </div>
                                <div
                                    className="size-9 rounded-full bg-linear-to-br from-primary/20 to-primary/10 border flex items-center justify-center overflow-hidden">
                                    <User className="size-5 text-primary"/>
                                </div>
                            </div>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-56">
                            <DropdownMenuLabel>Account</DropdownMenuLabel>
                            <DropdownMenuSeparator/>
                            <DropdownMenuItem onClick={() => navigate("/settings/user")} className="cursor-pointer">
                                <Settings className="mr-2 h-4 w-4"/>
                                <span>Settings</span>
                            </DropdownMenuItem>
                            <DropdownMenuSeparator/>
                            <DropdownMenuItem onClick={() => logout()} variant="destructive" className="cursor-pointer text-destructive focus:text-destructive-foreground focus:bg-destructive">
                                <LogOut className="mr-2 h-4 w-4"/>
                                <span>Logout</span>
                            </DropdownMenuItem>
                        </DropdownMenuContent>
                    </DropdownMenu>
                )}
                {(!loading && !user) && (
                    <NavLink to={"/login"}>
                        <Button>
                            <User className="mr-2 h-4 w-4"/>
                            Login
                        </Button>
                    </NavLink>
                )}
            </div>
        </header>
    );
}
