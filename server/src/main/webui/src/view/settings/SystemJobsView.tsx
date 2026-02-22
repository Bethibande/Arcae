import {useEffect, useMemo, useState} from "react";
import {type ColumnDef, getCoreRowModel, type PaginationState, useReactTable,} from "@tanstack/react-table";
import {JobEndpointApi, type ScheduledJobDTO, JobType, UserRole, SystemEndpointApi, JobStatus} from "@/generated";
import {DataTable} from "@/components/data-table";
import {DataTablePagination} from "@/components/data-table-pagination";
import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {Play, RefreshCcw} from "lucide-react";
import {toast} from "sonner";
import {useAuth} from "@/lib/auth.tsx";
import {Navigate} from "react-router";
import {showError} from "@/lib/errors.ts";
import {Tooltip, TooltipContent, TooltipTrigger} from "@/components/ui/tooltip.tsx";

const JOB_TRANSLATIONS: Record<JobType, { title: string; description: string }> = {
    [JobType.DeleteOldVersions]: {
        title: "Cleanup Old Versions",
        description: "Executes repository cleanup policies",
    },
    [JobType.CleanUpOrphanedFiles]: {
        title: "Delete Orphaned Files",
        description: "Scans file system for unreferenced blobs",
    },
    [JobType.DeleteExpiredSessions]: {
        title: "Clear Expired Sessions",
        description: "Removes expired user sessions from the database",
    },
};

