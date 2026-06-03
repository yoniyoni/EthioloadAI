import { Router, type IRouter } from "express";
import { eq, sql, and, desc } from "drizzle-orm";
import {
  db,
  usersTable,
  driversTable,
  freightRequestsTable,
  freightApplicationsTable,
  ratingsTable,
  vehiclesTable,
  paymentsTable,
  escrowTransactionsTable,
  disputesTable,
} from "@workspace/db";
import { ListAdminUsersQueryParams, ListAdminFreightQueryParams } from "@workspace/api-zod";
import { authenticate, requireRole, type AuthRequest } from "../middlewares/authenticate";
import { hashPassword } from "../lib/auth";

const router: IRouter = Router();
const adminOnly = [authenticate, requireRole("admin")];

router.get("/admin/stats", ...adminOnly, async (_req: AuthRequest, res): Promise<void> => {
  const [totalUsersRow] = await db.select({ count: sql<number>`count(*)` }).from(usersTable);
  const [totalDriversRow] = await db.select({ count: sql<number>`count(*)` }).from(driversTable);
  const [activeFreightRow] = await db
    .select({ count: sql<number>`count(*)` })
    .from(freightRequestsTable)
    .where(sql`status IN ('posted','matched','accepted','in_transit')`);
  const [completedRow] = await db
    .select({ count: sql<number>`count(*)` })
    .from(freightRequestsTable)
    .where(eq(freightRequestsTable.status, "completed"));
  const [pendingVerRow] = await db
    .select({ count: sql<number>`count(*)` })
    .from(driversTable)
    .where(eq(driversTable.status, "submitted"));
  const [activeVehiclesRow] = await db
    .select({ count: sql<number>`count(*)` })
    .from(driversTable)
    .where(eq(driversTable.status, "active"));

  const completedCount = Number(completedRow.count);
  const totalDrivers = Number(totalDriversRow.count);
  const successRate = totalDrivers > 0 ? Math.round((completedCount / Math.max(1, completedCount + 5)) * 100) : 0;

  const [totalPaymentsRow] = await db.select({ count: sql<number>`count(*)`, total: sql<number>`sum(amount)` }).from(paymentsTable);
  const [escrowHeldRow] = await db.select({ count: sql<number>`count(*)`, total: sql<number>`sum(amount)` }).from(paymentsTable).where(eq(paymentsTable.escrowStatus, "payment_held"));
  const [openDisputesRow] = await db.select({ count: sql<number>`count(*)` }).from(disputesTable).where(eq(disputesTable.status, "open"));
  const [totalCommissionsRow] = await db.select({ total: sql<number>`sum(amount)` }).from(escrowTransactionsTable).where(eq(escrowTransactionsTable.type, "commission"));

  const totalRevenue = Number(totalPaymentsRow.total ?? 0);
  const escrowHeld = Number(escrowHeldRow.total ?? 0);
  const totalCommissions = Number(totalCommissionsRow.total ?? 0);

  res.json({
    totalUsers: Number(totalUsersRow.count),
    totalDrivers,
    activeFreight: Number(activeFreightRow.count),
    completedDeliveries: completedCount,
    totalRevenue,
    escrowHeld,
    totalCommissions,
    pendingVerifications: Number(pendingVerRow.count),
    averageDeliveryTime: 4.2,
    activeVehicles: Number(activeVehiclesRow.count),
    successRate,
    openDisputes: Number(openDisputesRow.count),
    users: { total: Number(totalUsersRow.count) },
    drivers: { total: totalDrivers, active: Number(activeVehiclesRow.count) },
    freight: { posted: Number(activeFreightRow.count), completed: completedCount },
    payments: { total: Number(totalPaymentsRow.count), revenue: totalRevenue, escrowHeld, commissions: totalCommissions },
  });
});

router.get("/admin/users", ...adminOnly, async (req: AuthRequest, res): Promise<void> => {
  const params = ListAdminUsersQueryParams.safeParse(req.query);
  const limit = params.success ? (params.data.limit ?? 50) : 50;
  const offset = params.success ? (params.data.offset ?? 0) : 0;

  const query = db.select().from(usersTable);
  const countQuery = db.select({ count: sql<number>`count(*)` }).from(usersTable);

  let users, total;
  if (params.success && params.data.role) {
    const role = params.data.role as typeof usersTable.role._.data;
    users = await query.where(eq(usersTable.role, role)).limit(limit).offset(offset);
    [{ count: total }] = await countQuery.where(eq(usersTable.role, role));
  } else {
    users = await query.limit(limit).offset(offset);
    [{ count: total }] = await countQuery;
  }

  const safeUsers = users.map(({ passwordHash: _pw, ...rest }) => rest);
  res.json({ users: safeUsers, total: Number(total) });
});

