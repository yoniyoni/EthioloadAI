import { Router, type IRouter } from "express";
import { eq, desc } from "drizzle-orm";
import {
  db,
  disputesTable,
  paymentsTable,
  freightRequestsTable,
  escrowTransactionsTable,
  usersTable,
  driversTable,
} from "@workspace/db";
import { authenticate, requireRole, type AuthRequest } from "../middlewares/authenticate";

const router: IRouter = Router();

// POST /disputes — File a dispute
router.post("/disputes", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const { freightId, reason, description, evidence } = req.body;
  if (!freightId || !reason) {
    res.status(400).json({ error: "freightId and reason are required" });
    return;
  }

  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, Number(freightId)));
  if (!freight) {
    res.status(404).json({ error: "Freight not found" });
    return;
  }

  // Only shipper or matched driver can dispute
  if (freight.shipperId !== req.userId && req.userRole !== "admin") {
    const [driver] = await db.select().from(driversTable).where(eq(driversTable.userId, req.userId!));
    if (!driver || driver.id !== freight.matchedDriverId) {
      res.status(403).json({ error: "Only shipper, matched driver, or admin can file a dispute" });
      return;
    }
  }

  // Find payment
  const [payment] = await db.select().from(paymentsTable).where(eq(paymentsTable.freightId, Number(freightId)));
  if (!payment) {
    res.status(400).json({ error: "No payment found for this freight" });
    return;
  }

  // Check if dispute already exists
  const [existing] = await db.select().from(disputesTable).where(eq(disputesTable.freightId, Number(freightId)));
  if (existing && existing.status !== "closed") {
    res.status(409).json({ error: "Dispute already open for this freight", dispute: existing });
    return;
  }

  // Lock escrow status
  await db
    .update(paymentsTable)
    .set({ escrowStatus: "disputed", updatedAt: new Date() })
    .where(eq(paymentsTable.id, payment.id));

  // Hold freight status
  await db.update(freightRequestsTable).set({ status: "delivered" }).where(eq(freightRequestsTable.id, Number(freightId)));

  const [dispute] = await db
    .insert(disputesTable)
    .values({
      freightId: Number(freightId),
      paymentId: payment.id,
      initiatedBy: req.userId!,
      reason,
      description: description || null,
      evidence: evidence || null,
      status: "open",
    })
    .returning();

  res.status(201).json({
    dispute,
    message: "Dispute filed successfully. Funds are locked until resolved.",
  });
});

// GET /disputes — List disputes (admin)
router.get("/disputes", authenticate, requireRole("admin", "support"), async (req: AuthRequest, res): Promise<void> => {
  const disputes = await db.select().from(disputesTable).orderBy(desc(disputesTable.createdAt));

  const enriched = await Promise.all(
    disputes.map(async (d) => {
      const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, d.freightId));
      const [initiator] = await db.select().from(usersTable).where(eq(usersTable.id, d.initiatedBy));
      const [resolver] = d.resolvedBy ? await db.select().from(usersTable).where(eq(usersTable.id, d.resolvedBy)) : [null];
      return { ...d, freight, initiator: initiator ? { name: initiator.name, email: initiator.email } : null, resolver: resolver ? { name: resolver.name } : null };
    })
  );

  res.json({ disputes: enriched, total: disputes.length });
});

// GET /disputes/my — List disputes for current user
router.get("/disputes/my", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const userFreight = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.shipperId, req.userId!));
  const freightIds = userFreight.map((f) => f.id);
  const disputes = await db
    .select()
    .from(disputesTable)
    .where(eq(disputesTable.initiatedBy, req.userId!))
    .orderBy(desc(disputesTable.createdAt));
  res.json({ disputes, total: disputes.length });
});

// GET /disputes/:id — Get dispute details
router.get("/disputes/:id", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const id = parseInt(raw, 10);
  if (isNaN(id)) { res.status(400).json({ error: "Invalid ID" }); return; }

  const [dispute] = await db.select().from(disputesTable).where(eq(disputesTable.id, id));
  if (!dispute) { res.status(404).json({ error: "Dispute not found" }); return; }

  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, dispute.freightId));
  const [payment] = dispute.paymentId ? await db.select().from(paymentsTable).where(eq(paymentsTable.id, dispute.paymentId)) : [null];
  res.json({ ...dispute, freight, payment });
});

