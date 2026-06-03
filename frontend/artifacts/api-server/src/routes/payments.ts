import { Router, type IRouter } from "express";
import { eq, and, desc } from "drizzle-orm";
import {
  db,
  paymentsTable,
  escrowTransactionsTable,
  freightRequestsTable,
  driversTable,
  usersTable,
} from "@workspace/db";
import { authenticate, requireRole, type AuthRequest } from "../middlewares/authenticate";

const router: IRouter = Router();

// Platform commission rate (e.g., 10%)
const PLATFORM_COMMISSION_RATE = 0.10;

async function freightWithPayment(freightId: number) {
  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, freightId));
  if (!freight) return null;
  const [payment] = await db.select().from(paymentsTable).where(eq(paymentsTable.freightId, freightId));
  return { ...freight, payment: payment || null };
}

// POST /payments/initialize — Initialize escrow payment for a freight
router.post("/payments/initialize", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const { freightId, amount, provider } = req.body;
  if (!freightId || !amount || !provider) {
    res.status(400).json({ error: "freightId, amount, and provider are required" });
    return;
  }

  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, Number(freightId)));
  if (!freight) {
    res.status(404).json({ error: "Freight not found" });
    return;
  }
  if (freight.shipperId !== req.userId) {
    res.status(403).json({ error: "Only the shipper can initialize payment" });
    return;
  }
  if (freight.status !== "accepted" && freight.status !== "matched") {
    res.status(400).json({ error: "Freight must be matched or accepted before payment" });
    return;
  }

  // Check for existing payment
  const [existing] = await db.select().from(paymentsTable).where(eq(paymentsTable.freightId, Number(freightId)));
  if (existing) {
    res.status(409).json({ error: "Payment already exists for this freight", payment: existing });
    return;
  }

  const platformCommission = Math.round(Number(amount) * PLATFORM_COMMISSION_RATE);
  const driverAmount = Number(amount) - platformCommission;

  const [payment] = await db
    .insert(paymentsTable)
    .values({
      freightId: Number(freightId),
      shipperId: req.userId!,
      driverId: freight.matchedDriverId,
      amount: Number(amount),
      platformCommission,
      driverAmount,
      provider: provider as any,
      status: "pending",
      escrowStatus: "pending_payment",
      currency: "ETB",
    })
    .returning();

  // Update freight status to reflect payment pending
  await db.update(freightRequestsTable).set({ status: "accepted" }).where(eq(freightRequestsTable.id, Number(freightId)));

  // In a real implementation, this would redirect to the payment provider
  const providerUrl = getPaymentProviderUrl(provider, payment.id, amount);

  res.status(201).json({
    payment,
    checkoutUrl: providerUrl,
    message: "Payment initialized. Please complete payment via provider.",
  });
});

// POST /payments/verify — Verify payment callback (webhook or manual)
router.post("/payments/verify", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const { paymentId, providerTransactionId } = req.body;
  if (!paymentId || !providerTransactionId) {
    res.status(400).json({ error: "paymentId and providerTransactionId are required" });
    return;
  }

  const [payment] = await db.select().from(paymentsTable).where(eq(paymentsTable.id, Number(paymentId)));
  if (!payment) {
    res.status(404).json({ error: "Payment not found" });
    return;
  }

  // In real implementation, verify with provider API
  const isVerified = await verifyWithProvider(payment.provider, providerTransactionId);
  if (!isVerified) {
    res.status(400).json({ error: "Payment verification failed" });
    return;
  }

  // Update payment status
  const [updated] = await db
    .update(paymentsTable)
    .set({
      status: "completed",
      escrowStatus: "payment_held",
      providerTransactionId,
      updatedAt: new Date(),
    })
    .where(eq(paymentsTable.id, Number(paymentId)))
    .returning();

  // Create escrow transaction record
  await db.insert(escrowTransactionsTable).values({
    paymentId: Number(paymentId),
    freightId: payment.freightId,
    type: "escrow_deposit",
    amount: payment.amount,
    fromParty: "shipper",
    toParty: "escrow",
    status: "completed",
    notes: `Payment of ${payment.amount} ETB held in escrow`,
  });

  // Update freight status to payment_held
  await db.update(freightRequestsTable).set({ status: "in_transit" }).where(eq(freightRequestsTable.id, payment.freightId));

  res.json({ payment: updated, message: "Payment verified and held in escrow" });
});

