import { useState, type ChangeEvent, type ReactNode } from "react";
import { Link } from "wouter";
import {
  ArrowUpRight,
  Bell,
  Building2,
  ChevronRight,
  ClipboardList,
  History as HistoryIcon,
  LayoutDashboard,
  LogOut,
  Menu,
  Package,
  Truck,
  Users,
} from "lucide-react";
import { LanguageSwitcher } from "@/components/language-switcher";
import { NotificationBell } from "@/components/notification-bell";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { useAuth } from "@/contexts/auth-context";
import { useLocation } from "wouter";

type Props = {
  children: ReactNode;
  location: string;
};

type NavItem = {
  label: string;
  href: string;
  icon: typeof LayoutDashboard;
};

function isActive(location: string, href: string) {
  return location === href || location.startsWith(`${href}/`);
}

function selectedCompanyId(location: string) {
  const match = location.match(
    /^\/(?:company\/(\d+)|carrier-offers\/company\/(\d+)(?:\/freight\/\d+)?|carrier-dispatch\/company\/(\d+)|carrier\/(\d+)\/settlements(?:\/\d+)?)$/,
  );
  return match?.slice(1).find(Boolean);
}

function companyDestination(location: string, membership: { carrierCompanyId: number; role: string }) {
  const companyId = membership.carrierCompanyId;
  if (/^\/company\/\d+$/.test(location)) return `/company/${companyId}`;
  if (/^\/carrier-offers\/company\/\d+(?:\/freight\/\d+)?$/.test(location)) {
    return `/carrier-offers/company/${companyId}`;
  }
  if (/^\/carrier-dispatch\/company\/\d+$/.test(location)) {
    return `/carrier-dispatch/company/${companyId}`;
  }
  if (/^\/carrier\/\d+\/settlements(?:\/\d+)?$/.test(location) && membership.role === "CARRIER_OWNER") {
    return `/carrier/${companyId}/settlements`;
  }
  return `/company/${companyId}`;
}

function pageContext(location: string, companyId?: string, companyName?: string) {
  if (location === "/dashboard") return ["Workspaces", "Company workspaces"];
  if (location === "/fleet" || location === "/fleet/companies") return ["Companies", "My companies"];
  if (location.startsWith("/fleet/companies/")) return ["Companies", "Company application"];
  if (location === "/fleet/overview") return ["Fleet", "Overview"];
  if (location === "/fleet/drivers") return ["Fleet", "Drivers"];
  if (location === "/fleet/vehicles") return ["Fleet", "Vehicles"];
  if (location === "/bookings") return ["Operations", "Bookings"];
  if (location === "/history") return ["Operations", "Fleet history"];
  if (location === "/notifications") return ["Operations", "Notifications"];
  if (location === "/vehicles") return ["Operations", "My vehicles"];
  if (location.startsWith("/carrier-offers/company/")) return [companyName ?? `Company #${companyId}`, "Freight & offers"];
  if (location === "/carrier-dispatch") return ["Companies", "Choose a dispatch workspace"];
  if (location.startsWith("/carrier-dispatch")) return [companyName ?? `Company #${companyId}`, "Dispatch"];
  if (location.startsWith("/carrier/")) return [companyName ?? `Company #${companyId}`, "Settlements"];
  return [companyName ?? `Company #${companyId}`, "Workspace"];
}

function NavigationLink({
  item,
  location,
  onNavigate,
}: {
  item: NavItem;
  location: string;
  onNavigate?: () => void;
}) {
  const active = isActive(location, item.href);
  const Icon = item.icon;

  return (
    <Link
      href={item.href}
      aria-current={active ? "page" : undefined}
      onClick={onNavigate}
      className={`group flex min-h-11 min-w-0 items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${
        active
          ? "bg-sidebar-accent text-sidebar-accent-foreground"
          : "text-sidebar-foreground/75 hover:bg-sidebar-accent/70 hover:text-sidebar-foreground"
      }`}
    >
      <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
      <span className="min-w-0 flex-1 break-words">{item.label}</span>
      {active && <ChevronRight className="h-4 w-4 shrink-0 text-sidebar-primary" aria-hidden="true" />}
    </Link>
  );
}

