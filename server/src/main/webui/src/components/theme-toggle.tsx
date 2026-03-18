import {Button} from "@/components/ui/button.tsx";
import {useTheme} from "@/components/theme-provider.tsx";
import {Moon, Sun} from "lucide-react";

export function ThemeToggle() {
    const {theme, setTheme} = useTheme();

    function toggle() {
        setTheme(theme === "dark" ? "light" : "dark");
    }

    return (
        <Button variant={"ghost"}
                size={"icon"}
                className={"fixed bottom-5 right-5 z-10"}
                onClick={toggle}>
            {theme === "dark" ? <Sun className="h-4 w-4"/> : <Moon className="h-4 w-4"/>}
        </Button>
    )
}
