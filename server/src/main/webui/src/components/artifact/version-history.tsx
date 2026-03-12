import {type ArtifactVersionDTO, type PagedResponseArtifactVersionDTO} from "@/generated";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {History, Search} from "lucide-react";
import {Input} from "@/components/ui/input";
import {cn} from "@/lib/utils";
import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {useEffect, useState} from "react";

interface VersionHistoryProps {
    versions: PagedResponseArtifactVersionDTO | null;
    selectedVersion: ArtifactVersionDTO | null;
    onSelectVersion: (version: ArtifactVersionDTO) => void;
    latestVersion?: string | null;
    loading?: boolean;
    page: number;
    onPageChange: (page: number) => void;
    search: string;
    onSearchChange: (search: string) => void;
}

export function VersionHistory({
                                   versions,
                                   selectedVersion,
                                   onSelectVersion,
                                   latestVersion,
                                   loading,
                                   page,
                                   onPageChange,
                                   search,
                                   onSearchChange
                               }: VersionHistoryProps) {
    const [localSearch, setLocalSearch] = useState(search);

    useEffect(() => {
        setLocalSearch(search);
    }, [search]);

    useEffect(() => {
        if (localSearch === search) return;

        const timer = setTimeout(() => {
            onSearchChange(localSearch);
        }, 500);

        return () => clearTimeout(timer);
    }, [localSearch, onSearchChange, search]);

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === "Enter") {
            onSearchChange(localSearch);
        }
    };

    const totalVersions = versions?.total || 0;
    const totalPages = versions?.pages || 0;

    return (
        <Card className="flex flex-col h-full overflow-hidden">
            <CardHeader className="pb-3 shrink-0">
                <CardTitle className="text-lg flex items-center gap-2">
                    <History className="size-5 text-primary"/>
                    Version History
                </CardTitle>
            </CardHeader>
            <CardContent className="flex-1 flex flex-col min-h-0 gap-4">
                <div className="relative shrink-0">
                    <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground"/>
                    <Input
                        placeholder="Filter versions..."
                        className="pl-9 bg-muted/30 border-none h-9"
                        value={localSearch}
                        onChange={(e) => setLocalSearch(e.target.value)}
                        onKeyDown={handleKeyDown}
                    />
                </div>

                <div className="flex-1 overflow-y-auto space-y-2 pr-1 custom-scrollbar">
                    {loading ? (
                        <div className="py-8 text-center text-sm text-muted-foreground">Loading versions...</div>
                    ) : (
                        versions?.data?.map((v) => (
                            <div
                                key={v.id}
                                onClick={() => onSelectVersion(v)}
                                className={cn(
                                    "px-3 py-2.5 rounded-lg text-sm cursor-pointer transition-all border flex items-center justify-between",
                                    selectedVersion?.id === v.id
                                        ? "border-primary bg-primary/5 border-1 ring-primary shadow-sm"
                                        : "border-transparent hover:border-muted hover:bg-muted/50"
                                )}
                            >
                                <div className="flex flex-col min-w-0 flex-1">
                                    <span className={cn(
                                        "truncate font-medium",
                                        selectedVersion?.id === v.id ? "text-primary" : ""
                                    )}>
                                        {v.version}
                                    </span>
                                    {v.updated && (
                                        <span className="text-[10px] text-muted-foreground truncate">
                                            {new Date(v.updated).toLocaleDateString()}
                                        </span>
                                    )}
                                </div>
                                {v.version === latestVersion && (
                                    <Badge 
                                        variant={selectedVersion?.id === v.id ? "default" : "secondary"} 
                                        className="text-[10px] px-1.5 h-4 ml-2"
                                    >
                                        Latest
                                    </Badge>
                                )}
                            </div>
                        ))
                    )}
                    {!loading && totalVersions === 0 && (
                        <div className="py-8 text-center text-sm text-muted-foreground italic">No versions found</div>
                    )}
                </div>

                {totalPages > 1 && (
                    <div className="shrink-0 pt-2 border-t flex items-center justify-between gap-2">
                        <Button
                            variant="ghost"
                            size="sm"
                            disabled={page === 0 || loading}
                            onClick={() => onPageChange(page - 1)}
                            className="h-8 px-2"
                        >
                            Prev
                        </Button>
                        <span className="text-[11px] font-medium text-muted-foreground">
                            {page + 1} / {totalPages}
                        </span>
                        <Button
                            variant="ghost"
                            size="sm"
                            disabled={page >= totalPages - 1 || loading}
                            onClick={() => onPageChange(page + 1)}
                            className="h-8 px-2"
                        >
                            Next
                        </Button>
                    </div>
                )}

                {selectedVersion && (
                    <div className="text-[12px] text-muted-foreground space-y-1 pt-2 border-t">
                        <div className="flex items-center gap-2">
                            <span className="truncate">Created: {selectedVersion.created ? new Date(selectedVersion.created).toLocaleDateString() : "Unknown"}</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <span className="truncate">Updated: {selectedVersion.updated ? new Date(selectedVersion.updated).toLocaleDateString() : "Unknown"}</span>
                        </div>
                    </div>
                )}
            </CardContent>
        </Card>
    );
}
