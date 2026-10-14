import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "wouter";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/hooks/use-toast";
import { CheckCircle2, ClipboardList, RefreshCw, Truck, XCircle } from "lucide-react";

type Assignment = {
  id: number;
  freightId: number;
  vehicleId: number;
  status: string;
  agreedAmount: number;
  notes?: string | null;
  rejectionReason?: string | null;
  createdAt?: string;
  assignedAt: string;
  bookingId?: number | null;
};

export default function DriverAssignments() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const assignments = useQuery({
    queryKey: ["driver-assignments"],
    queryFn: () => api.get<Assignment[]>("/driver/assignments"),
    refetchInterval: 15000,
  });
  const respond = useMutation({
    mutationFn: ({ id, decision }: { id: number; decision: "accept" | "reject" }) =>
      api.post(`/driver/assignments/${id}/${decision}`, decision === "reject" ? { rejectionReason: "Declined by driver" } : {}),
    onSuccess: (updated: Assignment, variables) => {
      toast({ title: variables.decision === "accept" ? "Assignment accepted" : "Assignment declined", description: variables.decision === "accept" ? "Your booking is ready. Start the trip from Bookings when you are ready to depart." : "The dispatcher can assign another driver." });
      void queryClient.invalidateQueries({ queryKey: ["driver-assignments"] });
      void queryClient.invalidateQueries({ queryKey: ["bookings"] });
      if (updated.bookingId) window.location.href = "/bookings";
    },
    onError: (error: Error) => toast({ title: "Could not update assignment", description: error.message, variant: "destructive" }),
  });

  return <main className="container mx-auto max-w-4xl space-y-6 px-4 py-7 sm:px-6">
    <header className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-sm font-medium text-primary">Driver workspace</p><h1 className="mt-1 text-3xl font-bold tracking-tight">My assignments</h1><p className="mt-2 text-muted-foreground">Review dispatch requests, respond, then start your accepted trip from Bookings.</p></div><Button variant="outline" onClick={() => void assignments.refetch()} disabled={assignments.isFetching}><RefreshCw className={`mr-2 h-4 w-4 ${assignments.isFetching ? "animate-spin" : ""}`} /> Refresh</Button></header>
    {assignments.error && <p role="alert" className="rounded-xl bg-destructive/10 p-4 text-sm text-destructive">{assignments.error.message}</p>}
    {assignments.isLoading ? <div className="h-32 animate-pulse rounded-2xl bg-muted" /> : !assignments.data?.length ? <Card className="rounded-2xl border-dashed"><CardContent className="flex flex-col items-center px-6 py-12 text-center"><ClipboardList className="h-10 w-10 text-muted-foreground"/><h2 className="mt-4 text-lg font-semibold">No dispatch requests yet</h2><p className="mt-1 max-w-md text-sm text-muted-foreground">When your company assigns you a shipment, it will appear here. The app refreshes this list automatically.</p></CardContent></Card> : <section className="grid gap-4 sm:grid-cols-2">{assignments.data.map(item => <Card key={item.id} className="overflow-hidden rounded-2xl"><div className={`h-1.5 ${item.status === "ASSIGNED" ? "bg-amber-400" : item.status === "DRIVER_ACCEPTED" ? "bg-emerald-500" : "bg-muted-foreground/30"}`} /><CardHeader className="pb-3"><div className="flex items-center justify-between gap-2"><CardTitle className="text-lg">Shipment #{item.freightId}</CardTitle><Badge variant={item.status === "DRIVER_ACCEPTED" ? "default" : "outline"}>{item.status.replaceAll("_", " ")}</Badge></div><CardDescription>Assigned {new Date(item.assignedAt ?? item.createdAt ?? "").toLocaleString()}</CardDescription></CardHeader><CardContent className="space-y-4"><div className="grid grid-cols-2 gap-3"><div className="rounded-xl bg-muted/50 p-3"><p className="text-xs text-muted-foreground">Agreed amount</p><p className="mt-1 font-semibold">ETB {Number(item.agreedAmount).toLocaleString()}</p></div><div className="rounded-xl bg-muted/50 p-3"><p className="text-xs text-muted-foreground">Assigned vehicle</p><p className="mt-1 flex items-center gap-1 font-semibold"><Truck className="h-4 w-4"/> #{item.vehicleId}</p></div></div>{item.notes && <p className="rounded-xl border p-3 text-sm">{item.notes}</p>}{item.rejectionReason && <p className="text-sm text-destructive">Reason: {item.rejectionReason}</p>}{item.status === "ASSIGNED" ? <div className="flex flex-wrap gap-2"><Button className="gap-2" disabled={respond.isPending} onClick={() => respond.mutate({ id: item.id, decision: "accept" })}><CheckCircle2 className="h-4 w-4"/> Accept assignment</Button><Button variant="outline" className="gap-2" disabled={respond.isPending} onClick={() => respond.mutate({ id: item.id, decision: "reject" })}><XCircle className="h-4 w-4"/> Decline</Button></div> : item.bookingId ? <Button asChild className="w-full"><Link href="/bookings">Open booking #{item.bookingId} · Start trip</Link></Button> : null}</CardContent></Card>)}</section>}
  </main>;
}
