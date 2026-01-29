import {NavLink, Outlet} from "react-router";
import {recordPath} from "@/lib/path-restore.ts";
import i18next from "i18next";
import {SidebarProvider} from "@/components/ui/sidebar.tsx";
import {Box, Settings, User} from "lucide-react";
import {Button} from "@/components/ui/button.tsx";
import {Separator} from "@/components/ui/separator.tsx";
import {ThemeButton} from "@/components/theme-button.tsx";
import {useAuth} from "@/lib/auth.tsx";

export function mainLayoutInit() {
    i18next.addResourceBundle("en", "layout.main", {
        "login": "Login"
    })
}

function t(key: string) {
    return i18next.t(key, {ns: "layout.main"})
}

export default function MainLayout() {
    recordPath()

    const {user, pending} = useAuth()

    return (
        <SidebarProvider>
            <div className="flex flex-col w-full h-full min-h-screen bg-background">
                {/* Header */}
                <header className="flex h-16 items-center justify-between border-b px-6 shrink-0">
                    <div className="flex items-center gap-4">
                        <div
                            className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                            <Box className="size-5"/>
                        </div>
                        <span className="text-lg font-bold tracking-tight">Repository</span>
                    </div>

                    <div className="flex items-center gap-4">
                        {(!pending && user) && (
                            <>
                                <div className="flex items-center gap-1">
                                    <Button variant="ghost" size="icon-sm" className="text-muted-foreground">
                                        <Settings className="size-4"/>
                                    </Button>
                                </div>

                                <Separator orientation="vertical" className="h-8"/>

                                <div className="flex items-center gap-3 pl-1">
                                    <div className="flex flex-col items-end">
                                        <span className="text-sm font-semibold leading-none">{user.name}</span>
                                    </div>
                                    <div
                                        className="size-9 rounded-full bg-muted border flex items-center justify-center overflow-hidden">
                                        <div className="size-full bg-orange-200"/>
                                    </div>
                                </div>
                            </>
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

                {/* Main Content */}
                <main className="flex-1 relative overflow-y-auto">
                    <Outlet/>
                    <ThemeButton/>
                </main>
            </div>
        </SidebarProvider>
    )
}