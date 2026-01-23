import './App.css'
import {createBrowserRouter, RouterProvider, type RouteObject} from "react-router";
import ErrorLayout from "@/view/layouts/ErrorLayout.tsx";
import MainLayout from "@/view/layouts/MainLayout.tsx";
import LoginView from "@/view/LoginView.tsx";
import {ViewConfigProvider} from "@/lib/view-config.tsx";
import {AuthProvider} from "@/lib/auth.tsx";

function App() {
    const primaryRoutes: RouteObject[] = []
    const secondaryRoutes: RouteObject[] = [
        {
            path: "/login",
            Component: LoginView
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
