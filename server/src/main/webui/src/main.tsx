import {createRoot} from "react-dom/client"
import {BrowserRouter, Navigate, Route, Routes, useParams} from "react-router"

import "./index.css"
import {ThemeProvider} from "@/components/theme-provider.tsx"
import {AuthProvider} from "@/components/auth-provider.tsx"
import {ProtectedRoute} from "@/components/auth/protected-route.tsx"
import MainLayout from "@/components/layout/main-layout.tsx"
import LoginPage from "@/pages/login/page.tsx"
import PasswordResetPage from "@/pages/login/password-reset.tsx"
import DashboardPage from "@/pages/dashboard/page.tsx"
import RepositoryBrowsePage from "@/pages/repository/browse/page.tsx"
import ArtifactPage from "@/pages/artifact/page.tsx"
import OidcCompletePage from "@/pages/login/oidc-complete.tsx"
import {TooltipProvider} from "@/components/ui/tooltip.tsx";
import RepositorySettingsPage from "@/pages/repository/settings/page.tsx";
import UserSettingsLayout from "@/pages/settings/page.tsx";
import {ProfileTab} from "@/pages/settings/profile-tab.tsx";
import {PasswordTab} from "@/pages/settings/password-tab.tsx";
import {TokensTab} from "@/pages/settings/tokens-tab.tsx";
import {UserManagementTab} from "@/pages/settings/user-management-tab.tsx";
import {SystemJobsTab} from "@/pages/settings/system-jobs-tab.tsx";
import {MailTab} from "@/pages/settings/mail-tab.tsx";
import {ReferencesTab} from "@/pages/settings/references-tab.tsx";
import {OidcProvidersTab} from "@/pages/settings/oidc-providers-tab.tsx";
import ReferencePage from "@/pages/reference-page.tsx";
import {Toaster} from "@/components/ui/sonner.tsx";

// eslint-disable-next-line react-refresh/only-export-components
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
        case "references":
            return <ReferencesTab />;
        case "oidc":
            return <OidcProvidersTab />;
        default:
            return <Navigate to="/settings/profile" replace />;
    }
}

createRoot(document.getElementById("root")!).render(
    <BrowserRouter>
      <ThemeProvider>
        <TooltipProvider>
          <AuthProvider>
            <Toaster/>
            <Routes>
              <Route element={<MainLayout />}>
                <Route path="/" element={<DashboardPage />} />
                <Route path="/ref/:label" element={<ReferencePage />} />
                <Route path="/repository/:id/browse" element={<RepositoryBrowsePage />} />
                <Route path="/artifact/:id" element={<ArtifactPage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/login/reset" element={<PasswordResetPage />} />
                <Route path="/login/oidc/complete/:provider" element={<OidcCompletePage />} />
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
)
