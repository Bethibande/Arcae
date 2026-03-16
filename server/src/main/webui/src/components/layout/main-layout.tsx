import {Outlet} from "react-router";
import {SidebarProvider} from "@/components/ui/sidebar.tsx";
import {ThemeToggle} from "@/components/theme-toggle.tsx";
import {SystemProvider} from "@/components/system-provider.tsx";
import {Header} from "@/components/layout/header.tsx";

export default function MainLayout() {
    return (
        <SystemProvider>
            <SidebarProvider>
                <div className="flex flex-col w-full h-screen bg-background overflow-hidden">
                    <Header />

                    <main className="flex-1 min-h-0 overflow-hidden">
                        <div className="h-full overflow-y-auto">
                            <Outlet/>
                            <ThemeToggle/>
                        </div>
                    </main>
                </div>
            </SidebarProvider>
        </SystemProvider>
    )
}
