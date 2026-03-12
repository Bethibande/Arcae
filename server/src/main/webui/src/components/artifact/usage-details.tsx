import {useState} from "react";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {Tabs, TabsContent, TabsList, TabsTrigger} from "@/components/ui/tabs";
import {Button} from "@/components/ui/button";
import {Check, Copy, Terminal} from "lucide-react";
import {type ArtifactDTO, type ArtifactVersionDTO, PackageManager, type RepositoryOverviewDTO} from "@/generated";

interface DependencySnippet {
    name: string;
    language: string;
    content: string;
}

interface UsageDetailsProps {
    artifact: ArtifactDTO;
    version: ArtifactVersionDTO;
    packageManager: string;
    repository: RepositoryOverviewDTO;
}

export function UsageDetails({ artifact, version, packageManager, repository }: UsageDetailsProps) {
    const [copied, setCopied] = useState<string | null>(null);

    const getSnippets = (): DependencySnippet[] => {
        if (packageManager === PackageManager.Maven) {
            return [
                {
                    name: "Maven",
                    language: "xml",
                    content: `<dependency>\n    <groupId>${artifact.groupId}</groupId>\n    <artifactId>${artifact.artifactId}</artifactId>\n    <version>${version.version}</version>\n</dependency>`
                },
                {
                    name: "Gradle (Kotlin)",
                    language: "kotlin",
                    content: `implementation("${artifact.groupId}:${artifact.artifactId}:${version.version}")`
                },
                {
                    name: "Gradle (Groovy)",
                    language: "groovy",
                    content: `implementation '${artifact.groupId}:${artifact.artifactId}:${version.version}'`
                },
                {
                    name: "SBT",
                    language: "scala",
                    content: `libraryDependencies += "${artifact.groupId}" % "${artifact.artifactId}" % "${version.version}"`
                }
            ];
        }

        if (packageManager === PackageManager.Oci) {
            const externalHost = repository.repository.metadata!["HOST_NAME"] || window.location.host;
            const imageRef = `${externalHost}/${artifact.groupId ? `${artifact.groupId}/` : ""}${artifact.artifactId}:${version.version}`;

            return [
                {
                    name: "Image Reference",
                    language: "text",
                    content: imageRef
                },
                {
                    name: "Docker Pull",
                    language: "shell",
                    content: `docker pull ${imageRef}`
                },
                {
                    name: "Podman Pull",
                    language: "shell",
                    content: `podman pull ${imageRef}`
                }
            ];
        }

        return [];
    };

    const snippets = getSnippets();

    if (snippets.length === 0) return null;

    const copyToClipboard = (text: string, name: string) => {
        navigator.clipboard.writeText(text);
        setCopied(name);
        setTimeout(() => setCopied(null), 2000);
    };

    return (
        <Card>
            <CardHeader className="pb-3">
                <CardTitle className="text-lg flex items-center gap-2">
                    <Terminal className="size-5"/>
                    Dependency
                </CardTitle>
            </CardHeader>
            <CardContent>
                <Tabs defaultValue={snippets[0].name} className="w-full">
                    <TabsList className="mb-4">
                        {snippets.map(s => (
                            <TabsTrigger key={s.name} value={s.name}>
                                {s.name}
                            </TabsTrigger>
                        ))}
                    </TabsList>

                    {snippets.map(s => (
                        <TabsContent key={s.name} value={s.name}>
                            <div className="relative group">
                                <pre className="p-4 bg-muted/50 rounded-lg text-xs font-mono overflow-x-auto whitespace-pre">
                                    {s.content}
                                </pre>
                                <Button
                                    size="icon"
                                    variant="ghost"
                                    className="absolute right-2 top-2 h-8 w-8 opacity-0 group-hover:opacity-100 transition-opacity"
                                    onClick={() => copyToClipboard(s.content, s.name)}
                                >
                                    {copied === s.name ? <Check className="size-4 text-green-500" /> : <Copy className="size-4" />}
                                </Button>
                            </div>
                        </TabsContent>
                    ))}
                </Tabs>
            </CardContent>
        </Card>
    );
}
