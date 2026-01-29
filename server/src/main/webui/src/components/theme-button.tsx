import {Button} from "@/components/ui/button.tsx";
import {isDarkMode, toggleDarkMode} from "@/lib/theme.ts";
import {useState} from "react";
import {Sun} from "react-bootstrap-icons";
import {Moon} from "lucide-react";

export function ThemeButton() {
    const [isDark, setDark] = useState<boolean>(isDarkMode())

    function toggle() {
        toggleDarkMode()
        setDark(!isDark)
    }

    return (
        <Button variant={"ghost"}
                size={"icon"}
                className={"fixed bottom-5 right-5"}
                onClick={toggle}>
            {isDark ? <Sun/> : <Moon/>}
        </Button>
    )
}