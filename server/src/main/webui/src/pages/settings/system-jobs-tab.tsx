import {useCallback, useEffect, useMemo, useState} from "react";
import {JobStatus, JobType, type ScheduledJobDTO} from "@/generated";
import {jobApi, systemApi} from "@/lib/api.ts";
import {type ColumnDef, DataTable} from "@/components/ui/data-table.tsx";
import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {Play, RefreshCcw, Search, Settings} from "lucide-react";
import {toast} from "sonner";
import {showError} from "@/lib/errors.ts";
import {Tooltip, TooltipContent, TooltipTrigger} from "@/components/ui/tooltip.tsx";
import {cn} from "@/lib/utils.ts";
import {Card, CardContent} from "@/components/ui/card.tsx";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
    AlertDialogTrigger,
} from "@/components/ui/alert-dialog.tsx";

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
    [JobType.CleanUpExpiredUploads]: {
        title: "Cleanup Expired Uploads",
        description: "Cleans up expired uploads",
    },
    [JobType.UpdateSearchIndex]: {
        title: "Update Search Index",
        description: "Re-indexes all artifacts and versions for search",
    },
    [JobType.ResetPassword]: {
        title: "Reset Password Request",
        description: "Sends password reset emails to users",
    },
    [JobType.CleanUpDatabase]: {
        title: "Cleanup Database",
        description: "Removes expired data from the database",
    },
    [JobType.SendOtp]: {
        title: "Send OTP",
        description: "Sends One-Time Password to a user for authentication",
    }
};

