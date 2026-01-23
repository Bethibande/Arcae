import {NavLink, useRouteError} from "react-router";
import {Button} from "@/components/ui/button.tsx";
import {HouseFill} from "react-bootstrap-icons";

export default function ErrorLayout() {
    const error: any = useRouteError()

    // TODO: i18n
    return (
        <div className={"w-full h-full flex flex-col items-center justify-center"}>
            <h1>Error {error.status}</h1>
            <p>{error.statusText}</p>
            <div className={"mt-4"}>
                <NavLink to={"/"}>
                    <Button variant={"link"}><HouseFill/>&nbsp;Go to home</Button>
                </NavLink>
            </div>
        </div>
    )
}