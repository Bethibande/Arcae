import {Outlet} from "react-router";
import {recordPath} from "@/lib/path-restore.ts";
import i18next from "i18next";

export function mainLayoutInit() {
    i18next.addResourceBundle("en", "layout.main", {
    })
}

/*function t(key: string) {
    return i18next.t(key, {ns: "layout.main"})
}*/

export default function MainLayout() {
    recordPath()

    return (
        <main className={"w-full h-full"}>
            <Outlet/>
        </main>
    )
}