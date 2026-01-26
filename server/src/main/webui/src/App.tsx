import './App.css'
import {createBrowserRouter, RouterProvider, type RouteObject} from "react-router";
import ErrorLayout from "@/view/layouts/ErrorLayout.tsx";
import MainLayout from "@/view/layouts/MainLayout.tsx";
import LoginView from "@/view/LoginView.tsx";
import {ViewConfigProvider} from "@/lib/view-config.tsx";
import {AuthProvider} from "@/lib/auth.tsx";
import {SetupUserView} from "@/view/setup/SetupUser.tsx";
import {ThemeButton} from "@/components/theme-button.tsx";
import {DefaultView} from "@/view/overview/DefaultView.tsx";

function App() {
    const primaryRoutes: RouteObject[] = [
        {
            path: "/",
            Component: DefaultView
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
            <ThemeButton/>
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
