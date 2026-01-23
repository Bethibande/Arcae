import {DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger} from "@/components/ui/dropdown-menu.tsx";
import {SidebarMenuButton, useSidebar} from "@/components/ui/sidebar.tsx";
import {BrightnessHighFill, ChevronExpand, DoorClosed, MoonStarsFill, Person} from "react-bootstrap-icons";
import i18next from "i18next";
import {useAuth} from "@/lib/auth.tsx";
import {useNavigate} from "react-router";
import {isDarkMode, toggleDarkMode} from "@/lib/theme.ts";
import {useState} from "react";

export default function UserItem() {
    const {isMobile} = useSidebar();

    const {user, logout} = useAuth();
    const navigate = useNavigate();

    const [darkMode, setDarkMode] = useState<boolean>(isDarkMode());
    function toggleTheme() {
        setDarkMode(!darkMode);
        toggleDarkMode();
    }

    return (
        <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <SidebarMenuButton
                    size={"lg"}
                    className={"data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"}
                >
                    <Person/>
                    <div className={"grid flex-1 text-left text-sm leading-tight"}>
                        <span className={"truncate font-medium"}>{user?.name}</span>
                    </div>
                    <ChevronExpand className="ml-auto" />
                </SidebarMenuButton>
            </DropdownMenuTrigger>
            <DropdownMenuContent
                className="w-(--radix-dropdown-menu-trigger-width) min-w-56 rounded-lg"
                align="start"
                side={isMobile ? "bottom" : "right"}
                sideOffset={4}>
                <DropdownMenuItem onClick={() => toggleTheme()}
                                  className="gap-2 p-2">
                    {darkMode
                        ? (<BrightnessHighFill className="size-4 text-popover-foreground"/>)
                        : (<MoonStarsFill className="size-4 text-popover-foreground"/>)}
                    <p className={"font-medium"}>{darkMode ? i18next.t("theme.light") : i18next.t("theme.dark")}</p>
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => logout().then(() => navigate("/login"))}
                                  className="gap-2 p-2"
                                  variant={"destructive"}>
                    <DoorClosed className="size-4"/>
                    <p className="font-medium"> {i18next.t("logout")}</p>
                </DropdownMenuItem>
            </DropdownMenuContent>
        </DropdownMenu>
    )
}