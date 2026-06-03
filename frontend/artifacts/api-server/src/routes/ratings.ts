import { Router, type IRouter } from "express";
import { eq } from "drizzle-orm";
import { db, ratingsTable, driversTable, usersTable } from "@workspace/db";
import { CreateRatingBody } from "@workspace/api-zod";
import { authenticate, type AuthRequest } from "../middlewares/authenticate";

const router: IRouter = Router();

router.post("/ratings", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const parsed = CreateRatingBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const [rating] = await db
    .insert(ratingsTable)
    .values({ ...parsed.data, raterId: req.userId! })
    .returning();

  // Update driver's average rating if this is a shipper-to-driver rating
  if (parsed.data.type === "shipper_to_driver") {
    const [driverUser] = await db.select().from(usersTable).where(eq(usersTable.id, parsed.data.rateeId));
    if (driverUser) {
      const [driver] = await db.select().from(driversTable).where(eq(driversTable.userId, driverUser.id));
      if (driver) {
        const allRatings = await db.select().from(ratingsTable).where(eq(ratingsTable.rateeId, parsed.data.rateeId));
        const avg = allRatings.reduce((sum, r) => sum + r.stars, 0) / allRatings.length;
        await db.update(driversTable).set({ rating: Math.round(avg * 10) / 10 }).where(eq(driversTable.id, driver.id));
      }
    }
  }

  res.status(201).json(rating);
});

router.get("/ratings/driver/:id", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const driverId = parseInt(raw, 10);
  if (isNaN(driverId)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.id, driverId));
  if (!driver) {
    res.status(404).json({ error: "Driver not found" });
    return;
  }
  const ratings = await db.select().from(ratingsTable).where(eq(ratingsTable.rateeId, driver.userId));
  res.json(ratings);
});

export default router;
