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
import UserSettingsLayout from "@/pages/settings/user/page.tsx";
import { ProfileTab } from "@/pages/settings/user/profile-tab.tsx";
import { PasswordTab } from "@/pages/settings/user/password-tab.tsx";
import { TokensTab } from "@/pages/settings/user/tokens-tab.tsx";
import { UserManagementTab } from "@/pages/settings/user/user-management-tab.tsx";
import { SystemJobsTab } from "@/pages/settings/user/system-jobs-tab.tsx";
import { MailTab } from "@/pages/settings/user/mail-tab.tsx";
import {Toaster} from "@/components/ui/sonner.tsx";
import { Navigate, useParams } from "react-router";

function SettingsTabWrapper() {
    const { tab } = useParams();

    switch (tab) {
        case "profile":
            return <ProfileTab />;
        case "password":
            return <PasswordTab />;
        case "tokens":
            return <TokensTab />;
        case "users":
            return <UserManagementTab />;
        case "jobs":
            return <SystemJobsTab />;
        case "mail":
            return <MailTab />;
        default:
            return <Navigate to="/settings/profile" replace />;
    }
}

// eslint-disable-next-line react-refresh/only-export-components
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
                  <Route path="/settings" element={<UserSettingsLayout />}>
                    <Route index element={<Navigate to="profile" replace />} />
                    <Route path=":tab" element={<SettingsTabWrapper />} />
                  </Route>
                </Route>
              </Route>
            </Routes>
          </AuthProvider>
        </TooltipProvider>
      </ThemeProvider>
    </BrowserRouter>
  </StrictMode>
)
