import { Router, type IRouter } from "express";
import { eq, desc } from "drizzle-orm";
import { db, contractsTable, freightRequestsTable, driversTable, usersTable, paymentsTable, escrowTransactionsTable } from "@workspace/db";
import { authenticate, requireRole, type AuthRequest } from "../middlewares/authenticate";

const router: IRouter = Router();

// POST /contracts/:freightId/generate — Generate a contract when both parties agree
router.post("/contracts/:freightId/generate", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.freightId) ? req.params.freightId[0] : req.params.freightId;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) { res.status(400).json({ error: "Invalid ID" }); return; }

  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, freightId));
  if (!freight) { res.status(404).json({ error: "Freight not found" }); return; }

  if (freight.shipperId !== req.userId && req.userRole !== "admin") {
    res.status(403).json({ error: "Only shipper or admin can generate contract" });
    return;
  }

  if (!freight.matchedDriverId) {
    res.status(400).json({ error: "No driver matched yet" });
    return;
  }

  const [existing] = await db.select().from(contractsTable).where(eq(contractsTable.freightId, freightId));
  if (existing) {
    res.status(409).json({ error: "Contract already exists", contract: existing });
    return;
  }

  const [driver] = await db.select().from(driversTable).where(eq(driversTable.id, freight.matchedDriverId));
  const [driverUser] = driver ? await db.select().from(usersTable).where(eq(usersTable.id, driver.userId)) : [null];

  const [contract] = await db
    .insert(contractsTable)
    .values({
      freightId,
      driverId: freight.matchedDriverId,
      shipperId: freight.shipperId,
      agreedPrice: freight.budget ?? 0,
      status: "active",
      pickupLocation: freight.pickupLocation,
      deliveryLocation: freight.deliveryLocation,
      deadline: freight.deadline,
      paymentStatus: "pending",
    })
    .returning();

  // Update freight status
  await db.update(freightRequestsTable).set({ status: "accepted" }).where(eq(freightRequestsTable.id, freightId));

  res.status(201).json({
    contract,
    freight,
    driver: driver ? { ...driver, user: driverUser ? { name: driverUser.name, phone: driverUser.phone } : null } : null,
    message: "Contract generated successfully. Proceed to payment escrow.",
  });
});

// GET /contracts/:freightId — Get contract for freight
router.get("/contracts/:freightId", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.freightId) ? req.params.freightId[0] : req.params.freightId;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) { res.status(400).json({ error: "Invalid ID" }); return; }

  const [contract] = await db.select().from(contractsTable).where(eq(contractsTable.freightId, freightId));
  if (!contract) { res.status(404).json({ error: "Contract not found" }); return; }

  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, freightId));
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.id, contract.driverId));
  const [shipper] = await db.select().from(usersTable).where(eq(usersTable.id, contract.shipperId));

  res.json({
    ...contract,
    freight,
    driver: driver ? { ...driver, user: shipper ? { name: shipper.name } : null } : null,
    shipper: shipper ? { name: shipper.name, email: shipper.email, phone: shipper.phone } : null,
  });
});

// GET /contracts — List all contracts (admin)
router.get("/contracts", authenticate, requireRole("admin", "support"), async (_req: AuthRequest, res): Promise<void> => {
  const contracts = await db.select().from(contractsTable).orderBy(desc(contractsTable.createdAt));
  const enriched = await Promise.all(
    contracts.map(async (c) => {
      const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, c.freightId));
      const [shipper] = await db.select().from(usersTable).where(eq(usersTable.id, c.shipperId));
      return { ...c, freight, shipper: shipper ? { name: shipper.name } : null };
    })
  );
  res.json({ contracts: enriched, total: contracts.length });
});

// POST /freight/:id/deliver — Driver marks delivery as completed
router.post("/freight/:id/deliver", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) { res.status(400).json({ error: "Invalid ID" }); return; }

  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, freightId));
  if (!freight) { res.status(404).json({ error: "Freight not found" }); return; }

  // Verify driver
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.userId, req.userId!));
  if (!driver || driver.id !== freight.matchedDriverId) {
    res.status(403).json({ error: "Only matched driver can mark delivery" });
    return;
  }

  if (freight.status !== "in_transit") {
    res.status(400).json({ error: "Freight must be in transit to mark delivery" });
    return;
  }

  // Update freight status to delivered
  await db.update(freightRequestsTable).set({ status: "delivered" }).where(eq(freightRequestsTable.id, freightId));

  // Update payment escrow status
  await db.update(paymentsTable).set({ escrowStatus: "delivered", updatedAt: new Date() }).where(eq(paymentsTable.freightId, freightId));

  // Update contract
  await db.update(contractsTable).set({ status: "completed", paymentStatus: "completed" }).where(eq(contractsTable.freightId, freightId));

  res.json({ message: "Delivery marked as completed. Waiting for shipper confirmation.", freightId });
});

// POST /freight/:id/confirm-delivery — Shipper confirms delivery
router.post("/freight/:id/confirm-delivery", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) { res.status(400).json({ error: "Invalid ID" }); return; }

  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, freightId));
  if (!freight) { res.status(404).json({ error: "Freight not found" }); return; }

  if (freight.shipperId !== req.userId && req.userRole !== "admin") {
    res.status(403).json({ error: "Only shipper or admin can confirm delivery" });
    return;
  }

  if (freight.status !== "delivered") {
    res.status(400).json({ error: "Freight must be delivered before confirmation" });
    return;
  }

  // Update freight status
  await db.update(freightRequestsTable).set({ status: "completed" }).where(eq(freightRequestsTable.id, freightId));

  // Auto-release payment
  const [payment] = await db.select().from(paymentsTable).where(eq(paymentsTable.freightId, freightId));
  if (payment) {
    await db.update(paymentsTable).set({ escrowStatus: "released", status: "completed", updatedAt: new Date() }).where(eq(paymentsTable.id, payment.id));

    // Record transactions
    await db.insert(escrowTransactionsTable).values({
      paymentId: payment.id,
      freightId,
      type: "escrow_release",
      amount: payment.driverAmount,
      fromParty: "escrow",
      toParty: "driver",
      status: "completed",
      notes: `Released ${payment.driverAmount} ETB to driver after delivery confirmation`,
    });
    await db.insert(escrowTransactionsTable).values({
      paymentId: payment.id,
      freightId,
      type: "commission",
      amount: payment.platformCommission,
      fromParty: "escrow",
      toParty: "platform",
      status: "completed",
      notes: `Platform commission ${payment.platformCommission} ETB`,
    });
  }

  res.json({ message: "Delivery confirmed. Payment released to driver.", freightId });
});

export default router;
