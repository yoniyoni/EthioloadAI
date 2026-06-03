import { Router, type IRouter } from "express";
import { eq, desc } from "drizzle-orm";
import { db, trackingLocationsTable, driversTable } from "@workspace/db";
import { PostTrackingUpdateBody } from "@workspace/api-zod";
import { authenticate, type AuthRequest } from "../middlewares/authenticate";

const router: IRouter = Router();

router.post("/tracking", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const parsed = PostTrackingUpdateBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.userId, req.userId!));
  if (!driver) {
    res.status(400).json({ error: "Must have driver profile" });
    return;
  }
  // Update driver's current location
  await db.update(driversTable).set({
    currentLatitude: parsed.data.latitude,
    currentLongitude: parsed.data.longitude,
  }).where(eq(driversTable.id, driver.id));

  const [loc] = await db
    .insert(trackingLocationsTable)
    .values({ ...parsed.data, driverId: driver.id })
    .returning();
  res.status(201).json(loc);
});

router.get("/tracking/:freightId", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.freightId) ? req.params.freightId[0] : req.params.freightId;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  const locations = await db
    .select()
    .from(trackingLocationsTable)
    .where(eq(trackingLocationsTable.freightId, freightId))
    .orderBy(trackingLocationsTable.timestamp);
  res.json(locations);
});

router.get("/tracking/:freightId/latest", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.freightId) ? req.params.freightId[0] : req.params.freightId;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  const [loc] = await db
    .select()
    .from(trackingLocationsTable)
    .where(eq(trackingLocationsTable.freightId, freightId))
    .orderBy(desc(trackingLocationsTable.timestamp))
    .limit(1);
  if (!loc) {
    res.status(404).json({ error: "No tracking data" });
    return;
  }
  res.json(loc);
});

export default router;
