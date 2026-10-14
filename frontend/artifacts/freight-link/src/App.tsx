import { Switch, Route, Router as WouterRouter } from "wouter";
import React from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "@/lib/query-client";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AuthProvider } from "@/contexts/auth-context";
import { LanguageProvider } from "@/lib/i18n/language-context";
import { Layout } from "@/components/layout";
import { ProtectedRoute } from "@/components/protected-route";
import { AIChatWidget } from "@/components/ai-chat-widget";

import Home from "@/pages/home";
import Login from "@/pages/login";
import Register from "@/pages/register";
import InvitationAccept from "@/pages/invitation-accept";
import CompanyWorkspace from "@/pages/company-workspace";
import Dashboard from "@/pages/dashboard";
import FreightList from "@/pages/freight-list";
import FreightDetail from "@/pages/freight-detail";
import FreightNew from "@/pages/freight-new";
import Drivers from "@/pages/drivers";
import DriverDetail from "@/pages/driver-detail";
import Vehicles from "@/pages/vehicles";
import Profile from "@/pages/profile";
import Admin from "@/pages/admin";
import Payment from "@/pages/payment";
import Contract from "@/pages/contract";
import Tracking from "@/pages/tracking";
import Dispute from "@/pages/dispute";
import Messages from "@/pages/messages";
import Bids from "@/pages/bids";
import Documents from "@/pages/documents";
import Bookings from "@/pages/bookings";
import Notifications from "@/pages/notifications";
import History from "@/pages/history";
import Fleet from "@/pages/fleet";
import CarrierCompanies from "@/pages/carrier-companies";
import AdminCompanies from "@/pages/admin-companies";
import CarrierOffers from "@/pages/carrier-offers";
import CarrierDispatch from "@/pages/carrier-dispatch";
import ProofVerification from "@/pages/proof-verification";
import CarrierSettlement from "@/pages/carrier-settlement";
import NotFound from "@/pages/not-found";

