import {Clock, CupHot, ThreeDotsVertical} from "react-bootstrap-icons";
import {Button} from "@/components/ui/button.tsx";
import {Separator} from "@/components/ui/separator.tsx";
import {cn} from "@/lib/utils.ts";
import i18next from "i18next";

export const RepositoryStatus = {
    ACTIVE: "active",
    MAINTENANCE: "maintenance"
}
export type RepositoryStatus = keyof typeof RepositoryStatus;

function t(key: string) {
    return i18next.t(key, {ns: "components.repository-card"})
}

function StatusIndicator(props: {status: RepositoryStatus}) {
    let colorForeground: string = "";
    let colorBackground: string = "";
    let colorBackgroundPrimary: string = "";
    switch (props.status) {
        case RepositoryStatus.MAINTENANCE:
            colorForeground = "text-yellow-600";
            colorBackground = "bg-yellow-600/20";
            colorBackgroundPrimary = "bg-yellow-600";
            break;
        case RepositoryStatus.ACTIVE:
            colorForeground = "text-green-600";
            colorBackground = "bg-green-600/20";
            colorBackgroundPrimary = "bg-green-600";
            break;
    }

    return (
        <div className={"flex items-center gap-1"}>
            <div className={`size-4 ${colorBackground} rounded-full flex items-center justify-center`}>
                <div className={`size-2 ${colorBackgroundPrimary} rounded-full`}></div>
            </div>
            <p className={`${colorForeground} font-semibold`}>{t(props.status)}</p>
        </div>
    )
}

export function RepositoryCard(props: {name: string, type: string, artifacts: number, lastUpdated: Date, status: RepositoryStatus}) {
    let colors: string[] = [];
    switch (props.type) {
        case "MAVEN": colors = ["bg-red-500/20", "text-red-500"]; break;
        case "DOCKER": colors = ["bg-blue-500/20", "text-blue-500"]; break;
    }

    return (
        <div className={"bg-card border cursor-pointer hover:shadow-xl hover:shadow-primary/10 transition-shadow p-3 rounded-2xl group"}>
            <div className={"flex justify-between mb-4"}>
                <div className={"flex gap-3"}>
                    <div className={cn(colors, "w-fit p-4 rounded-lg")}>
                        <CupHot/>
                    </div>
                    <div>
                        <h3 className={"group-hover:text-primary"}>{props.name}</h3>
                        <p className={"text-xs text-card-foreground/60"}><span className={cn(colors, "px-2 py-0.5 rounded-xs")}>{props.type}</span> &bull; {props.artifacts.toLocaleString()} Artifacts</p>
                    </div>
                </div>
                <Button size={"icon"} variant={"ghost"}>
                    <ThreeDotsVertical/>
                </Button>
            </div>
            <div className={"mt-4 text-xs"}>
                <Separator/>
                <div className={"mt-4 mb-2 text-card-foreground/60 flex justify-between items-center"}>
                    <p className={"flex items-center gap-1"}><Clock/> Updated 1 hour ago</p>
                    <StatusIndicator status={props.status}/>
                </div>
            </div>
        </div>
    )
}