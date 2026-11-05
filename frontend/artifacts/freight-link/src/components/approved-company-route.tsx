import type { ReactNode } from "react";
import { Link } from "wouter";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/contexts/auth-context";
import { api } from "@/lib/api";
import type { Company } from "@/lib/carrier-companies";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

export function ApprovedCompanyRoute({
  companyId: requestedId,
  allowedRoles,
  children,
}: {
  companyId: string;
  allowedRoles?: string[];
  children: ReactNode;
}) {
  const { user, memberships, membershipError, membershipRevalidating, refreshMemberships } = useAuth();
  const parsedId = Number(requestedId);
  const companyId = Number.isSafeInteger(parsedId) && parsedId > 0 && String(parsedId) === requestedId
    ? parsedId
    : undefined;
  const membership = companyId
    ? memberships.find(item => item.carrierCompanyId === companyId && item.status === "ACTIVE")
    : undefined;
  const roleAllowed = !!membership && (!allowedRoles || allowedRoles.includes(membership.role));
  const company = useQuery({
    queryKey: ["approved-company-route", user?.id, membership?.carrierCompanyId],
    queryFn: async () => {
      if (!membership) throw new Error("An active company membership is required.");
      const result = await api.get<Company>(`/carrier-companies/${membership.carrierCompanyId}`);
      if (result.id !== membership.carrierCompanyId) throw new Error("The company response did not match the selected workspace.");
      return result;
    },
    enabled: roleAllowed && !membershipRevalidating && !membershipError,
    staleTime: 0,
    refetchOnWindowFocus: true,
  });

  if (!membership) {
    return (
      <main className="container mx-auto max-w-5xl px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
        <Card><CardContent className="space-y-3 p-5 sm:p-6">
          <h1 className="text-xl font-semibold">Company access needed</h1>
          <p className="text-sm leading-6 text-muted-foreground">{membershipError || "Choose a company where your account has an active membership to continue."}</p>
          {membershipError && <Button type="button" variant="outline" className="min-h-11" onClick={() => void refreshMemberships().catch(() => undefined)}>Retry company access</Button>}
          <Button asChild className="min-h-11"><Link href="/dashboard">Choose a company</Link></Button>
        </CardContent></Card>
      </main>
    );
  }

  if (membershipRevalidating) {
    return <main className="container mx-auto max-w-5xl space-y-4 px-3 py-5 sm:px-5 sm:py-7 lg:px-7" aria-label="Refreshing company access"><div className="h-7 w-48 animate-pulse rounded bg-muted" /><div className="h-28 animate-pulse rounded-xl border bg-muted/30" /></main>;
  }

  if (membershipError) {
    return (
      <main className="container mx-auto max-w-5xl px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
        <Card><CardContent className="space-y-3 p-5 sm:p-6">
          <h1 className="text-xl font-semibold">Unable to verify company access</h1>
          <p role="alert" className="text-sm leading-6 text-destructive">{membershipError}</p>
          <Button type="button" variant="outline" className="min-h-11" onClick={() => void refreshMemberships().catch(() => undefined)}>Retry company access</Button>
        </CardContent></Card>
      </main>
    );
  }

  if (!roleAllowed) {
    return (
      <main className="container mx-auto max-w-5xl px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
        <Card><CardContent className="space-y-3 p-5 sm:p-6">
          <h1 className="text-xl font-semibold">This workspace is not available to your role</h1>
          <p className="text-sm leading-6 text-muted-foreground">Your active company role does not have access to this operation.</p>
          <Button asChild className="min-h-11"><Link href={`/company/${membership.carrierCompanyId}`}>Company workspace</Link></Button>
        </CardContent></Card>
      </main>
    );
  }

  if (company.isLoading || company.isFetching) {
    return <main className="container mx-auto max-w-5xl space-y-4 px-3 py-5 sm:px-5 sm:py-7 lg:px-7" aria-label="Checking company access"><div className="h-7 w-48 animate-pulse rounded bg-muted" /><div className="h-28 animate-pulse rounded-xl border bg-muted/30" /></main>;
  }

  if (company.isError) {
    return (
      <main className="container mx-auto max-w-5xl px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
        <Card><CardContent className="space-y-3 p-5 sm:p-6">
          <h1 className="text-xl font-semibold">Unable to verify company access</h1>
          <p role="alert" className="text-sm leading-6 text-destructive">{company.error.message}</p>
          <Button type="button" variant="outline" className="min-h-11" onClick={() => void company.refetch()}>Retry</Button>
        </CardContent></Card>
      </main>
    );
  }

  if (company.data?.verificationStatus !== "APPROVED") {
    return (
      <main className="container mx-auto max-w-5xl px-3 py-5 sm:px-5 sm:py-7 lg:px-7">
        <Card><CardContent className="space-y-3 p-5 sm:p-6">
          <h1 className="text-xl font-semibold">Company operations are unavailable</h1>
          <p className="text-sm leading-6 text-muted-foreground">This company is not approved for operational activity. Current status: {company.data?.verificationStatus ?? "unknown"}.</p>
          <Button asChild variant="outline" className="min-h-11"><Link href={`/company/${membership.carrierCompanyId}`}>View company workspace</Link></Button>
        </CardContent></Card>
      </main>
    );
  }

  return children;
}