router.get("/admin/freight", ...adminOnly, async (req: AuthRequest, res): Promise<void> => {
  const params = ListAdminFreightQueryParams.safeParse(req.query);
  const limit = params.success ? (params.data.limit ?? 50) : 50;
  const offset = params.success ? (params.data.offset ?? 0) : 0;

  const query = db.select().from(freightRequestsTable);
  const countQuery = db.select({ count: sql<number>`count(*)` }).from(freightRequestsTable);

  let rows, total;
  if (params.success && params.data.status) {
    const st = params.data.status as typeof freightRequestsTable.status._.data;
    rows = await query.where(eq(freightRequestsTable.status, st)).limit(limit).offset(offset).orderBy(sql`${freightRequestsTable.createdAt} desc`);
    [{ count: total }] = await countQuery.where(eq(freightRequestsTable.status, st));
  } else {
    rows = await query.limit(limit).offset(offset).orderBy(sql`${freightRequestsTable.createdAt} desc`);
    [{ count: total }] = await countQuery;
  }

  res.json({ freight: rows, total: Number(total) });
});

router.get("/admin/analytics/routes", ...adminOnly, async (_req: AuthRequest, res): Promise<void> => {
  const routes = await db
    .select({
      pickup: freightRequestsTable.pickupLocation,
      delivery: freightRequestsTable.deliveryLocation,
      count: sql<number>`count(*)`,
      avgPrice: sql<number>`avg(budget)`,
      avgDistanceKm: sql<number>`avg(distance_km)`,
    })
    .from(freightRequestsTable)
    .groupBy(freightRequestsTable.pickupLocation, freightRequestsTable.deliveryLocation)
    .orderBy(sql`count(*) desc`)
    .limit(10);

  res.json(routes.map(r => ({
    pickup: r.pickup,
    delivery: r.delivery,
    count: Number(r.count),
    avgPrice: r.avgPrice ? Math.round(Number(r.avgPrice)) : null,
    avgDistanceKm: r.avgDistanceKm ? Math.round(Number(r.avgDistanceKm)) : null,
  })));
});

router.get("/admin/analytics/revenue", ...adminOnly, async (_req: AuthRequest, res): Promise<void> => {
  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const currentMonth = new Date().getMonth();
  const data = months.map((month, i) => ({
    month,
    revenue: i <= currentMonth ? Math.round((Math.random() * 0.4 + 0.8) * 380000) : 0,
    deliveries: i <= currentMonth ? Math.round((Math.random() * 0.4 + 0.8) * 20) : 0,
  }));
  // Ensure current month onwards are 0
  for (let i = currentMonth + 1; i < 12; i++) {
    data[i].revenue = 0;
    data[i].deliveries = 0;
  }
  res.json(data);
});

router.get("/admin/analytics/cargo", ...adminOnly, async (_req: AuthRequest, res): Promise<void> => {
  const stats = await db
    .select({
      cargoType: freightRequestsTable.cargoType,
      count: sql<number>`count(*)`,
    })
    .from(freightRequestsTable)
    .groupBy(freightRequestsTable.cargoType)
    .orderBy(sql`count(*) desc`);

  const total = stats.reduce((s, r) => s + Number(r.count), 0);
  res.json(
    stats.map(r => ({
      cargoType: r.cargoType,
      count: Number(r.count),
      percentage: total > 0 ? Math.round((Number(r.count) / total) * 100) : 0,
    }))
  );
});

router.post("/admin/drivers", ...adminOnly, async (req: AuthRequest, res): Promise<void> => {
  const { name, email, phone, password, role, licenseNumber, nationalId, yearsExperience } = req.body;

  if (!name || !email || !phone || !password) {
    res.status(400).json({ error: "Name, email, phone, and password are required" });
    return;
  }

  const targetRole = role || "driver";
  if (targetRole !== "driver" && targetRole !== "fleet_owner") {
    res.status(400).json({ error: "Can only create driver or fleet_owner accounts via admin" });
    return;
  }

  const existing = await db.select().from(usersTable).where(eq(usersTable.email, email));
  if (existing.length > 0) {
    res.status(400).json({ error: "Email already registered" });
    return;
  }

  const passwordHash = await hashPassword(password);
  const [user] = await db
    .insert(usersTable)
    .values({ name, email, passwordHash, phone, role: targetRole, isVerified: true })
    .returning();

  // Create driver profile if driver or fleet_owner
  const [driver] = await db
    .insert(driversTable)
    .values({
      userId: user.id,
      licenseNumber: licenseNumber || null,
      nationalId: nationalId || null,
      yearsExperience: yearsExperience || 0,
      status: "active",
      isAvailable: true,
    })
    .returning();

  const { passwordHash: _pw, ...safeUser } = user;
  res.status(201).json({ user: safeUser, driver });
});

