import './App.css'
import {createBrowserRouter, type RouteObject, RouterProvider} from "react-router";
import ErrorLayout from "@/view/layouts/ErrorLayout.tsx";
import MainLayout from "@/view/layouts/MainLayout.tsx";
import LoginView from "@/view/LoginView.tsx";
import {ViewConfigProvider} from "@/lib/view-config.tsx";
import {AuthProvider} from "@/lib/auth.tsx";
import {SetupUserView} from "@/view/setup/SetupUser.tsx";
import DashboardView from "@/view/dashboard/DashboardView.tsx";
import RepositoryEditView from "@/view/dashboard/RepositoryEditView.tsx";
import RepositoryBrowseView from "@/view/dashboard/RepositoryBrowseView.tsx";

function App() {
    const primaryRoutes: RouteObject[] = [
        {
            index: true,
            Component: DashboardView
        },
        {
            path: "/repositories/new",
            Component: RepositoryEditView
        },
        {
            path: "/repositories/:id/edit",
            Component: RepositoryEditView
        },
        {
            path: "/repositories/:id/browse",
            Component: RepositoryBrowseView
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
            <ViewConfigProvider>
                <AuthProvider>
                    <RouterProvider router={router}>
                    </RouterProvider>
                </AuthProvider>
            </ViewConfigProvider>
        </div>
    )
}

export default App
