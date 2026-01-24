import {SidebarProvider, SidebarTrigger} from "@/components/ui/sidebar.tsx";
import {Outlet, useNavigate} from "react-router";
import {useViewConfig} from "@/lib/view-config.tsx";
import {useAuth} from "@/lib/auth.tsx";
import {AppSidebar} from "@/components/navigation/app-sidebar.tsx";
import {recordPath} from "@/lib/path-restore.ts";

function MainLayoutToolbar() {
    const {viewConfig} = useViewConfig();

    return (
        <div className={"w-full p-3 bg-sidebar border-b flex gap-2 items-center h-16"}>
            <SidebarTrigger className={"size-10"}/>
            {viewConfig.toolbar}
        </div>
    )
}

export default function MainLayout() {
    const {user, pending} = useAuth();
    const navigate = useNavigate();

    if (!user && !pending) {
        navigate("/login");
    }

    recordPath()

    return (
        <SidebarProvider>
            <AppSidebar/>
            <main className={"w-full flex flex-col overflow-hidden"}>
                <MainLayoutToolbar/>
                <div className={"w-full h-full p-5 overflow-y-auto"}>
                    <Outlet/>
                </div>
            </main>
        </SidebarProvider>
    )
}