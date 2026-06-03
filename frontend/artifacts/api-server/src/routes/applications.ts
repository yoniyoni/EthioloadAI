import { Router, type IRouter } from "express";
import { eq, and } from "drizzle-orm";
import {
  db,
  freightApplicationsTable,
  freightRequestsTable,
  driversTable,
  usersTable,
  vehiclesTable,
} from "@workspace/db";
import {
  ApplyForFreightBody,
  UpdateApplicationStatusBody,
} from "@workspace/api-zod";
import { authenticate, type AuthRequest } from "../middlewares/authenticate";

const router: IRouter = Router();

async function appWithRelations(appId: number) {
  const [app] = await db.select().from(freightApplicationsTable).where(eq(freightApplicationsTable.id, appId));
  if (!app) return null;
  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, app.freightId));
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.id, app.driverId));
  const vehicles = driver
    ? await db.select().from(vehiclesTable).where(eq(vehiclesTable.driverId, driver.id))
    : [];
  const driverUser = driver
    ? await db.select().from(usersTable).where(eq(usersTable.id, driver.userId)).then(r => r[0])
    : null;
  const safeDriverUser = driverUser ? (({ passwordHash: _pw, ...rest }) => rest)(driverUser) : null;
  const shipperUser = freight
    ? await db.select().from(usersTable).where(eq(usersTable.id, freight.shipperId)).then(r => r[0])
    : null;
  const safeShipperUser = shipperUser ? (({ passwordHash: _pw, ...rest }) => rest)(shipperUser) : null;
  return {
    ...app,
    freight: freight ? { ...freight, shipper: safeShipperUser } : null,
    driver: driver ? { ...driver, user: safeDriverUser, vehicles } : null,
  };
}

router.get("/freight/:id/applications", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  const apps = await db.select().from(freightApplicationsTable).where(eq(freightApplicationsTable.freightId, freightId));
  const enriched = await Promise.all(apps.map(a => appWithRelations(a.id)));
  res.json(enriched.filter(Boolean));
});

router.post("/freight/:id/apply", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  const parsed = ApplyForFreightBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.userId, req.userId!));
  if (!driver) {
    res.status(400).json({ error: "Must have a driver profile to apply" });
    return;
  }
  const [app] = await db
    .insert(freightApplicationsTable)
    .values({ freightId, driverId: driver.id, ...parsed.data })
    .returning();
  // Update freight status to matched
  await db.update(freightRequestsTable).set({ status: "matched" }).where(eq(freightRequestsTable.id, freightId));
  const result = await appWithRelations(app.id);
  res.status(201).json(result);
});

router.patch("/applications/:id/status", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const appId = parseInt(raw, 10);
  if (isNaN(appId)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  const parsed = UpdateApplicationStatusBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const [app] = await db
    .update(freightApplicationsTable)
    .set({ status: parsed.data.status })
    .where(eq(freightApplicationsTable.id, appId))
    .returning();
  if (!app) {
    res.status(404).json({ error: "Application not found" });
    return;
  }
  // If accepted, update freight status and set matched driver
  if (parsed.data.status === "accepted") {
    await db
      .update(freightRequestsTable)
      .set({ status: "accepted", matchedDriverId: app.driverId })
      .where(eq(freightRequestsTable.id, app.freightId));
    // Reject all other pending applications
    await db
      .update(freightApplicationsTable)
      .set({ status: "rejected" })
      .where(
        and(
          eq(freightApplicationsTable.freightId, app.freightId),
          eq(freightApplicationsTable.status, "pending")
        )
      );
  }
  const result = await appWithRelations(app.id);
  res.json(result);
});

router.get("/drivers/my-applications", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.userId, req.userId!));
  if (!driver) { res.json({ applications: [] }); return; }
  const apps = await db.select().from(freightApplicationsTable).where(eq(freightApplicationsTable.driverId, driver.id));
  const enriched = await Promise.all(apps.map(a => appWithRelations(a.id)));
  res.json({ applications: enriched.filter(Boolean) });
});

router.get("/applications/my", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.userId, req.userId!));
  if (!driver) { res.json({ applications: [] }); return; }
  const apps = await db.select().from(freightApplicationsTable).where(eq(freightApplicationsTable.driverId, driver.id));
  const enriched = await Promise.all(apps.map(a => appWithRelations(a.id)));
  res.json({ applications: enriched.filter(Boolean) });
});

router.get("/applications/freight/:id", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) { res.status(400).json({ error: "Invalid ID" }); return; }
  const apps = await db.select().from(freightApplicationsTable).where(eq(freightApplicationsTable.freightId, freightId));
  const enriched = await Promise.all(apps.map(a => appWithRelations(a.id)));
  res.json({ applications: enriched.filter(Boolean) });
});

router.patch("/applications/:id/accept", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const appId = parseInt(raw, 10);
  if (isNaN(appId)) { res.status(400).json({ error: "Invalid ID" }); return; }
  const [app] = await db
    .update(freightApplicationsTable)
    .set({ status: "accepted" })
    .where(eq(freightApplicationsTable.id, appId))
    .returning();
  if (!app) { res.status(404).json({ error: "Application not found" }); return; }
  await db.update(freightRequestsTable).set({ status: "matched", matchedDriverId: app.driverId }).where(eq(freightRequestsTable.id, app.freightId));
  await db.update(freightApplicationsTable).set({ status: "rejected" }).where(and(eq(freightApplicationsTable.freightId, app.freightId), eq(freightApplicationsTable.status, "pending")));
  const result = await appWithRelations(app.id);
  res.json(result);
});

router.post("/applications", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const { freightId, proposedPrice, message } = req.body;
  if (!freightId || !proposedPrice) { res.status(400).json({ error: "freightId and proposedPrice required" }); return; }
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.userId, req.userId!));
  if (!driver) { res.status(400).json({ error: "Must have a driver profile to apply" }); return; }
  const [existing] = await db.select().from(freightApplicationsTable).where(and(eq(freightApplicationsTable.freightId, Number(freightId)), eq(freightApplicationsTable.driverId, driver.id)));
  if (existing) { res.status(409).json({ error: "Already applied for this freight" }); return; }
  const [app] = await db.insert(freightApplicationsTable).values({ freightId: Number(freightId), driverId: driver.id, proposedPrice: Number(proposedPrice), message: message || null }).returning();
  const result = await appWithRelations(app.id);
  res.status(201).json(result);
});

export default router;
