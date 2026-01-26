import {useViewConfig} from "@/lib/view-config.tsx";
import {useEffect} from "react";
import {Separator} from "@/components/ui/separator.tsx";
import {ChevronRight, Person, Plus} from "react-bootstrap-icons";
import {RepositoryCard, RepositoryStatus} from "@/view/overview/RepositoryCard.tsx";

export function DefaultView() {
    const {setViewConfig} = useViewConfig()
    useEffect(() => {
        setViewConfig({
            toolbar: (<h2>Repositories</h2>),
            sidebarVisible: true
        })
    }, [])

    return (
        <div>
            <div className={"p-4 border-b flex justify-between"}>
                <div>
                    <h1>Repository</h1>
                </div>
                <div className={"flex gap-3"}>
                    <Separator orientation={"vertical"}/>
                    <div className={"flex gap-2 group cursor-pointer"}>
                        <div className={"flex flex-col select-none"}>
                            <p className={"font-semibold"}>Username</p>
                            <span className={"text-xs text-gray-500"}>admin</span>
                        </div>
                        <div
                            className={"size-10 rounded-full bg-card group-hover:outline-2 group-hover:outline-primary flex justify-center items-center"}>
                            <Person/>
                        </div>
                    </div>
                </div>
            </div>
            <div className={"p-4 flex justify-center"}>
                <div className={"xl:w-1/2 w-full flex flex-col gap-3"}>
                    <div className={"text-primary-foreground/60 flex gap-2 items-center"}>
                        <p>Repositories</p>
                        <ChevronRight/>
                        <p className={"text-primary-foreground"}>Overview</p>
                    </div>
                    <div className={"flex justify-between items-end"}>
                        <div>
                            <h2>Repositories</h2>
                            <p>Browse all available repositories</p>
                        </div>
                        <div className={"flex items-center gap-2"}>

                        </div>
                    </div>
                    <div className={"grid lg:grid-cols-2 gap-3"}>
                        <RepositoryCard name={"releases"} type={"MAVEN"} artifacts={1252} lastUpdated={new Date()} status={RepositoryStatus.ACTIVE}/>
                        <RepositoryCard name={"snapshots"} type={"MAVEN"} artifacts={1252} lastUpdated={new Date()} status={RepositoryStatus.ACTIVE}/>
                        <RepositoryCard name={"docker"} type={"DOCKER"} artifacts={52} lastUpdated={new Date()} status={RepositoryStatus.MAINTENANCE}/>

                        <div className={"outline-2 outline-dashed cursor-pointer flex justify-center items-center rounded-2xl hover:text-primary transition-colors"}>
                                <Plus className={"text-4xl"}/>
                                <p>Create repository</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    )
}