import {StrictMode} from "react"
import {createRoot} from "react-dom/client"
import {BrowserRouter, Route, Routes} from "react-router"

import "./index.css"
import {ThemeProvider} from "@/components/theme-provider.tsx"
import {AuthProvider} from "@/components/auth-provider.tsx"
import {ProtectedRoute} from "@/components/auth/protected-route.tsx"
import MainLayout from "@/components/layout/main-layout.tsx"
import LoginPage from "@/pages/login/page.tsx"
import DashboardPage from "@/pages/dashboard/page.tsx"
import RepositoryBrowsePage from "@/pages/repository/browse/page.tsx"
import ArtifactPage from "@/pages/artifact/page.tsx"
import {TooltipProvider} from "@/components/ui/tooltip.tsx";
import RepositorySettingsPage from "@/pages/repository/settings/page.tsx";
import {Toaster} from "@/components/ui/sonner.tsx";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <ThemeProvider>
        <TooltipProvider>
          <AuthProvider>
            <Toaster/>
            <Routes>
              <Route element={<MainLayout />}>
                <Route path="/" element={<DashboardPage />} />
                <Route path="/repository/:id/browse" element={<RepositoryBrowsePage />} />
                <Route path="/artifact/:id" element={<ArtifactPage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route element={<ProtectedRoute />}>
                  <Route path="/repository/new" element={<RepositorySettingsPage />} />
                  <Route path="/repository/:id/settings" element={<RepositorySettingsPage />} />
                  {/* Add protected routes here later if needed */}
                </Route>
              </Route>
            </Routes>
          </AuthProvider>
        </TooltipProvider>
      </ThemeProvider>
    </BrowserRouter>
  </StrictMode>
)