// POST /payments/release — Release escrow to driver (after delivery)
router.post("/payments/release", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const { freightId } = req.body;
  if (!freightId) {
    res.status(400).json({ error: "freightId is required" });
    return;
  }

  const [payment] = await db.select().from(paymentsTable).where(eq(paymentsTable.freightId, Number(freightId)));
  if (!payment) {
    res.status(404).json({ error: "No payment found for this freight" });
    return;
  }

  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, Number(freightId)));
  if (!freight) {
    res.status(404).json({ error: "Freight not found" });
    return;
  }

  // Only shipper or admin can release
  if (freight.shipperId !== req.userId && req.userRole !== "admin") {
    res.status(403).json({ error: "Only shipper or admin can release payment" });
    return;
  }

  if (payment.escrowStatus !== "delivered" && payment.escrowStatus !== "in_transit") {
    res.status(400).json({ error: "Payment cannot be released. Freight must be delivered first." });
    return;
  }

  // Update payment status
  const [updated] = await db
    .update(paymentsTable)
    .set({
      escrowStatus: "released",
      status: "completed",
      updatedAt: new Date(),
    })
    .where(eq(paymentsTable.id, payment.id))
    .returning();

  // Create escrow release transaction
  await db.insert(escrowTransactionsTable).values({
    paymentId: payment.id,
    freightId: payment.freightId,
    type: "escrow_release",
    amount: payment.driverAmount,
    fromParty: "escrow",
    toParty: "driver",
    status: "completed",
    notes: `Released ${payment.driverAmount} ETB to driver after delivery`,
  });

  // Create commission transaction
  await db.insert(escrowTransactionsTable).values({
    paymentId: payment.id,
    freightId: payment.freightId,
    type: "commission",
    amount: payment.platformCommission,
    fromParty: "escrow",
    toParty: "platform",
    status: "completed",
    notes: `Platform commission ${payment.platformCommission} ETB`,
  });

  // Update freight status
  await db.update(freightRequestsTable).set({ status: "completed" }).where(eq(freightRequestsTable.id, Number(freightId)));

  // Update driver stats
  if (payment.driverId) {
    const [driver] = await db.select().from(driversTable).where(eq(driversTable.id, payment.driverId));
    if (driver) {
      await db.update(driversTable).set({
        totalDeliveries: driver.totalDeliveries + 1,
        successRate: Math.round(((driver.totalDeliveries + 1) / Math.max(1, driver.totalDeliveries + 1 + 2)) * 100),
      }).where(eq(driversTable.id, payment.driverId));
    }
  }

  res.json({ payment: updated, message: "Payment released to driver and commission deducted" });
});

// GET /payments/:freightId — Get payment for a freight
router.get("/payments/:freightId", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.freightId) ? req.params.freightId[0] : req.params.freightId;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) { res.status(400).json({ error: "Invalid ID" }); return; }

  const [payment] = await db.select().from(paymentsTable).where(eq(paymentsTable.freightId, freightId));
  if (!payment) {
    res.status(404).json({ error: "No payment found" });
    return;
  }
  res.json(payment);
});

// GET /payments — List all payments (admin)
router.get("/payments", authenticate, requireRole("admin", "support"), async (req: AuthRequest, res): Promise<void> => {
  const payments = await db.select().from(paymentsTable).orderBy(desc(paymentsTable.createdAt));
  res.json({ payments, total: payments.length });
});

// GET /escrow/transactions/:freightId — Get escrow transactions for freight
router.get("/escrow/transactions/:freightId", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.freightId) ? req.params.freightId[0] : req.params.freightId;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) { res.status(400).json({ error: "Invalid ID" }); return; }
  const transactions = await db
    .select()
    .from(escrowTransactionsTable)
    .where(eq(escrowTransactionsTable.freightId, freightId))
    .orderBy(desc(escrowTransactionsTable.createdAt));
  res.json(transactions);
});

// Helper functions
function getPaymentProviderUrl(provider: string, paymentId: number, amount: number): string {
  // Placeholder for real provider integration
  if (provider === "chapa") {
    return `https://checkout.chapa.co/checkout/${paymentId}?amount=${amount}&currency=ETB`;
  }
  return `/api/payments/${paymentId}/pay`;
}

async function verifyWithProvider(provider: string, transactionId: string): Promise<boolean> {
  // Placeholder for real provider verification
  // In production, this calls the actual payment provider API
  return true;
}

// GET /payments/reminders — Check for pending payments (for escrow enforcement)
router.get("/payments/reminders", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const userFreight = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.shipperId, req.userId!));
  const freightIds = userFreight.filter(f => f.status === "matched").map(f => f.id);
  if (freightIds.length === 0) {
    res.json({ reminders: [] });
    return;
  }
  const payments = await db.select().from(paymentsTable).where(eq(paymentsTable.freightId, freightIds[0]));
  const paidFreightIds = new Set(payments.map(p => p.freightId));
  const reminders = freightIds
    .filter(id => !paidFreightIds.has(id))
    .map(id => {
      const f = userFreight.find(uf => uf.id === id);
      return { freightId: id, origin: f?.pickupLocation, destination: f?.deliveryLocation, cargoType: f?.cargoType, urgency: "high" };
    });
  res.json({ reminders });
});

export default router;
