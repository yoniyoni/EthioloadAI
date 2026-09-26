import { useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { useAuth } from "@/contexts/auth-context";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { AlertTriangle, Upload } from "lucide-react";

const requiredDocuments = [
  ["LICENSE", "Driver's License"], ["NATIONAL_ID", "National ID"],
  ["VEHICLE_REGISTRATION", "Vehicle Registration"], ["INSURANCE", "Insurance Certificate"],
  ["TIN", "TIN Certificate"],
] as const;

interface DocumentItem {
  documentType?: string;
  document_type?: string;
  status?: string;
  originalName?: string;
  original_name?: string;
  rejectionReason?: string | null;
  rejection_reason?: string | null;
  fileUrl?: string;
}

export default function Documents() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const inputRefs = useRef<Record<string, HTMLInputElement | null>>({});
  const [busyType, setBusyType] = useState<string | null>(null);
  const { data = [], isLoading, isError } = useQuery<DocumentItem[]>({
    queryKey: ["my-documents", user?.id],
    queryFn: () => api.get<DocumentItem[]>(`/documents/driver/${user?.id}`),
    enabled: user?.role === "driver" && !!user?.id,
  });
  const uploadMutation = useMutation({
    mutationFn: async ({ type, file }: { type: string; file: File }) => {
      if (file.size > 5 * 1024 * 1024) throw new Error("Maximum file size is 5 MB");
      if (!["application/pdf", "image/jpeg", "image/png"].includes(file.type)) throw new Error("Only PDF, JPG, and PNG files are accepted");
      const form = new FormData(); form.append("documentType", type); form.append("file", file);
      return api.upload(`/documents/upload`, form);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["my-documents", user?.id] }),
    onSettled: () => setBusyType(null),
  });
  const safeData: DocumentItem[] = Array.isArray(data) ? data : ((data as any)?.content ?? []);
  const documentsByType = useMemo(() => new Map<string, DocumentItem>(safeData.map((item) => [String(item.documentType ?? item.document_type).toUpperCase(), item])), [safeData]);
  const approvedCount = requiredDocuments.filter(([type]) => String(documentsByType.get(type)?.status ?? "").toUpperCase() === "APPROVED").length;
  if (user?.role !== "driver") return <div className="container mx-auto max-w-4xl px-4 py-16 text-center"><p className="text-lg font-medium">Documents are available from the driver account.</p></div>;
  return <div className="container mx-auto max-w-4xl px-4 py-8">
    <h1 className="text-3xl font-bold tracking-tight">My Documents / ሰነዶች</h1>
    <p className="text-muted-foreground mt-1 mb-6">{approvedCount}/5 approved. Accepted formats: JPG, PNG, PDF. Maximum 5 MB.</p>
    <div className="mb-6 flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800"><AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /><p>Documents remain pending until an administrator reviews them.</p></div>
    {isLoading && <p className="text-muted-foreground">Loading documents...</p>}{isError && <p className="text-sm text-destructive">Unable to load documents.</p>}
    <div className="grid gap-3">{requiredDocuments.map(([type, label]) => { const document = documentsByType.get(type); const status = String(document?.status ?? "NOT_UPLOADED").toUpperCase(); return <Card key={type} className="rounded-xl border-border/60"><CardHeader className="pb-2"><div className="flex flex-wrap items-center justify-between gap-2"><CardTitle className="text-base">{label}</CardTitle><Badge variant="outline">{status}</Badge></div></CardHeader><CardContent className="space-y-3"><p className="text-sm text-muted-foreground">{document?.originalName ?? "Not uploaded"}</p>{document?.rejectionReason && <p className="text-sm text-destructive">Reason: {document.rejectionReason}</p>}<input ref={(element) => { inputRefs.current[type] = element; }} className="hidden" type="file" accept=".jpg,.jpeg,.png,.pdf" onChange={(event) => { const file = event.target.files?.[0]; if (file) { setBusyType(type); uploadMutation.mutate({ type, file }); } event.currentTarget.value = ""; }} /><Button type="button" variant="outline" disabled={busyType === type} onClick={() => inputRefs.current[type]?.click()}><Upload className="mr-2 h-4 w-4" />{status === "REJECTED" ? "Re-upload" : status === "NOT_UPLOADED" ? "Upload" : "Replace"}</Button></CardContent></Card>; })}</div>
    {uploadMutation.isError && <p className="mt-4 text-sm text-destructive">{(uploadMutation.error as Error).message}</p>}
  </div>;
}
