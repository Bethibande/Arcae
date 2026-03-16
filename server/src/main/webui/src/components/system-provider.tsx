import {createContext, ReactNode, useCallback, useContext, useEffect, useState} from "react";
import {systemApi} from "@/lib/api.ts";
import {type SystemReference} from "@/generated";

interface SystemContextType {
    references: SystemReference[];
    refreshReferences: () => Promise<void>;
}

const SystemContext = createContext<SystemContextType | undefined>(undefined);

export function SystemProvider({ children }: { children: ReactNode }) {
    const [references, setReferences] = useState<SystemReference[]>([]);
    const [initialized, setInitialized] = useState(false);

    const refreshReferences = useCallback(async () => {
        try {
            const data = await systemApi.apiV1SystemFooterRefsGet();
            setReferences(data);
        } catch (error) {
            console.error("Failed to fetch footer references", error);
        }
    }, []);

    useEffect(() => {
        if (!initialized) {
            refreshReferences();
            setInitialized(true);
        }
    }, [initialized, refreshReferences]);

    return (
        <SystemContext.Provider value={{ references, refreshReferences }}>
            {children}
        </SystemContext.Provider>
    );
}

export function useSystem() {
    const context = useContext(SystemContext);
    if (context === undefined) {
        throw new Error("useSystem must be used within a SystemProvider");
    }
    return context;
}
