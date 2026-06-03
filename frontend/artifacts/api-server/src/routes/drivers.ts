import { Router, type IRouter } from "express";
import { eq, and, sql } from "drizzle-orm";
import { db, driversTable, usersTable, vehiclesTable } from "@workspace/db";
import {
  CreateDriverProfileBody,
  ListDriversQueryParams,
  UpdateDriverStatusBody,
} from "@workspace/api-zod";
import { authenticate, requireRole, type AuthRequest } from "../middlewares/authenticate";

const router: IRouter = Router();

async function driverWithRelations(driverId: number) {
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.id, driverId));
  if (!driver) return null;
  const [user] = await db.select().from(usersTable).where(eq(usersTable.id, driver.userId));
  const vehicles = await db.select().from(vehiclesTable).where(eq(vehiclesTable.driverId, driver.id));
  const { passwordHash: _pw, ...safeUser } = user ?? {};
  return { ...driver, user: safeUser, vehicles };
}

router.post("/drivers/profile", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const parsed = CreateDriverProfileBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const existing = await db.select().from(driversTable).where(eq(driversTable.userId, req.userId!));
  let driverId: number;
  if (existing.length > 0) {
    const [updated] = await db
      .update(driversTable)
      .set({ ...parsed.data, status: "submitted" })
      .where(eq(driversTable.userId, req.userId!))
      .returning();
    driverId = updated.id;
  } else {
    const [created] = await db
      .insert(driversTable)
      .values({ ...parsed.data, userId: req.userId!, status: "submitted" })
      .returning();
    driverId = created.id;
  }
  const result = await driverWithRelations(driverId);
  res.status(201).json(result);
});

router.get("/drivers", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const params = ListDriversQueryParams.safeParse(req.query);
  const limit = params.success ? (params.data.limit ?? 20) : 20;
  const offset = params.success ? (params.data.offset ?? 0) : 0;

  const conditions = [];
  if (params.success && params.data.status) {
    conditions.push(eq(driversTable.status, params.data.status as typeof driversTable.status._.data));
  }
  if (params.success && params.data.available !== undefined) {
    conditions.push(eq(driversTable.isAvailable, params.data.available));
  }

  const query = db.select().from(driversTable);
  const drivers = conditions.length > 0
    ? await query.where(and(...conditions)).limit(limit).offset(offset)
    : await query.limit(limit).offset(offset);

  const total = conditions.length > 0
    ? (await db.select({ count: sql<number>`count(*)` }).from(driversTable).where(and(...conditions)))[0].count
    : (await db.select({ count: sql<number>`count(*)` }).from(driversTable))[0].count;

  const enriched = await Promise.all(drivers.map(d => driverWithRelations(d.id)));
  res.json({ drivers: enriched.filter(Boolean), total: Number(total) });
});

router.get("/drivers/me", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.userId, req.userId!));
  if (!driver) {
    res.status(404).json({ error: "No driver profile found" });
    return;
  }
  const result = await driverWithRelations(driver.id);
  res.json(result);
});

router.get("/drivers/my-applications", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.userId, req.userId!));
  if (!driver) {
    res.status(404).json({ error: "No driver profile found" });
    return;
  }
  // This is handled in applications route but we need to export it here for routing
  res.json([]);
});

router.get("/drivers/:id", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const id = parseInt(raw, 10);
  if (isNaN(id)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  const result = await driverWithRelations(id);
  if (!result) {
    res.status(404).json({ error: "Driver not found" });
    return;
  }
  res.json(result);
});

router.patch(
  "/drivers/:id/status",
  authenticate,
  requireRole("admin"),
  async (req: AuthRequest, res): Promise<void> => {
    const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
    const id = parseInt(raw, 10);
    if (isNaN(id)) {
      res.status(400).json({ error: "Invalid ID" });
      return;
    }
    const parsed = UpdateDriverStatusBody.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({ error: parsed.error.message });
      return;
    }
    const updateData: Record<string, unknown> = { status: parsed.data.status };
    if (parsed.data.notes) updateData.statusNotes = parsed.data.notes;
    if (parsed.data.status === "active") updateData.isAvailable = true;
    await db.update(driversTable).set(updateData).where(eq(driversTable.id, id));
    const result = await driverWithRelations(id);
    if (!result) {
      res.status(404).json({ error: "Driver not found" });
      return;
    }
    res.json(result);
  }
);

export default router;
