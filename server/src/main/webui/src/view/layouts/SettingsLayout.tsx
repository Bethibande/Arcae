import {NavLink, Outlet} from "react-router";
import {
    Sidebar,
    SidebarContent,
    SidebarGroup,
    SidebarGroupContent,
    SidebarGroupLabel,
    SidebarMenu,
    SidebarMenuButton,
    SidebarMenuItem,
    SidebarRail,
} from "@/components/ui/sidebar.tsx";
import {Key, Play, ShieldCheck, User} from "lucide-react";
import i18next from "i18next";
import {useAuth} from "@/lib/auth.tsx";
import {UserRole} from "@/generated";

export function settingsLayoutInit() {
    i18next.addResourceBundle("en", "layout.settings", {
        "title": "Settings",
        "user": "User Settings",
        "tokens": "Access Tokens",
        "management": "User Management",
        "jobs": "System Jobs"
    })
}

function t(key: string) {
    return i18next.t(key, {ns: "layout.settings"})
}

export default function SettingsLayout() {
    const {user} = useAuth();

    const hasRole = (role: UserRole) => {
        return user?.roles?.includes(role);
    };

    return (
        <div className="flex h-full w-full">
            <Sidebar variant="sidebar" className="top-16 border-r h-[calc(100vh-4rem)]">
                <SidebarContent>
                    <SidebarGroup>
                        <SidebarGroupLabel>{t("title")}</SidebarGroupLabel>
                        <SidebarGroupContent>
                            <SidebarMenu>
                                <SidebarMenuItem>
                                    <NavLink to="/settings/user" className="w-full">
                                        {({isActive}) => (
                                            <SidebarMenuButton isActive={isActive}>
                                                <User />
                                                <span>{t("user")}</span>
                                            </SidebarMenuButton>
                                        )}
                                    </NavLink>
                                </SidebarMenuItem>
                                <SidebarMenuItem>
                                    <NavLink to="/settings/tokens" className="w-full">
                                        {({isActive}) => (
                                            <SidebarMenuButton isActive={isActive}>
                                                <Key />
                                                <span>{t("tokens")}</span>
                                            </SidebarMenuButton>
                                        )}
                                    </NavLink>
                                </SidebarMenuItem>
                                {hasRole(UserRole.Admin) && (
                                    <>
                                        <SidebarMenuItem>
                                            <NavLink to="/settings/users" className="w-full">
                                                {({isActive}) => (
                                                    <SidebarMenuButton isActive={isActive}>
                                                        <ShieldCheck />
                                                        <span>{t("management")}</span>
                                                    </SidebarMenuButton>
                                                )}
                                            </NavLink>
                                        </SidebarMenuItem>
                                        <SidebarMenuItem>
                                            <NavLink to="/settings/jobs" className="w-full">
                                                {({isActive}) => (
                                                    <SidebarMenuButton isActive={isActive}>
                                                        <Play />
                                                        <span>{t("jobs")}</span>
                                                    </SidebarMenuButton>
                                                )}
                                            </NavLink>
                                        </SidebarMenuItem>
                                    </>
                                )}
                            </SidebarMenu>
                        </SidebarGroupContent>
                    </SidebarGroup>
                </SidebarContent>
                <SidebarRail />
            </Sidebar>
            <main className="flex-1 overflow-y-auto">
                <div className="max-w-5xl mx-auto">
                    <Outlet/>
                </div>
            </main>
        </div>
    )
}
