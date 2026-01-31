import {NavLink, Outlet, useNavigate} from "react-router";
import {recordPath} from "@/lib/path-restore.ts";
import i18next from "i18next";
import {SidebarProvider} from "@/components/ui/sidebar.tsx";
import {Box, LogOut, Settings, User} from "lucide-react";
import {Button} from "@/components/ui/button.tsx";
import {useAuth} from "@/lib/auth.tsx";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger
} from "@/components/ui/dropdown-menu.tsx";
import {ThemeButton} from "@/components/theme-button.tsx";

export function mainLayoutInit() {
    i18next.addResourceBundle("en", "layout.main", {
        "login": "Login",
        "logout": "Logout",
        "account": "Account",
        "settings": "Settings"
    })
}

function t(key: string) {
    return i18next.t(key, {ns: "layout.main"})
}

export default function MainLayout() {
    recordPath()

    const {user, pending, logout} = useAuth()
    const navigate = useNavigate()

    return (
        <SidebarProvider>
            <div className="flex flex-col w-full h-full min-h-screen bg-background">
                {/* Header */}
                <header className="flex h-16 items-center justify-between border-b px-6 shrink-0">
                    <NavLink to="/" className="flex items-center gap-4">
                        <div
                            className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                            <Box className="size-5"/>
                        </div>
                        <span className="text-lg font-bold tracking-tight">Repository</span>
                    </NavLink>

                    <div className="flex items-center gap-4">
                        {(!pending && user) && (
                            <DropdownMenu>
                                <DropdownMenuTrigger asChild>
                                    <div
                                        className="flex items-center gap-3 pl-1 cursor-pointer hover:bg-accent p-1.5 rounded-lg transition-colors">
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
                                    <DropdownMenuLabel>{t("account")}</DropdownMenuLabel>
                                    <DropdownMenuSeparator/>
                                    <DropdownMenuItem onClick={() => navigate("/settings/user")} className="cursor-pointer">
                                        <Settings className="mr-2 h-4 w-4"/>
                                        <span>{t("settings")}</span>
                                    </DropdownMenuItem>
                                    <DropdownMenuSeparator/>
                                    <DropdownMenuItem onClick={() => logout()} variant="destructive" className="cursor-pointer">
                                        <LogOut className="mr-2 h-4 w-4"/>
                                        <span>{t("logout")}</span>
                                    </DropdownMenuItem>
                                </DropdownMenuContent>
                            </DropdownMenu>
                        )}
                        {(!pending && !user) && (
                            <NavLink to={"/login"}>
                                <Button>
                                    <User/>
                                    {t("login")}
                                </Button>
                            </NavLink>
                        )}
                    </div>
                </header>

                <main className="flex-1 relative min-h-0 overflow-hidden">
                    <div className={"overflow-y-auto h-full max-h-full"}>
                        <Outlet/>
                        <ThemeButton/>
                    </div>
                </main>
            </div>
        </SidebarProvider>
    )
}