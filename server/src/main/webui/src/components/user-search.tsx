import {useEffect, useState} from "react";
import {Input} from "@/components/ui/input.tsx";
import {Loader2, Search, X} from "lucide-react";
import {cn} from "@/lib/utils.ts";
import {userApi} from "@/lib/api.ts";
import {type UserDTOWithoutPassword} from "@/generated";
import {Popover, PopoverAnchor, PopoverContent} from "@/components/ui/popover.tsx";
import {Button} from "@/components/ui/button.tsx";

interface UserSearchProps {
    onSelect: (user: UserDTOWithoutPassword) => void;
    placeholder?: string;
    className?: string;
    value?: string;
    onClear?: () => void;
}

export function UserSearch({onSelect, placeholder = "Search for a user...", className, value, onClear}: UserSearchProps) {
    const [query, setQuery] = useState(value || "");
    const [results, setResults] = useState<UserDTOWithoutPassword[]>([]);
    const [loading, setLoading] = useState(false);
    const [open, setOpen] = useState(false);

    useEffect(() => {
        if (value !== undefined) {
            setQuery(value);
        }
    }, [value]);

    useEffect(() => {
        if (query.length < 2 || (value && query === value)) {
            setResults([]);
            setOpen(false);
            return;
        }

        const timeout = setTimeout(() => {
            setLoading(true);
            userApi
                .apiV1UserSearchGet({q: query})
                .then(resp => {
                    setResults(resp.data || []);
                    setOpen(true);
                })
                .finally(() => setLoading(false));
        }, 300);

        return () => clearTimeout(timeout);
    }, [query, value]);

    return (
        <div className={cn("relative", className)}>
            <Popover open={open} onOpenChange={setOpen}>
                <PopoverAnchor asChild>
                    <div className="relative group">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"/>
                        <Input
                            type="text"
                            placeholder={placeholder}
                            className="pl-10 pr-10"
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            onFocus={() => query.length >= 2 && setOpen(true)}
                        />
                        {loading ? (
                            <div className="absolute right-3 top-1/2 -translate-y-1/2">
                                <Loader2 className="h-4 w-4 animate-spin text-muted-foreground"/>
                            </div>
                        ) : query && onClear ? (
                            <Button
                                variant="ghost"
                                size="icon"
                                className="absolute right-1 top-1/2 -translate-y-1/2 h-8 w-8 text-muted-foreground hover:text-foreground"
                                onClick={(e) => {
                                    e.stopPropagation();
                                    setQuery("");
                                    onClear();
                                }}
                            >
                                <X className="h-4 w-4"/>
                            </Button>
                        ) : null}
                    </div>
                </PopoverAnchor>

                <PopoverContent
                    align={"start"}
                    className="p-1 min-w-(--radix-popper-anchor-width) overflow-hidden"
                    onOpenAutoFocus={(e) => e.preventDefault()}
                >
                    {results.length > 0 ? (
                        <div className="max-h-60 overflow-y-auto">
                            {results.map(user => (
                                <button
                                    key={user.id}
                                    className="w-full text-left px-3 py-2 text-sm rounded-sm hover:bg-accent hover:text-accent-foreground flex flex-col gap-0.5"
                                    onClick={() => {
                                        onSelect(user);
                                        setQuery(user.name);
                                        setOpen(false);
                                    }}
                                >
                                    <span className="font-semibold">{user.name}</span>
                                    <span className="text-xs text-muted-foreground">{user.email}</span>
                                </button>
                            ))}
                        </div>
                    ) : (
                        query.length >= 2 && !loading && (
                            <div className="p-4 text-sm text-center text-muted-foreground">
                                No users found
                            </div>
                        )
                    )}
                </PopoverContent>
            </Popover>
        </div>
    );
}