export function FleetOwnerShell({ children, location }: Props) {
  const { user, logout, memberships } = useAuth();
  const [, setLocation] = useLocation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const companyId = selectedCompanyId(location);
  const companyMembership = companyId
    ? memberships.find((membership) => membership.carrierCompanyId === Number(companyId) && membership.status === "ACTIVE")
    : undefined;
  const context = pageContext(location, companyId, companyMembership?.companyName);

  const fleetItems: NavItem[] = [
    { label: "Personal fleet overview", href: "/fleet/overview", icon: LayoutDashboard },
    { label: "Personal fleet drivers", href: "/fleet/drivers", icon: Users },
    { label: "Personal fleet vehicles", href: "/fleet/vehicles", icon: Truck },
  ];
  const companyItems: NavItem[] = [
    { label: "My companies", href: "/fleet/companies", icon: Building2 },
  ];
  const marketplaceItem: NavItem = {
    label: "Freight Marketplace",
    href: "/freight",
    icon: Package,
  };
  const operationsItems: NavItem[] = [
    { label: "Bookings", href: "/bookings", icon: Package },
    { label: "My vehicles", href: "/vehicles", icon: Truck },
    { label: "Fleet history", href: "/history", icon: HistoryIcon },
    { label: "Notifications", href: "/notifications", icon: Bell },
  ];
  const selectedCompanyItems: NavItem[] = [];

  if (companyId && companyMembership) {
    selectedCompanyItems.push({
      label: `${companyMembership.companyName || `Company #${companyId}`} workspace`,
      href: `/company/${companyId}`,
      icon: Building2,
    });
    if (["CARRIER_OWNER", "FLEET_MANAGER", "DISPATCHER"].includes(companyMembership.role)) {
      selectedCompanyItems.push({
        label: "Company Offers",
        href: `/carrier-offers/company/${companyId}`,
        icon: Package,
      });
      selectedCompanyItems.push({
        label: "Dispatch",
        href: `/carrier-dispatch/company/${companyId}`,
        icon: ClipboardList,
      });
    }
    if (companyMembership.role === "CARRIER_OWNER") {
      selectedCompanyItems.push({
        label: "Settlements",
        href: `/carrier/${companyId}/settlements`,
        icon: ArrowUpRight,
      });
    }
  }

  const dashboardItem: NavItem = {
    label: "Company workspaces",
    href: "/dashboard",
    icon: LayoutDashboard,
  };

  const chooseCompany = (event: ChangeEvent<HTMLSelectElement>, onNavigate?: () => void) => {
    const membership = memberships.find(
      (candidate) => String(candidate.carrierCompanyId) === event.currentTarget.value && candidate.status === "ACTIVE",
    );
    if (!membership) return;
    setLocation(companyDestination(location, membership));
    onNavigate?.();
  };

  const renderNavigation = (idPrefix: string, onNavigate?: () => void) => (
    <nav aria-label="Fleet Owner navigation" className="space-y-6">
      {memberships.length > 0 && (
        <section aria-labelledby={`${idPrefix}-workspaces-heading`}>
          <h2
            id={`${idPrefix}-workspaces-heading`}
            className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-sidebar-foreground/55"
          >
            Workspaces
          </h2>
          <NavigationLink item={dashboardItem} location={location} onNavigate={onNavigate} />
          <label className="mt-3 block px-3">
            <span className="sr-only">Switch company workspace</span>
            <select
              value={companyMembership ? String(companyMembership.carrierCompanyId) : ""}
              onChange={(event) => chooseCompany(event, onNavigate)}
              className="min-h-11 w-full rounded-md border border-sidebar-border bg-background px-3 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <option value="">Select a company</option>
              {memberships.filter((membership) => membership.status === "ACTIVE").map((membership) => (
                <option key={membership.carrierCompanyId} value={membership.carrierCompanyId}>
                  {membership.companyName || `Company #${membership.carrierCompanyId}`}
                </option>
              ))}
            </select>
          </label>
        </section>
      )}
      <section aria-labelledby={`${idPrefix}-marketplace-heading`}>
        <h2
          id={`${idPrefix}-marketplace-heading`}
          className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-sidebar-foreground/55"
        >
          Explore
        </h2>
        <NavigationLink item={marketplaceItem} location={location} onNavigate={onNavigate} />
      </section>
      <section aria-labelledby={`${idPrefix}-companies-heading`}>
        <h2
          id={`${idPrefix}-companies-heading`}
          className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-sidebar-foreground/55"
        >
          Companies
        </h2>
        <NavigationLink item={companyItems[0]} location={location} onNavigate={onNavigate} />
      </section>
      {selectedCompanyItems.length > 0 && (
        <section aria-labelledby={`${idPrefix}-selected-company-heading`}>
          <h2
            id={`${idPrefix}-selected-company-heading`}
            className="mb-2 truncate px-3 text-xs font-semibold uppercase tracking-wider text-sidebar-foreground/55"
            title={`Selected company ${companyMembership?.companyName ?? `#${companyId}`}`}
          >
            {companyMembership?.companyName ?? `Selected company · #${companyId}`}
          </h2>
          <div className="space-y-1">
            {selectedCompanyItems.map((item) => (
              <NavigationLink key={item.href} item={item} location={location} onNavigate={onNavigate} />
            ))}
          </div>
        </section>
      )}
      <section aria-labelledby={`${idPrefix}-fleet-heading`}>
        <h2
          id={`${idPrefix}-fleet-heading`}
          className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-sidebar-foreground/55"
        >
          Personal fleet (legacy)
        </h2>
        <div className="space-y-1">
          {fleetItems.map((item) => (
            <NavigationLink key={item.href} item={item} location={location} onNavigate={onNavigate} />
          ))}
        </div>
      </section>
      <section aria-labelledby={`${idPrefix}-operations-heading`}>
        <h2
          id={`${idPrefix}-operations-heading`}
          className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-sidebar-foreground/55"
        >
          Operations
        </h2>
        <div className="space-y-1">
          {operationsItems.map((item) => (
            <NavigationLink key={item.href} item={item} location={location} onNavigate={onNavigate} />
          ))}
        </div>
      </section>
    </nav>
  );

  const signOut = () => {
    logout();
    setLocation("/");
  };

  return (
    <div className="flex min-h-[100dvh] bg-background text-foreground">
      <aside aria-label="Fleet Owner sidebar" className="hidden h-dvh min-h-0 w-64 shrink-0 border-r border-sidebar-border bg-sidebar text-sidebar-foreground lg:flex lg:flex-col">
        <Link
          href="/fleet/companies"
          className="flex min-h-16 items-center gap-3 border-b border-sidebar-border px-5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-sidebar-ring"
          aria-label="Fleet Owner home — My companies"
        >
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-sidebar-primary text-sidebar-primary-foreground">
            <Truck className="h-5 w-5" aria-hidden="true" />
          </span>
          <span className="min-w-0">
            <span className="block truncate text-sm font-semibold">ETHIO-FREIGHT</span>
            <span className="block truncate text-xs text-sidebar-foreground/60">Fleet Owner</span>
          </span>
        </Link>
        <div className="min-h-0 flex-1 overflow-y-auto px-3 py-5">{renderNavigation("desktop")}</div>
        <div className="border-t border-sidebar-border p-4">
          <Link
            href="/profile"
            className="mb-2 block truncate rounded-md px-2 py-1 text-sm font-medium hover:text-sidebar-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring"
            title={user?.name ?? "Profile"}
          >
            {user?.name ?? "Profile"}
          </Link>
          <Button
            type="button"
            variant="ghost"
            className="min-h-11 w-full justify-start gap-3 text-sidebar-foreground/75 hover:bg-sidebar-accent hover:text-sidebar-foreground"
            onClick={signOut}
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            Sign out
          </Button>
        </div>
      </aside>

      <div className="flex min-h-[100dvh] min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex min-h-16 items-center gap-3 border-b bg-background/95 px-3 backdrop-blur supports-[backdrop-filter]:bg-background/80 sm:px-5 lg:px-7">
          <Sheet open={mobileMenuOpen} onOpenChange={setMobileMenuOpen}>
            <SheetTrigger asChild>
              <Button
                type="button"
                variant="outline"
                size="icon"
                className="h-11 w-11 shrink-0 lg:hidden"
                aria-label="Open Fleet Owner navigation"
              >
                <Menu className="h-5 w-5" aria-hidden="true" />
              </Button>
            </SheetTrigger>
            <SheetContent
              side="left"
              className="flex w-[min(20rem,calc(100vw-1rem))] flex-col gap-0 border-sidebar-border bg-sidebar p-0 text-sidebar-foreground"
            >
              <SheetTitle className="sr-only">Fleet Owner navigation</SheetTitle>
              <SheetDescription className="sr-only">
                Navigate to fleet management, companies, and the selected company workspace.
              </SheetDescription>
              <Link
                href="/fleet/companies"
                onClick={() => setMobileMenuOpen(false)}
                className="flex min-h-16 items-center gap-3 border-b px-5 pr-14 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
              >
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary text-primary-foreground">
                  <Truck className="h-5 w-5" aria-hidden="true" />
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-sm font-semibold">ETHIO-FREIGHT</span>
                  <span className="block truncate text-xs text-muted-foreground">Fleet Owner</span>
                </span>
              </Link>
              <div className="min-h-0 flex-1 overflow-y-auto px-3 py-5">
                {renderNavigation("mobile", () => setMobileMenuOpen(false))}
              </div>
              <div className="border-t p-4">
                <Link
                  href="/profile"
                  onClick={() => setMobileMenuOpen(false)}
                  className="mb-2 block min-h-11 truncate rounded-md px-2 py-3 text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  {user?.name ?? "Profile"}
                </Link>
                <Button
                  type="button"
                  variant="outline"
                  className="min-h-11 w-full justify-start gap-3"
                  onClick={() => {
                    setMobileMenuOpen(false);
                    signOut();
                  }}
                >
                  <LogOut className="h-4 w-4" aria-hidden="true" />
                  Sign out
                </Button>
              </div>
            </SheetContent>
          </Sheet>

          <nav aria-label="Current page" className="min-w-0 flex-1">
            <ol className="flex min-w-0 items-center gap-2 text-sm">
              <li className="min-w-0 truncate text-muted-foreground" title={context[0]}>
                {context[0]}
              </li>
              <li aria-hidden="true" className="shrink-0 text-muted-foreground">/</li>
              <li aria-current="page" className="min-w-0 truncate font-medium" title={context[1]}>
                {context[1]}
              </li>
            </ol>
          </nav>

          <div className="flex shrink-0 items-center gap-1 sm:gap-2">
            <LanguageSwitcher />
            <NotificationBell />
          </div>
        </header>
        <div className="min-w-0 flex-1 py-4 sm:py-6">
          {children}
        </div>
      </div>
    </div>
  );
}
