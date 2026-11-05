import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  getLoginRedirect,
  getPostLoginDestination,
  getSafeReturnTo,
} from "../src/lib/auth-redirect.js";

const source = path => readFile(new URL(path, import.meta.url), "utf8");

test("private freight destinations survive login only as safe internal paths", () => {
  const destination = "/freight?status=posted&page=2#results";
  const login = getLoginRedirect(destination);
  assert.equal(login, `/login?returnTo=${encodeURIComponent(destination)}`);
  assert.equal(
    getPostLoginDestination(`?returnTo=${encodeURIComponent(destination)}`, "shipper", false),
    destination,
  );
  assert.equal(getSafeReturnTo("https://example.com/freight"), undefined);
  assert.equal(getSafeReturnTo("//example.com/freight"), undefined);
  assert.equal(getSafeReturnTo("/\\example.com/freight"), undefined);
  assert.equal(getPostLoginDestination("?returnTo=%2F%2Fexample.com", "driver", false), "/dashboard");
  assert.equal(getPostLoginDestination("", "admin", false), "/admin");
  assert.equal(getPostLoginDestination("?returnTo=%2Ffreight", "shipper", true), "/freight");
  assert.equal(getPostLoginDestination("", "shipper", true), "/invitations/accept");
});

test("freight list and detail routes are guarded before their page components mount", async () => {
  const app = await source("../src/App.tsx");
  assert.match(app, /<Route path="\/freight\/:id">[\s\S]*?<ProtectedRoute>[\s\S]*?<FreightDetail \/>/);
  assert.match(app, /<Route path="\/freight">[\s\S]*?<ProtectedRoute>[\s\S]*?<FreightList \/>/);

  const guard = await source("../src/components/protected-route.tsx");
  assert.match(guard, /getLoginRedirect\(`\$\{location\}\$\{search\}\$\{window\.location\.hash\}`\)/);
});

test("freight queries remain disabled until authentication is established", async () => {
  const listing = await source("../src/pages/freight-list.tsx");
  const detail = await source("../src/pages/freight-detail.tsx");
  assert.match(listing, /enabled:\s*isAuthenticated/);
  assert.match(detail, /enabled:\s*isAuthenticated/);
});

test("public Welcome content contains no marketplace promotions or freight preloads", async () => {
  const home = await source("../src/pages/home.tsx");
  const translations = await source("../src/lib/i18n/translations.ts");
  const privateExamples = [
    "Live Freight Activity",
    "Industrial Cement",
    "Coffee Export",
    "ETB 42,500",
    "18,500",
    "15,900",
    "21,000",
    "EF-99281-AD",
    "12,000+",
    "2,400+",
    "4.8M",
    "850+",
    "99.2%",
    "12,500 kg",
    "ETB 45,000",
    "ETB 1.2M",
    "127 deliveries",
    "Addis Ababa",
    "Dire Dawa",
    "Hawassa",
    "Bahir Dar",
    "Mekelle",
    "Addis Trading Co.",
    "AgroFresh Ethiopia",
    "Dawit Transports",
  ];

  for (const example of privateExamples) {
    assert.equal(home.includes(example), false, `Welcome page still contains ${example}`);
  }
  for (const metric of ["12,000+", "2,400+", "4.8M", "850+", "99.2%"]) {
    assert.equal(translations.includes(metric), false, `Public translation bundle still contains ${metric}`);
  }
  assert.doesNotMatch(home, /\b(?:from|to|price|status):\s*["']/i);
  assert.doesNotMatch(home, /\b(?:api\.get|fetch\(|useQuery|useInfiniteQuery)\b/);
  assert.doesNotMatch(home, /marketplace|carrier offers|view live loads|explore loads|href="\/freight"/i);
});

test("authenticated marketplace navigation and company-offer access remain separate", async () => {
  const layout = await source("../src/components/layout.tsx");
  const fleetOwnerShell = await source("../src/components/fleet-owner-shell.tsx");
  const app = await source("../src/App.tsx");
  const sharedMarketplaceItem = layout.slice(
    layout.indexOf("const marketplaceItem"),
    layout.indexOf("const workspaceItems"),
  );
  const fleetOwnerMarketplaceItem = fleetOwnerShell.slice(
    fleetOwnerShell.indexOf("const marketplaceItem"),
    fleetOwnerShell.indexOf("const operationsItems"),
  );

  assert.match(layout, /const marketplaceItem:[\s\S]*?href: '\/freight'/);
  assert.match(layout, /isAuthenticated\s*\?\s*\{ label: t\('nav\.marketplace'\)/);
  assert.doesNotMatch(sharedMarketplaceItem, /memberships/);
  assert.match(layout, /href: `\/carrier-offers\/company\/\$\{membership\.carrierCompanyId\}`/);
  assert.match(layout, /membership\.status === 'ACTIVE'/);
  assert.match(fleetOwnerShell, /label: "Freight Marketplace",\s*href: "\/freight"/);
  assert.doesNotMatch(fleetOwnerMarketplaceItem, /memberships/);
  assert.match(fleetOwnerShell, /label: "Company Offers",\s*href: `\/carrier-offers\/company\/\$\{companyId\}`/);
  assert.match(fleetOwnerShell, /membership\.status === "ACTIVE"/);
  assert.match(app, /<Route path="\/freight">[\s\S]*?<ProtectedRoute>[\s\S]*?<FreightList \/>/);
  assert.match(app, /ApprovedCompanyRoute companyId=\{params\.companyId\} allowedRoles=\{\["CARRIER_OWNER", "FLEET_MANAGER", "DISPATCHER"\]\}/);
});

test("login and logout clear cached user data to prevent cross-account freight reuse", async () => {
  const authContext = await source("../src/contexts/auth-context.tsx");
  assert.match(authContext, /const clear = useCallback\([\s\S]*?queryClient\.clear\(\);\s*setUser\(null\)/);
  assert.match(authContext, /const login=\([\s\S]*?queryClient\.clear\(\);setUser\(current\)/);
  assert.match(authContext, /getLoginRedirect\(returnTo\)/);
});