export default function SystemJobsView() {
    const {user} = useAuth();

    if (!user?.roles?.includes(UserRole.Admin)) {
        return <Navigate to="/settings/user" replace />;
    }

    const [data, setData] = useState<ScheduledJobDTO[]>([]);
    const [loading, setLoading] = useState(true);
    const [pagination, setPagination] = useState<PaginationState>({
        pageIndex: 0,
        pageSize: 10,
    });
    const [totalPages, setTotalPages] = useState(0);
    const [distributedEnabled, setDistributedEnabled] = useState(false);
    const [now, setNow] = useState(new Date());

    useEffect(() => {
        const interval = setInterval(() => setNow(new Date()), 1000);
        return () => clearInterval(interval);
    }, []);

    const fetchData = async () => {
        setLoading(true);
        try {
            const api = new JobEndpointApi();
            const response = await api.apiV1JobGet({
                p: pagination.pageIndex,
                s: pagination.pageSize,
            });
            setData(response.data || []);
            setTotalPages(response.pages || 0);

            const systemApi = new SystemEndpointApi();
            const caps = await systemApi.apiV1SystemK8sCapabilitiesGet();
            setDistributedEnabled(caps.distributedScheduler);
        } catch (error) {
            console.error("Failed to fetch jobs", error);
            toast.error("Failed to fetch jobs");
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchData();
    }, [pagination.pageIndex, pagination.pageSize]);

    const runNow = async (jobId: number) => {
        try {
            const api = new JobEndpointApi();
            // Setting nextRunAt to the current time triggers immediate execution
            await api.apiV1JobIdNextRunAtPut({
                id: jobId,
                body: new Date(),
            });
            toast.success("Job scheduled for immediate execution");
            fetchData();
        } catch (error) {
            console.error("Failed to trigger job", error);
            showError(error);
        }
    };

    const formatTimeAgo = (dateInput?: string | Date) => {
        if (!dateInput) return "Never";
        const date = dateInput instanceof Date ? dateInput : new Date(dateInput);
        const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

        if (diffInSeconds < 60) return "Just now";
        if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)} mins ago`;
        if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)} hours ago`;
        return `${Math.floor(diffInSeconds / 86400)} days ago`;
    };

    const formatNextRun = (dateInput?: string | Date) => {
        if (!dateInput) return "Not scheduled";
        const date = dateInput instanceof Date ? dateInput : new Date(dateInput);
        const diffInSeconds = Math.floor((date.getTime() - now.getTime()) / 1000);

        if (diffInSeconds < 0) return "As soon as possible";
        if (diffInSeconds < 60) return "In less than a minute";
        if (diffInSeconds < 3600) return `In ${Math.floor(diffInSeconds / 60)} mins`;

        return date.toLocaleString();
    };

    const formatDuration = (start?: string | Date, end?: string | Date) => {
        if (!start || !end) return null;
        const startDate = start instanceof Date ? start : new Date(start);
        const endDate = end instanceof Date ? end : new Date(end);
        const durationMs = endDate.getTime() - startDate.getTime();
        
        if (durationMs < 0) return null;

        if (durationMs < 1000) return `${durationMs}ms`;

        const seconds = Math.floor(durationMs / 1000);
        if (seconds < 60) return `${seconds}s`;
        
        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = seconds % 60;
        if (minutes < 60) return `${minutes}m ${remainingSeconds}s`;
        
        const hours = Math.floor(minutes / 60);
        const remainingMinutes = minutes % 60;
        return `${hours}h ${remainingMinutes}m`;
    };

    const columns: ColumnDef<ScheduledJobDTO>[] = useMemo(() => [
        {
            accessorKey: "type",
            header: "Job Name",
            cell: ({row}) => {
                const type = row.getValue("type") as JobType;
                const translation = JOB_TRANSLATIONS[type] || { title: type, description: "" };
                return (
                    <div className="flex flex-col">
                        <span className="font-semibold text-foreground">{translation.title}</span>
                        <span className="text-xs text-muted-foreground">{translation.description}</span>
                    </div>
                );
            },
        },
        ...(distributedEnabled ? [{
            accessorKey: "runner",
            header: "Runner",
            cell: ({row}: {row: any}) => (
                <span className="font-mono text-xs text-muted-foreground bg-muted/50 px-1.5 py-0.5 rounded">
                    {row.original.runner || "N/A"}
                </span>
            ),
        }] : []),
        {
            id: "status",
            header: "Status",
            cell: ({row}) => {
                const status = row.original.status;
                
                switch (status) {
                    case JobStatus.Running:
                        return (
                            <Badge className="bg-blue-500/10 text-blue-500 border-blue-500/20 hover:bg-blue-500/20 flex gap-1.5 items-center w-fit">
                                <div className="size-1.5 rounded-full bg-blue-500 animate-pulse" />
                                RUNNING
                            </Badge>
                        );
                    case JobStatus.Failed:
                        return (
                            <Badge variant="destructive" className="flex gap-1.5 items-center w-fit">
                                <div className="size-1.5 rounded-full bg-white" />
                                FAILED
                            </Badge>
                        );
                    case JobStatus.Scheduled:
                    case JobStatus.Queued:
                        return (
                            <Badge variant="outline" className="bg-blue-500/5 text-blue-400 border-blue-500/20 flex gap-1.5 items-center w-fit">
                                <div className="size-1.5 rounded-full bg-blue-400" />
                                {status}
                            </Badge>
                        );
                    default:
                        return (
                            <Badge variant="outline" className="bg-muted/50 text-muted-foreground border-transparent flex gap-1.5 items-center w-fit">
                                <div className="size-1.5 rounded-full bg-muted-foreground/50" />
                                IDLE
                            </Badge>
                        );
                }
            }
        },
        {
            accessorKey: "lastSuccessfulRun",
            header: "Last Successful Run",
            cell: ({row}) => {
                const value = row.getValue("lastSuccessfulRun") as string | undefined;
                if (!value) return "Never";
                
                return (
                    <Tooltip>
                        <TooltipTrigger asChild>
                            <span className="cursor-help underline decoration-dotted decoration-muted-foreground/30 underline-offset-4">
                                {formatTimeAgo(value)}
                            </span>
                        </TooltipTrigger>
                        <TooltipContent>
                            {new Date(value).toLocaleString()}
                        </TooltipContent>
                    </Tooltip>
                );
            },
        },
        {
            id: "duration",
            header: "Last Duration",
            cell: ({row}) => {
                const job = row.original;
                if (job.status === JobStatus.Running || job.status === JobStatus.Failed) return <span className="text-muted-foreground text-xs">—</span>;

                const duration = formatDuration(job.executionStartedAt, job.lastSuccessfulRun);
                return (
                    <span className="text-muted-foreground text-xs font-mono">
                        {duration || "—"}
                    </span>
                );
            }
        },
        {
            id: "nextRunAt",
            header: "Next Scheduled Run",
            cell: ({row}) => {
                const nextRunAt = row.original.nextRunAt;
                const date = nextRunAt && (nextRunAt instanceof Date ? nextRunAt : new Date(nextRunAt));
                const isSoon = date && (date.getTime() - now.getTime()) < 3600000;
                return (
                    <span className={isSoon ? "text-blue-400 font-medium" : "text-muted-foreground"}>
                        {formatNextRun(nextRunAt)}
                    </span>
                );
            }
        },
        {
            id: "actions",
            header: "Actions",
            cell: ({row}) => {
                const job = row.original;
                return (
                    <div className="flex justify-end">
                        {(job.status !== JobStatus.Running && job.status !== JobStatus.Queued) && (
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => runNow(job.id!)}
                                className="h-8 px-2 text-muted-foreground hover:text-foreground"
                            >
                                <Play className="mr-2 h-3.5 w-3.5" />
                                Run Now
                            </Button>
                        )}
                    </div>
                );
            },
        },
    ], [distributedEnabled, now, runNow]);

    const table = useReactTable({
        data,
        columns,
        getCoreRowModel: getCoreRowModel(),
        manualPagination: true,
        pageCount: totalPages,
        state: {
            pagination,
        },
        onPaginationChange: setPagination,
    });

    return (
        <div className="p-8 space-y-8 max-w-7xl mx-auto">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold tracking-tight">Repository Maintenance Jobs</h1>
                    <p className="text-muted-foreground mt-1">Manage and monitor background system tasks.</p>
                </div>
                <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={fetchData} disabled={loading} className="bg-muted/30 border-muted-foreground/20">
                        <RefreshCcw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
                        Refresh All
                    </Button>
                </div>
            </div>

            {loading && data.length === 0 ? (
                <div className="flex items-center justify-center h-64 text-muted-foreground">
                    <RefreshCcw className="mr-2 h-5 w-5 animate-spin" />
                    Loading jobs...
                </div>
            ) : (
                <div className="space-y-4">
                    <DataTable columns={columns} data={data}/>
                    <DataTablePagination table={table}/>
                </div>
            )}
        </div>
    );
}