export function SystemJobsTab() {
    const [data, setData] = useState<ScheduledJobDTO[]>([]);
    const [loading, setLoading] = useState(true);
    const [distributedEnabled, setDistributedEnabled] = useState(false);
    const [searchEnabled, setSearchEnabled] = useState(false);
    const [now, setNow] = useState(new Date());

    useEffect(() => {
        const interval = setInterval(() => setNow(new Date()), 1000);
        return () => clearInterval(interval);
    }, []);

    const fetchData = useCallback(async () => {
        setLoading(true);
        try {
            // The API supports pagination, but for now we fetch all/first page
            const response = await jobApi.apiV1JobGet({
                p: 0,
                s: 100,
            });
            setData(response.data || []);

            const caps = await systemApi.apiV1SystemCapabilitiesGet();
            setDistributedEnabled(caps.distributedScheduler);
            setSearchEnabled(caps.elasticSearchEnabled);
        } catch (error) {
            console.error("Failed to fetch jobs", error);
            toast.error("Failed to fetch jobs");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const runNow = useCallback(async (jobId: number) => {
        try {
            // Setting nextRunAt to the current time triggers immediate execution
            await jobApi.apiV1JobIdNextRunAtPut({
                id: jobId,
                body: new Date(),
            });
            toast.success("Job scheduled for immediate execution");
            fetchData();
        } catch (error) {
            console.error("Failed to trigger job", error);
            showError(error);
        }
    }, [fetchData]);

    const runOneOff = useCallback(async (type: JobType) => {
        try {
            await jobApi.apiV1JobSchedulePost({
                scheduledJobDTOWithoutId: {
                    type,
                    deleteAfterRun: true,
                    cronSchedule: "* * * * *",
                    settings: "{}",
                },
                now: true
            });
            toast.success("Job scheduled successfully");
            fetchData();
        } catch (error) {
            console.error("Failed to schedule one-off job", error);
            showError(error);
        }
    }, [fetchData]);

    const formatTimeAgo = useCallback((dateInput?: string | Date) => {
        if (!dateInput) return "Never";
        const date = dateInput instanceof Date ? dateInput : new Date(dateInput);
        const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

        if (diffInSeconds < 60) return "Just now";
        if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)} mins ago`;
        if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)} hours ago`;
        return `${Math.floor(diffInSeconds / 86400)} days ago`;
    }, [now]);

    const formatNextRun = useCallback((dateInput?: string | Date) => {
        if (!dateInput) return "Not scheduled";
        const date = dateInput instanceof Date ? dateInput : new Date(dateInput);
        const diffInSeconds = Math.floor((date.getTime() - now.getTime()) / 1000);

        if (diffInSeconds < 0) return "As soon as possible";
        if (diffInSeconds < 60) return "In less than a minute";
        if (diffInSeconds < 3600) return `In ${Math.floor(diffInSeconds / 60)} mins`;

        return date.toLocaleString();
    }, [now]);

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

    const columns = useMemo<ColumnDef<ScheduledJobDTO>[]>(() => {
        const cols: ColumnDef<ScheduledJobDTO>[] = [
            {
                accessorKey: "type",
                header: "Job Name",
                cell: (job) => {
                    const type = job.type as JobType;
                    const translation = JOB_TRANSLATIONS[type] || { title: type, description: "" };
                    return (
                        <div className="flex flex-col text-left">
                            <span className="font-semibold text-foreground">{translation.title}</span>
                            <span className="text-xs text-muted-foreground">{translation.description}</span>
                        </div>
                    );
                },
            },
        ];

        if (distributedEnabled) {
            cols.push({
                accessorKey: "runner",
                header: "Runner",
                cell: (job) => (
                    <span className="font-mono text-xs text-muted-foreground bg-muted/50 px-1.5 py-0.5 rounded">
                        {job.runner || "N/A"}
                    </span>
                ),
            });
        }

        cols.push(
            {
                header: "Status",
                cell: (job) => {
                    const status = job.status;
                    
                    switch (status) {
                        case JobStatus.Running:
                            return (
                                <Badge className="bg-green-500/10 text-green-500 border-green-500/20 hover:bg-green-500/20 flex gap-1.5 items-center w-fit">
                                    <div className="size-1.5 rounded-full bg-green-500 animate-pulse" />
                                    Running
                                </Badge>
                            );
                        case JobStatus.Failed:
                            return (
                                <Badge variant="destructive" className="flex gap-1.5 items-center w-fit">
                                    <div className="size-1.5 rounded-full bg-white" />
                                    Failed
                                </Badge>
                            );
                        case JobStatus.Scheduled:
                        case JobStatus.Queued:
                            return (
                                <Badge variant="outline" className="bg-blue-500/5 text-blue-400 border-blue-500/20 flex gap-1.5 items-center w-fit">
                                    <div className="size-1.5 rounded-full bg-blue-400" />
                                    {status === JobStatus.Scheduled ? "Scheduled" : "Queued"}
                                </Badge>
                            );
                        default:
                            return (
                                <Badge variant="outline" className="bg-muted/50 text-muted-foreground border-transparent flex gap-1.5 items-center w-fit">
                                    <div className="size-1.5 rounded-full bg-muted-foreground/50" />
                                    Idle
                                </Badge>
                            );
                    }
                }
            },
            {
                accessorKey: "lastSuccessfulRun",
                header: "Last Successful Run",
                cell: (job) => {
                    const value = job.lastSuccessfulRun;
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
                header: "Last Duration",
                cell: (job) => {
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
                header: "Next Scheduled Run",
                cell: (job) => {
                    const nextRunAt = job.nextRunAt;
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
                header: "Actions",
                cell: (job) => {
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
            }
        );

        return cols;
    }, [distributedEnabled, now, runNow, formatNextRun, formatTimeAgo]);

    return (
        <section className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-xl font-semibold flex items-center gap-2">
                        <Settings className="size-5" /> System Jobs
                    </h2>
                    <p className="text-sm text-muted-foreground mt-1">
                        Manage and monitor background system tasks.
                    </p>
                </div>
                <Button variant="outline" size="sm" onClick={fetchData} disabled={loading} className="gap-2">
                    <RefreshCcw className={cn("size-4", loading && "animate-spin")} />
                    Refresh All
                </Button>
            </div>

            <DataTable
                columns={columns}
                data={data}
                loading={loading}
                emptyMessage="No system jobs found."
            />

            <div className={cn("space-y-4", !searchEnabled && "hidden")}>
                <h3 className="text-lg font-medium flex items-center gap-2">
                    <Play className="size-4" /> One-off Jobs
                </h3>
                <Card>
                    <CardContent className="p-6">
                        <div className="flex items-center justify-between">
                            <div className="space-y-1">
                                <h4 className="font-semibold flex items-center gap-2">
                                    <Search className="size-4" /> Update Search Index
                                </h4>
                                <p className="text-sm text-muted-foreground">
                                    Re-indexes all artifacts for search. This may take a while depending on the number of artifacts.
                                </p>
                            </div>
                            <AlertDialog>
                                <AlertDialogTrigger asChild>
                                    <Button variant="outline" className="gap-2">
                                        <Play className="size-4" /> Run Now
                                    </Button>
                                </AlertDialogTrigger>
                                <AlertDialogContent>
                                    <AlertDialogHeader>
                                        <AlertDialogTitle>Are you sure?</AlertDialogTitle>
                                        <AlertDialogDescription>
                                            This action cannot be cancelled. It might cause small disruptions and take a long time to complete, depending on your database size.
                                        </AlertDialogDescription>
                                    </AlertDialogHeader>
                                    <AlertDialogFooter>
                                        <AlertDialogCancel>Cancel</AlertDialogCancel>
                                        <AlertDialogAction onClick={() => runOneOff(JobType.UpdateSearchIndex)}>
                                            Run Now
                                        </AlertDialogAction>
                                    </AlertDialogFooter>
                                </AlertDialogContent>
                            </AlertDialog>
                        </div>
                    </CardContent>
                </Card>
            </div>
        </section>
    );
}
