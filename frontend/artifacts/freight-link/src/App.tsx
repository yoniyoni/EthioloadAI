import { Switch, Route, Router as WouterRouter } from "wouter";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
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
import Dashboard from "@/pages/dashboard";
import FreightList from "@/pages/freight-list";
import FreightDetail from "@/pages/freight-detail";
import FreightNew from "@/pages/freight-new";
import Drivers from "@/pages/drivers";
import Vehicles from "@/pages/vehicles";
import Profile from "@/pages/profile";
import Admin from "@/pages/admin";
import Payment from "@/pages/payment";
import Contract from "@/pages/contract";
import Tracking from "@/pages/tracking";
import Dispute from "@/pages/dispute";
import Messages from "@/pages/messages";
import NotFound from "@/pages/not-found";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
});

function Router() {
  return (
    <Layout>
      <Switch>
        <Route path="/" component={Home} />
        <Route path="/login" component={Login} />
        <Route path="/register" component={Register} />

        <Route path="/freight/new">
          <ProtectedRoute allowedRoles={["shipper", "admin"]}>
            <FreightNew />
          </ProtectedRoute>
        </Route>
        <Route path="/freight/:id" component={FreightDetail} />
        <Route path="/freight" component={FreightList} />

        <Route path="/dashboard">
          <ProtectedRoute>
            <Dashboard />
          </ProtectedRoute>
        </Route>

        <Route path="/drivers">
          <ProtectedRoute allowedRoles={["admin", "shipper"]}>
            <Drivers />
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
            <WouterRouter base={import.meta.env.BASE_URL.replace(/\/$/, "")}>
              <Router />
            </WouterRouter>
            <AIChatWidget />
          </AuthProvider>
          <Toaster />
        </LanguageProvider>
      </TooltipProvider>
    </QueryClientProvider>
  );
}

export default App;
