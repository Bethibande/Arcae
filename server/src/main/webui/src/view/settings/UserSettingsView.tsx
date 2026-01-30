import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card.tsx";

export default function UserSettingsView() {
    return (
        <div className="p-6">
            <Card>
                <CardHeader>
                    <CardTitle>User Settings</CardTitle>
                    <CardDescription>Manage your profile and personal preferences.</CardDescription>
                </CardHeader>
                <CardContent>
                    <p className="text-sm text-muted-foreground">Placeholder for User Settings.</p>
                </CardContent>
            </Card>
        </div>
    )
}
