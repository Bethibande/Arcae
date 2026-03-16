import {useEffect, useState} from "react";
import {useParams, useNavigate} from "react-router";
import {systemApi} from "@/lib/api.ts";
import {type SystemReference, SystemReferenceType} from "@/generated";
import {ChevronLeft} from "lucide-react";
import {Button} from "@/components/ui/button.tsx";
import {showError} from "@/lib/errors.ts";

export default function ReferencePage() {
    const {label} = useParams();
    const navigate = useNavigate();
    const [reference, setReference] = useState<SystemReference | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchReference = async () => {
            try {
                const refs = await systemApi.apiV1SystemHeaderRefsGet();
                const ref = refs.find(r => r.label === label && r.type === SystemReferenceType.Text);
                setReference(ref || null);
            } catch (err) {
                showError(err)
            } finally {
                setLoading(false);
            }
        };

        fetchReference();
    }, [label]);

    if (loading) {
        return (
            <div className="flex items-center justify-center min-h-[50vh]">
                <div className="text-muted-foreground animate-pulse">Loading...</div>
            </div>
        );
    }

    if (!reference) {
        return (
            <div className="flex flex-col items-center justify-center min-h-[50vh] space-y-4">
                <h1 className="text-2xl font-bold">Not Found</h1>
                <p className="text-muted-foreground">The requested page could not be found.</p>
                <Button variant="outline" onClick={() => navigate("/")}>
                    Go Home
                </Button>
            </div>
        );
    }

    return (
        <div className="container max-w-4xl mx-auto py-12 px-6">
            <Button
                variant="ghost"
                size="sm"
                className="mb-8 -ml-2 text-muted-foreground hover:text-foreground"
                onClick={() => navigate(-1)}
            >
                <ChevronLeft className="mr-2 h-4 w-4" />
                Back
            </Button>

            <article className="prose prose-zinc dark:prose-invert max-w-none">
                <h1 className="text-4xl font-bold mb-8">{reference.label}</h1>
                <div className="whitespace-pre-wrap text-foreground/90 leading-relaxed">
                    {reference.value}
                </div>
            </article>
        </div>
    );
}
