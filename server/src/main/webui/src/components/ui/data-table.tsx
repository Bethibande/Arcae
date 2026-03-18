import * as React from "react";
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow,} from "@/components/ui/table.tsx";
import {Button} from "@/components/ui/button.tsx";
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue,} from "@/components/ui/select.tsx";
import {ChevronLeft, ChevronRight} from "lucide-react";
import {cn} from "@/lib/utils.ts";

export interface ColumnDef<T> {
    header: string;
    accessorKey?: string;
    id?: string;
    cell?: (item: T) => React.ReactNode;
    className?: string;
}

interface DataTableProps<T> {
    columns: ColumnDef<T>[];
    data: T[];
    loading?: boolean;
    emptyMessage?: string;
}

export function DataTable<T>({
    columns,
    data,
    loading = false,
    emptyMessage = "No data available.",
}: DataTableProps<T>) {
    const [pageSize, setPageSize] = React.useState(10);
    const [currentPage, setCurrentPage] = React.useState(1);

    const totalPages = Math.ceil(data.length / pageSize) || 1;
    const startIndex = (currentPage - 1) * pageSize;
    const endIndex = Math.min(startIndex + pageSize, data.length);
    const currentData = data.slice(startIndex, endIndex);

    React.useEffect(() => {
        if (currentPage > totalPages) {
            setCurrentPage(totalPages);
        }
    }, [totalPages, currentPage]);

    return (
        <div className="space-y-4 w-full">
            <div className="rounded-xl border bg-card overflow-hidden">
                <Table>
                    <TableHeader>
                        <TableRow>
                            {columns.map((column, index) => (
                                <TableHead
                                    key={index}
                                    className={cn(
                                        "text-xs uppercase tracking-wider font-bold text-muted-foreground p-4",
                                        column.className
                                    )}
                                >
                                    {column.header}
                                </TableHead>
                            ))}
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        {loading ? (
                            <TableRow>
                                <TableCell
                                    colSpan={columns.length}
                                    className="h-24 text-center text-muted-foreground"
                                >
                                    Loading...
                                </TableCell>
                            </TableRow>
                        ) : currentData.length === 0 ? (
                            <TableRow>
                                <TableCell
                                    colSpan={columns.length}
                                    className="h-24 text-center text-muted-foreground"
                                >
                                    {emptyMessage}
                                </TableCell>
                            </TableRow>
                        ) : (
                            currentData.map((item, rowIndex) => (
                                <TableRow key={rowIndex} className="group hover:bg-muted/30">
                                    {columns.map((column, colIndex) => (
                                        <TableCell
                                            key={colIndex}
                                            className={cn("p-4", column.className)}
                                        >
                                            {column.cell
                                                ? column.cell(item)
                                                : column.accessorKey
                                                ? (item[column.accessorKey as keyof T] as any)
                                                : null}
                                        </TableCell>
                                    ))}
                                </TableRow>
                            ))
                        )}
                    </TableBody>
                </Table>
            </div>

            <div className="flex flex-col sm:flex-row items-center justify-between gap-4 px-2 py-1 text-sm text-muted-foreground">
                <div className="order-2 sm:order-1">
                    Showing {data.length === 0 ? 0 : startIndex + 1} to {endIndex} of {data.length} entries
                </div>

                <div className="flex items-center gap-6 order-1 sm:order-2">
                    <div className="flex items-center gap-2">
                        <span>Rows per page</span>
                        <Select
                            value={pageSize.toString()}
                            onValueChange={(value) => {
                                setPageSize(Number(value));
                                setCurrentPage(1);
                            }}
                        >
                            <SelectTrigger className="h-8 w-[70px]">
                                <SelectValue placeholder={pageSize} />
                            </SelectTrigger>
                            <SelectContent side="top">
                                {[10, 20, 30, 40, 50].map((size) => (
                                    <SelectItem key={size} value={size.toString()}>
                                        {size}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>

                    <div className="flex items-center gap-4">
                        <div className="flex items-center justify-center font-medium">
                            Page {currentPage} of {totalPages}
                        </div>
                        <div className="flex items-center gap-2">
                            <Button
                                variant="outline"
                                className="h-8 w-8 p-0"
                                onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                                disabled={currentPage === 1}
                            >
                                <span className="sr-only">Go to previous page</span>
                                <ChevronLeft className="h-4 w-4" />
                            </Button>
                            <Button
                                variant="outline"
                                className="h-8 w-8 p-0"
                                onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                                disabled={currentPage === totalPages}
                            >
                                <span className="sr-only">Go to next page</span>
                                <ChevronRight className="h-4 w-4" />
                            </Button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
