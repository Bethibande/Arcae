import { useNavigate, useParams, Link, Outlet } from "react-router";
import { cn } from "@/lib/utils.ts";
import {
    Mail,
    ChevronRight,
    KeyRound,
    Lock,
    User,
    Shield,
    Settings,
} from "lucide-react";
import { useAuth } from "@/components/auth-provider.tsx";
import { UserRole } from "@/generated";

type Tab = "profile" | "password" | "tokens" | "users" | "jobs" | "mail";

export default function UserSettingsLayout() {
    const navigate = useNavigate();
    const { tab: activeTab = "profile" } = useParams<{ tab: Tab }>();
    const { user } = useAuth();

    const isAdmin = user?.roles?.includes(UserRole.Admin);

    const userTabs: { id: Tab; label: string; description: string; icon: React.ElementType }[] = [
        { id: "profile", label: "Profile", description: "Manage your account details", icon: User },
        { id: "password", label: "Password", description: "Secure your account", icon: Lock },
        { id: "tokens", label: "Access Tokens", description: "API and CLI authentication", icon: KeyRound },
    ];

    const adminTabs: { id: Tab; label: string; description: string; icon: React.ElementType }[] = [
        { id: "users", label: "User Management", description: "Manage system users and roles", icon: Shield },
        { id: "mail", label: "Mail Settings", description: "Configure SMTP settings", icon: Mail },
        { id: "jobs", label: "System Jobs", description: "Monitor background tasks", icon: Settings },
    ];

    return (
        <div className="text-foreground h-full flex flex-col overflow-hidden">
            {/* Header */}
            <header className="flex items-center justify-between px-8 py-4 border-b bg-background z-20 shrink-0">
                <div className="flex items-center gap-2 text-sm">
                    <span
                        className="text-muted-foreground cursor-pointer hover:text-foreground"
                        onClick={() => navigate("/")}
                    >
                        Home
                    </span>
                    <ChevronRight className="size-4 text-muted-foreground" />
                    <span className="font-medium">Settings</span>
                </div>
            </header>

            <div className="flex flex-1 overflow-hidden">
                {/* Sidebar */}
                <aside className="w-80 max-h-full border-r p-6 space-y-8 overflow-y-auto">
                    <nav className="space-y-1">
                        {userTabs.map((tab) => {
                            const Icon = tab.icon;
                            return (
                                <Link
                                    key={tab.id}
                                    to={`/settings/${tab.id}`}
                                    className={cn(
                                        "w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-all group",
                                        activeTab === tab.id
                                            ? "bg-primary text-primary-foreground shadow"
                                            : "text-muted-foreground hover:text-foreground hover:bg-accent"
                                    )}
                                >
                                    <Icon
                                        className={cn(
                                            "size-4 shrink-0",
                                            activeTab === tab.id
                                                ? "text-primary-foreground"
                                                : "text-muted-foreground group-hover:text-foreground"
                                        )}
                                    />
                                    <div className="flex flex-col items-start overflow-hidden">
                                        <span className="truncate">{tab.label}</span>
                                        <span className={cn(
                                            "text-[10px] font-normal truncate w-full",
                                            activeTab === tab.id ? "text-primary-foreground/70" : "text-muted-foreground/70"
                                        )}>
                                            {tab.description}
                                        </span>
                                    </div>
                                </Link>
                            );
                        })}

                        {isAdmin && adminTabs.map((tab) => {
                            const Icon = tab.icon;
                            return (
                                <Link
                                    key={tab.id}
                                    to={`/settings/${tab.id}`}
                                    className={cn(
                                        "w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-all group",
                                        activeTab === tab.id
                                            ? "bg-primary text-primary-foreground shadow"
                                            : "text-muted-foreground hover:text-foreground hover:bg-accent"
                                    )}
                                >
                                    <Icon
                                        className={cn(
                                            "size-4 shrink-0",
                                            activeTab === tab.id
                                                ? "text-primary-foreground"
                                                : "text-muted-foreground group-hover:text-foreground"
                                        )}
                                    />
                                    <div className="flex flex-col items-start overflow-hidden">
                                        <span className="truncate">{tab.label}</span>
                                        <span className={cn(
                                            "text-[10px] font-normal truncate w-full",
                                            activeTab === tab.id ? "text-primary-foreground/70" : "text-muted-foreground/70"
                                        )}>
                                            {tab.description}
                                        </span>
                                    </div>
                                </Link>
                            );
                        })}
                    </nav>
                </aside>

                {/* Main Content */}
                <main className="flex-1 flex justify-center overflow-y-auto p-12">
                    <div className={cn(
                        "w-full space-y-6 h-fit",
                        activeTab === "jobs" ? "max-w-7xl" : "max-w-2xl"
                    )}>
                        <Outlet />
                    </div>
                </main>
            </div>
        </div>
    );
}
