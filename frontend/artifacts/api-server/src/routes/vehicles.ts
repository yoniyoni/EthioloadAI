import { Router, type IRouter } from "express";
import { eq } from "drizzle-orm";
import { db, vehiclesTable, driversTable } from "@workspace/db";
import { CreateVehicleBody, UpdateVehicleBody } from "@workspace/api-zod";
import { authenticate, type AuthRequest } from "../middlewares/authenticate";

const router: IRouter = Router();

router.post("/vehicles", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const parsed = CreateVehicleBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.userId, req.userId!));
  if (!driver) {
    res.status(400).json({ error: "You must create a driver profile first" });
    return;
  }
  const [vehicle] = await db
    .insert(vehiclesTable)
    .values({ ...parsed.data, driverId: driver.id })
    .returning();
  res.status(201).json(vehicle);
});

router.get("/vehicles/my", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const [driver] = await db.select().from(driversTable).where(eq(driversTable.userId, req.userId!));
  if (!driver) {
    res.json([]);
    return;
  }
  const vehicles = await db.select().from(vehiclesTable).where(eq(vehiclesTable.driverId, driver.id));
  res.json({ vehicles });
});

router.get("/vehicles/:id", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const id = parseInt(raw, 10);
  if (isNaN(id)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  const [vehicle] = await db.select().from(vehiclesTable).where(eq(vehiclesTable.id, id));
  if (!vehicle) {
    res.status(404).json({ error: "Vehicle not found" });
    return;
  }
  res.json(vehicle);
});

router.patch("/vehicles/:id", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const id = parseInt(raw, 10);
  if (isNaN(id)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  const parsed = UpdateVehicleBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const [vehicle] = await db
    .update(vehiclesTable)
    .set(parsed.data)
    .where(eq(vehiclesTable.id, id))
    .returning();
  if (!vehicle) {
    res.status(404).json({ error: "Vehicle not found" });
    return;
  }
  res.json(vehicle);
});

export default router;
