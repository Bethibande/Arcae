import './App.css'
import {createBrowserRouter, type RouteObject, RouterProvider} from "react-router";
import ErrorLayout from "@/view/layouts/ErrorLayout.tsx";
import MainLayout from "@/view/layouts/MainLayout.tsx";
import SettingsLayout from "@/view/layouts/SettingsLayout.tsx";
import LoginView from "@/view/LoginView.tsx";
import {AuthProvider} from "@/lib/auth.tsx";
import {SetupUserView} from "@/view/setup/SetupUser.tsx";
import DashboardView from "@/view/dashboard/DashboardView.tsx";
import RepositoryEditView from "@/view/dashboard/RepositoryEditView.tsx";
import RepositoryBrowseView from "@/view/dashboard/RepositoryBrowseView.tsx";
import ArtifactView from "@/view/dashboard/ArtifactView.tsx";
import UserSettingsView from "@/view/settings/UserSettingsView.tsx";
import AccessTokensView from "@/view/settings/AccessTokensView.tsx";
import UserManagementView from "@/view/settings/UserManagementView.tsx";
import SystemJobsView from "@/view/settings/SystemJobsView.tsx";

function App() {
    const primaryRoutes: RouteObject[] = [
        {
            index: true,
            Component: DashboardView
        },
        {
            path: "/repository/new",
            Component: RepositoryEditView
        },
        {
            path: "/repository/:id/edit",
            Component: RepositoryEditView
        },
        {
            path: "/repository/:id/browse",
            Component: RepositoryBrowseView
        },
        {
            path: "/artifacts/:id",
            Component: ArtifactView
        },
        {
            path: "/settings",
            Component: SettingsLayout,
            children: [
                {
                    path: "user",
                    Component: UserSettingsView
                },
                {
                    path: "tokens",
                    Component: AccessTokensView
                },
                {
                    path: "users",
                    Component: UserManagementView
                },
                {
                    path: "jobs",
                    Component: SystemJobsView
                }
            ]
        }
    ]
    const secondaryRoutes: RouteObject[] = [
        {
            path: "/login",
            Component: LoginView
        },
        {
            path: "/setup",
            Component: SetupUserView
        }
    ]

    const router = createBrowserRouter([{
        children: [
            {
                children: primaryRoutes,
                Component: MainLayout
            },
            ...secondaryRoutes,
        ],
        ErrorBoundary: ErrorLayout
    }])

    return (
        <div className={"bg-muted w-full h-full flex"}>
                <AuthProvider>
                    <RouterProvider router={router}>
                    </RouterProvider>
                </AuthProvider>
        </div>
    )
}

export default App
