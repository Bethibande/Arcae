import {useState, useEffect} from "react";
import {Input} from "@/components/ui/input.tsx";
import {Search, Loader2} from "lucide-react";
import {cn} from "@/lib/utils.ts";
import {type UserDTOWithoutPassword, UserEndpointApi} from "@/generated";
import {Popover, PopoverAnchor, PopoverContent} from "@/components/ui/popover.tsx";

interface UserSearchProps {
    onSelect: (user: UserDTOWithoutPassword) => void;
    placeholder?: string;
    className?: string;
}

export function UserSearch({onSelect, placeholder = "Search for a user...", className}: UserSearchProps) {
    const [query, setQuery] = useState("");
    const [results, setResults] = useState<UserDTOWithoutPassword[]>([]);
    const [loading, setLoading] = useState(false);
    const [open, setOpen] = useState(false);

    useEffect(() => {
        if (query.length < 2) {
            setResults([]);
            setOpen(false);
            return;
        }

        const timeout = setTimeout(() => {
            setLoading(true);
            new UserEndpointApi()
                .apiV1UserSearchGet({q: query})
                .then(resp => {
                    setResults(resp.data || []);
                    setOpen(true);
                })
                .finally(() => setLoading(false));
        }, 300);

        return () => clearTimeout(timeout);
    }, [query]);

    return (
        <div className={cn("relative", className)}>
            <Popover open={open} onOpenChange={setOpen}>
                <PopoverAnchor asChild>
                    <div className="relative">
                        <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground"/>
                        <Input
                            type="search"
                            placeholder={placeholder}
                            className="pl-8"
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            onFocus={() => query.length >= 2 && setOpen(true)}
                        />
                        {loading && (
                            <div className="absolute right-2.5 top-2.5">
                                <Loader2 className="h-4 w-4 animate-spin text-muted-foreground"/>
                            </div>
                        )}
                    </div>
                </PopoverAnchor>

                <PopoverContent
                    align={"start"}
                    className="p-0 min-w-(--radix-popper-anchor-width) overflow-hidden"
                    onOpenAutoFocus={(e) => e.preventDefault()}
                >
                    {results.length > 0 ? (
                        <div className="max-h-60 overflow-y-auto">
                            {results.map(user => (
                                <button
                                    key={user.id}
                                    className="w-full text-left px-4 py-2 text-sm hover:bg-accent hover:text-accent-foreground flex flex-col"
                                    onClick={() => {
                                        onSelect(user);
                                        setQuery("");
                                        setOpen(false);
                                    }}
                                >
                                    <span className="font-medium">{user.name}</span>
                                    <span className="text-xs text-muted-foreground">{user.email}</span>
                                </button>
                            ))}
                        </div>
                    ) : (
                        query.length >= 2 && !loading && (
                            <div className="p-4 text-sm text-center">
                                No users found
                            </div>
                        )
                    )}
                </PopoverContent>
            </Popover>
        </div>
    );
}
