import {Link, NavLink, Outlet, useNavigate} from "react-router";
import {SidebarProvider} from "@/components/ui/sidebar.tsx";
import {Box, LogOut, Settings, User} from "lucide-react";
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
import {ThemeToggle} from "@/components/theme-toggle.tsx";

export default function MainLayout() {
    const {user, loading, logout} = useAuth()
    const navigate = useNavigate()

    return (
        <SidebarProvider>
            <div className="flex flex-col w-full h-screen bg-background overflow-hidden">
                {/* Header */}
                <header className="flex h-16 items-center justify-between border-b px-6 shrink-0">
                    <Link to="/" className="flex items-center gap-4">
                        <div
                            className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                            <Box className="size-5"/>
                        </div>
                        <span className="text-lg font-bold tracking-tight text-foreground">Repository</span>
                    </Link>

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

                <main className="flex-1 min-h-0 overflow-hidden">
                    <div className="h-full overflow-y-auto">
                        <Outlet/>
                        <ThemeToggle/>
                    </div>
                </main>
            </div>
        </SidebarProvider>
    )
}