function Router() {
  return (
    <Layout>
      <Switch>
        <Route path="/" component={Home} />
        <Route path="/login" component={Login} />
        <Route path="/register" component={Register} />
        <Route path="/invitations/accept" component={InvitationAccept} />
        <Route path="/company/:id">{params => <ProtectedRoute><CompanyWorkspace id={params.id} /></ProtectedRoute>}</Route>

        <Route path="/freight/new">
          <ProtectedRoute allowedRoles={["shipper", "admin"]}>
            <FreightNew />
          </ProtectedRoute>
        </Route>
        <Route path="/freight/:id" component={FreightDetail} />
        <Route path="/freight" component={FreightList} />

        <Route path="/bids">
          <ProtectedRoute allowedRoles={["driver"]}>
            <Bids />
          </ProtectedRoute>
        </Route>

        <Route path="/documents">
          <ProtectedRoute allowedRoles={["driver"]}>
            <Documents />
          </ProtectedRoute>
        </Route>

        <Route path="/bookings">
          <ProtectedRoute allowedRoles={["shipper", "driver", "fleet_owner", "admin"]}>
            <Bookings />
          </ProtectedRoute>
        </Route>

        <Route path="/notifications">
          <ProtectedRoute allowedRoles={["driver", "shipper", "fleet_owner"]}>
            <Notifications />
          </ProtectedRoute>
        </Route>

        <Route path="/history">
          <ProtectedRoute allowedRoles={["driver", "shipper", "fleet_owner"]}>
            <History />
          </ProtectedRoute>
        </Route>

        <Route path="/tracking">
          <ProtectedRoute allowedRoles={["shipper", "driver"]}>
            <Tracking />
          </ProtectedRoute>
        </Route>

        <Route path="/dashboard">
          <ProtectedRoute>
            <Dashboard />
          </ProtectedRoute>
        </Route>

        <Route path="/fleet/companies/:id">{params => <ProtectedRoute><CarrierCompanies key={params.id} id={params.id} /></ProtectedRoute>}</Route>
        <Route path="/fleet/companies"><ProtectedRoute><CarrierCompanies /></ProtectedRoute></Route>
        <Route path="/admin/companies"><ProtectedRoute allowedRoles={["admin"]}><AdminCompanies /></ProtectedRoute></Route>

        {/* Phase 5 — Carrier Offer routes */}
        <Route path="/carrier-offers/freight/:freightId">
          {params => (
            <ProtectedRoute allowedRoles={["shipper", "admin"]}>
              <CarrierOffers freightId={params.freightId} />
            </ProtectedRoute>
          )}
        </Route>
        <Route path="/carrier-offers/company/:companyId/freight/:freightId">
          {params => (
            <ProtectedRoute>
              <CarrierOffers companyId={params.companyId} freightId={params.freightId} />
            </ProtectedRoute>
          )}
        </Route>
        <Route path="/carrier-offers/company/:companyId">
          {params => (
            <ProtectedRoute>
              <CarrierOffers companyId={params.companyId} />
            </ProtectedRoute>
          )}
        </Route>
        <Route path="/freight/:freightId/trips/:tripId/proof"><ProtectedRoute><ProofVerification /></ProtectedRoute></Route>
        <Route path="/carrier/:companyId/settlements/:id"><ProtectedRoute><CarrierSettlement /></ProtectedRoute></Route>
        <Route path="/carrier/:companyId/settlements"><ProtectedRoute><CarrierSettlement /></ProtectedRoute></Route>
        {/* Phase 6 — Carrier Dispatch routes */}
        <Route path="/carrier-dispatch/company/:companyId">
          {params => (
            <ProtectedRoute>
              <CarrierDispatch key={params.companyId} />
            </ProtectedRoute>
          )}
        </Route>
        <Route path="/carrier-dispatch">
          <ProtectedRoute>
            <CarrierDispatch />
          </ProtectedRoute>
        </Route>

        <Route path="/fleet">
          <ProtectedRoute allowedRoles={["fleet_owner"]}>
            <Dashboard />
          </ProtectedRoute>
        </Route>
        <Route path="/fleet/drivers">
          <ProtectedRoute allowedRoles={["fleet_owner"]}>
            <Dashboard />
          </ProtectedRoute>
        </Route>
        <Route path="/fleet/vehicles">
          <ProtectedRoute allowedRoles={["fleet_owner"]}>
            <Dashboard />
          </ProtectedRoute>
        </Route>

        <Route path="/drivers">
          <ProtectedRoute allowedRoles={["admin"]}>
            <Drivers />
          </ProtectedRoute>
        </Route>
        <Route path="/drivers/:id">
          <ProtectedRoute allowedRoles={["admin"]}>
            <DriverDetail />
          </ProtectedRoute>
        </Route>
        <Route path="/admin/drivers/:id">
          <ProtectedRoute allowedRoles={["admin"]}>
            <DriverDetail />
          </ProtectedRoute>
        </Route>

        <Route path="/vehicles">
          <ProtectedRoute allowedRoles={["driver", "fleet_owner", "admin"]}>
            <Vehicles />
          </ProtectedRoute>
        </Route>

        <Route path="/profile">
          <ProtectedRoute>
            <Profile />
          </ProtectedRoute>
        </Route>

        <Route path="/admin">
          <ProtectedRoute allowedRoles={["admin"]}>
            <Admin />
          </ProtectedRoute>
        </Route>

        <Route path="/payment/:id">
          <ProtectedRoute>
            <Payment />
          </ProtectedRoute>
        </Route>
        <Route path="/contract/:id">
          <ProtectedRoute>
            <Contract />
          </ProtectedRoute>
        </Route>
        <Route path="/tracking/:id">
          <ProtectedRoute>
            <Tracking />
          </ProtectedRoute>
        </Route>
        <Route path="/dispute/:id">
          <ProtectedRoute>
            <Dispute />
          </ProtectedRoute>
        </Route>
        <Route path="/messages/:id">
          <ProtectedRoute>
            <Messages />
          </ProtectedRoute>
        </Route>

        <Route component={NotFound} />
      </Switch>
    </Layout>
  );
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <LanguageProvider>
          <AuthProvider>
            <AppExperience />
          </AuthProvider>
          <Toaster />
        </LanguageProvider>
      </TooltipProvider>
    </QueryClientProvider>
  );
}

function AppExperience() {
  return (
    <>
      <WouterRouter base={import.meta.env.BASE_URL.replace(/\/$/, "")}>
        <Router />
      </WouterRouter>
      <AIChatWidget />
    </>
  );
}

export default App;