// POST /disputes/:id/resolve — Resolve dispute (admin only)
router.post("/disputes/:id/resolve", authenticate, requireRole("admin", "support"), async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const id = parseInt(raw, 10);
  if (isNaN(id)) { res.status(400).json({ error: "Invalid ID" }); return; }

  const { resolution, resolutionNotes, refundAmount, driverAmount } = req.body;
  if (!resolution) {
    res.status(400).json({ error: "resolution is required" });
    return;
  }

  const [dispute] = await db.select().from(disputesTable).where(eq(disputesTable.id, id));
  if (!dispute) { res.status(404).json({ error: "Dispute not found" }); return; }
  if (dispute.status !== "open" && dispute.status !== "under_review") {
    res.status(400).json({ error: "Dispute is already resolved or closed" });
    return;
  }

  const [payment] = dispute.paymentId ? await db.select().from(paymentsTable).where(eq(paymentsTable.id, dispute.paymentId)) : [null];
  if (!payment) {
    res.status(404).json({ error: "Payment not found" });
    return;
  }

  // Update dispute
  const [updated] = await db
    .update(disputesTable)
    .set({
      status: "resolved",
      resolution: resolution as any,
      resolutionNotes: resolutionNotes || null,
      refundAmount: refundAmount ? Number(refundAmount) : null,
      driverAmount: driverAmount ? Number(driverAmount) : null,
      resolvedBy: req.userId!,
      resolvedAt: new Date(),
    })
    .where(eq(disputesTable.id, id))
    .returning();

  // Handle resolution actions
  if (resolution === "release_to_driver") {
    await db.update(paymentsTable).set({ escrowStatus: "released", status: "completed", updatedAt: new Date() }).where(eq(paymentsTable.id, payment.id));
    await db.insert(escrowTransactionsTable).values({
      paymentId: payment.id,
      freightId: payment.freightId,
      type: "escrow_release",
      amount: payment.driverAmount,
      fromParty: "escrow",
      toParty: "driver",
      status: "completed",
      notes: "Released to driver after dispute resolution",
    });
    await db.insert(escrowTransactionsTable).values({
      paymentId: payment.id,
      freightId: payment.freightId,
      type: "commission",
      amount: payment.platformCommission,
      fromParty: "escrow",
      toParty: "platform",
      status: "completed",
      notes: "Platform commission after dispute resolution",
    });
  } else if (resolution === "refund_to_shipper") {
    await db.update(paymentsTable).set({ escrowStatus: "refunded", status: "refunded", updatedAt: new Date() }).where(eq(paymentsTable.id, payment.id));
    await db.insert(escrowTransactionsTable).values({
      paymentId: payment.id,
      freightId: payment.freightId,
      type: "refund",
      amount: payment.amount,
      fromParty: "escrow",
      toParty: "shipper",
      status: "completed",
      notes: "Full refund to shipper after dispute resolution",
    });
  } else if (resolution === "split_payment") {
    const shipperRefund = refundAmount ? Number(refundAmount) : Math.round(payment.amount * 0.5);
    const driverPayout = driverAmount ? Number(driverAmount) : payment.amount - shipperRefund;
    await db.update(paymentsTable).set({ escrowStatus: "split", status: "completed", updatedAt: new Date() }).where(eq(paymentsTable.id, payment.id));
    await db.insert(escrowTransactionsTable).values({
      paymentId: payment.id,
      freightId: payment.freightId,
      type: "refund",
      amount: shipperRefund,
      fromParty: "escrow",
      toParty: "shipper",
      status: "completed",
      notes: `Partial refund to shipper: ${shipperRefund} ETB`,
    });
    await db.insert(escrowTransactionsTable).values({
      paymentId: payment.id,
      freightId: payment.freightId,
      type: "escrow_release",
      amount: driverPayout,
      fromParty: "escrow",
      toParty: "driver",
      status: "completed",
      notes: `Partial payment to driver: ${driverPayout} ETB`,
    });
  }

  // Update freight status
  await db.update(freightRequestsTable).set({ status: "completed" }).where(eq(freightRequestsTable.id, payment.freightId));

  res.json({ dispute: updated, message: `Dispute resolved: ${resolution}` });
});

export default router;
