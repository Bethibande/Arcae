import {StrictMode} from 'react'
import {createRoot} from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import i18next from "i18next";
import {LoginViewTranslationsEN} from "@/view/LoginView.tsx";
import {Toaster} from "@/components/ui/sonner.tsx";

i18next.init({
    fallbackLng: "en"
})

i18next.addResourceBundle("en", "views.login", LoginViewTranslationsEN)

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <App/>
        <Toaster/>
    </StrictMode>,
)
