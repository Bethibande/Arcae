import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Layers } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { type ArtifactVersionDTO } from "@/generated";

interface OCIManifestsCardProps {
    version: ArtifactVersionDTO;
}

export function OCIManifestsCard({ version }: OCIManifestsCardProps) {
    const manifests = version.details?.additionalData?.manifests;

    if (!manifests || manifests.length === 0) {
        return null;
    }

    return (
        <Card>
            <CardHeader>
                <CardTitle className="text-lg flex items-center gap-2">
                    <Layers className="size-5" />
                    Manifests
                </CardTitle>
            </CardHeader>
            <CardContent>
                <div className="rounded-md border overflow-hidden">
                    <table className="w-full text-sm">
                        <thead className="bg-muted/50 border-b">
                            <tr>
                                <th className="px-4 py-2 text-left font-medium">OS / Architecture</th>
                                <th className="px-4 py-2 text-left font-medium">Digest</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y">
                            {manifests.map((m: any, i: number) => (
                                <tr key={i} className="hover:bg-muted/30 transition-colors">
                                    <td className="px-4 py-3">
                                        <div className="flex items-center gap-2">
                                            <Badge variant="secondary" className="capitalize">
                                                {m.os}
                                            </Badge>
                                            <Badge variant="outline">
                                                {m.architecture}
                                            </Badge>
                                        </div>
                                    </td>
                                    <td className="px-4 py-3 font-mono text-[10px] text-muted-foreground break-all text-right">
                                        {m.digest}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </CardContent>
        </Card>
    );
}
