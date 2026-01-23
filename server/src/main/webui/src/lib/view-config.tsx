import {createContext, type ReactNode, useContext, useState} from "react";

export interface ViewConfig {
    toolbar: ReactNode;
}

export interface ViewConfigContextType {
    viewConfig: ViewConfig;
    setViewConfig: (config: ViewConfig) => void;
}

export const ViewConfigContext = createContext<ViewConfigContextType | undefined>(undefined);
export const ViewConfigProvider = ({children}: { children: ReactNode }) => {
    const [viewConfig, setViewConfig] = useState<ViewConfig>({toolbar: null});

    return (
        <ViewConfigContext.Provider value={{viewConfig, setViewConfig}}>
            {children}
        </ViewConfigContext.Provider>
    )
}

export const useViewConfig = () => {
    const viewConfig = useContext(ViewConfigContext);
    if (!viewConfig) {
        throw new Error('useViewConfig must be used within a ViewConfigProvider');
    }
    return viewConfig;
}