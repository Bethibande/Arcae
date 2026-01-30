import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card.tsx";

export default function AccessTokensView() {
    return (
        <div className="p-6">
            <Card>
                <CardHeader>
                    <CardTitle>Access Tokens</CardTitle>
                    <CardDescription>Manage your personal access tokens for API access.</CardDescription>
                </CardHeader>
                <CardContent>
                    <p className="text-sm text-muted-foreground">Placeholder for Access Tokens.</p>
                </CardContent>
            </Card>
        </div>
    )
}