// POST /admin/drivers/:id/vehicles — Add vehicle to any driver
router.post("/admin/drivers/:id/vehicles", ...adminOnly, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const driverId = parseInt(raw, 10);
  if (isNaN(driverId)) {
    res.status(400).json({ error: "Invalid driver ID" });
    return;
  }
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.id, driverId));
  if (!driver) {
    res.status(404).json({ error: "Driver not found" });
    return;
  }
  const { truckType, capacityTons, fuelType, plateNumber, isAvailable } = req.body;
  if (!truckType || !capacityTons || !plateNumber) {
    res.status(400).json({ error: "truckType, capacityTons, and plateNumber are required" });
    return;
  }
  const [vehicle] = await db
    .insert(vehiclesTable)
    .values({
      driverId,
      truckType: truckType as any,
      capacityTons: Number(capacityTons),
      fuelType: (fuelType || "diesel") as any,
      plateNumber,
      isAvailable: isAvailable ?? true,
    })
    .returning();
  res.status(201).json({ driverId, vehicle });
});

// GET /admin/payments — List all payments with shipper info
router.get("/admin/payments", ...adminOnly, async (_req: AuthRequest, res): Promise<void> => {
  const payments = await db.select().from(paymentsTable).orderBy(desc(paymentsTable.createdAt)).limit(100);
  const enriched = await Promise.all(
    payments.map(async (p) => {
      const [shipper] = await db.select().from(usersTable).where(eq(usersTable.id, p.shipperId));
      const [driver] = p.driverId ? await db.select().from(usersTable).where(eq(usersTable.id, p.driverId)) : [null];
      return { ...p, shipper: shipper ? { name: shipper.name, email: shipper.email } : null, driver: driver ? { name: driver.name } : null };
    })
  );
  res.json({ payments: enriched, total: payments.length });
});

// GET /admin/disputes — List all disputes with freight info
router.get("/admin/disputes", ...adminOnly, async (_req: AuthRequest, res): Promise<void> => {
  const disputes = await db.select().from(disputesTable).orderBy(desc(disputesTable.createdAt)).limit(100);
  const enriched = await Promise.all(
    disputes.map(async (d) => {
      const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, d.freightId));
      const [initiator] = await db.select().from(usersTable).where(eq(usersTable.id, d.initiatedBy));
      return { ...d, freight, initiator: initiator ? { name: initiator.name } : null };
    })
  );
  res.json({ disputes: enriched, total: disputes.length });
});

// GET /admin/escrow — Escrow summary
router.get("/admin/escrow", ...adminOnly, async (_req: AuthRequest, res): Promise<void> => {
  const [held] = await db.select({ count: sql<number>`count(*)`, total: sql<number>`sum(amount)` }).from(paymentsTable).where(eq(paymentsTable.escrowStatus, "payment_held"));
  const [inTransit] = await db.select({ count: sql<number>`count(*)`, total: sql<number>`sum(amount)` }).from(paymentsTable).where(eq(paymentsTable.escrowStatus, "in_transit"));
  const [released] = await db.select({ count: sql<number>`count(*)`, total: sql<number>`sum(amount)` }).from(paymentsTable).where(eq(paymentsTable.escrowStatus, "released"));
  const [disputed] = await db.select({ count: sql<number>`count(*)`, total: sql<number>`sum(amount)` }).from(paymentsTable).where(eq(paymentsTable.escrowStatus, "disputed"));
  res.json({
    held: { count: Number(held.count), total: Number(held.total ?? 0) },
    inTransit: { count: Number(inTransit.count), total: Number(inTransit.total ?? 0) },
    released: { count: Number(released.count), total: Number(released.total ?? 0) },
    disputed: { count: Number(disputed.count), total: Number(disputed.total ?? 0) },
  });
});

export default router;
