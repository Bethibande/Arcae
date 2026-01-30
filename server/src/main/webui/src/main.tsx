import {StrictMode} from 'react'
import {createRoot} from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import i18next from "i18next";
import {LoginViewTranslationsEN} from "@/view/LoginView.tsx";
import {Toaster} from "@/components/ui/sonner.tsx";
import {SetupUserTranslationsEN} from "@/view/setup/SetupUser.tsx";
import {restoreTheme} from "@/lib/theme.ts";
import {mainLayoutInit} from "@/view/layouts/MainLayout.tsx";
import {settingsLayoutInit} from "@/view/layouts/SettingsLayout.tsx";

i18next.init({
    fallbackLng: "en"
})

i18next.addResourceBundle("en", "views.login", LoginViewTranslationsEN)
i18next.addResourceBundle("en", "views.onboarding.user", SetupUserTranslationsEN)

mainLayoutInit()
settingsLayoutInit()

restoreTheme()

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <App/>
        <Toaster/>
    </StrictMode>,
)
