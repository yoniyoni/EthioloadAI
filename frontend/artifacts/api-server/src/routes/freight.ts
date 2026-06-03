import { Router, type IRouter } from "express";
import { eq, and, sql } from "drizzle-orm";
import { db, freightRequestsTable, usersTable } from "@workspace/db";
import {
  CreateFreightBody,
  UpdateFreightBody,
  UpdateFreightStatusBody,
  ListFreightQueryParams,
  ListMyFreightQueryParams,
} from "@workspace/api-zod";
import { authenticate, optionalAuthenticate, type AuthRequest } from "../middlewares/authenticate";

const router: IRouter = Router();

async function freightWithShipper(id: number) {
  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, id));
  if (!freight) return null;
  const [shipper] = await db.select().from(usersTable).where(eq(usersTable.id, freight.shipperId));
  if (shipper) {
    const { passwordHash: _pw, ...safeShipper } = shipper;
    return { ...freight, shipper: safeShipper };
  }
  return { ...freight, shipper: null };
}

router.get("/freight", optionalAuthenticate, async (req: AuthRequest, res): Promise<void> => {
  const params = ListFreightQueryParams.safeParse(req.query);
  const limit = params.success ? (params.data.limit ?? 20) : 20;
  const offset = params.success ? (params.data.offset ?? 0) : 0;

  const conditions = [];
  if (params.success && params.data.status) {
    conditions.push(eq(freightRequestsTable.status, params.data.status as typeof freightRequestsTable.status._.data));
  }
  if (params.success && params.data.shipperId) {
    conditions.push(eq(freightRequestsTable.shipperId, params.data.shipperId));
  }
  if (params.success && params.data.cargoType) {
    conditions.push(eq(freightRequestsTable.cargoType, params.data.cargoType as typeof freightRequestsTable.cargoType._.data));
  }

  const query = db.select().from(freightRequestsTable);
  const rows = conditions.length > 0
    ? await query.where(and(...conditions)).limit(limit).offset(offset).orderBy(sql`${freightRequestsTable.createdAt} desc`)
    : await query.limit(limit).offset(offset).orderBy(sql`${freightRequestsTable.createdAt} desc`);

  const countQuery = db.select({ count: sql<number>`count(*)` }).from(freightRequestsTable);
  const [{ count }] = conditions.length > 0
    ? await countQuery.where(and(...conditions))
    : await countQuery;

  const enriched = await Promise.all(rows.map(f => freightWithShipper(f.id)));
  res.json({ freight: enriched.filter(Boolean), total: Number(count) });
});

router.post("/freight", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const parsed = CreateFreightBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const [freight] = await db
    .insert(freightRequestsTable)
    .values({ ...parsed.data, shipperId: req.userId! })
    .returning();
  const result = await freightWithShipper(freight.id);
  res.status(201).json(result);
});

router.get("/freight/my", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const params = ListMyFreightQueryParams.safeParse(req.query);
  const conditions = [eq(freightRequestsTable.shipperId, req.userId!)];
  if (params.success && params.data.status) {
    conditions.push(eq(freightRequestsTable.status, params.data.status as typeof freightRequestsTable.status._.data));
  }
  const rows = await db
    .select()
    .from(freightRequestsTable)
    .where(and(...conditions))
    .orderBy(sql`${freightRequestsTable.createdAt} desc`);
  const enriched = await Promise.all(rows.map(f => freightWithShipper(f.id)));
  res.json(enriched.filter(Boolean));
});

router.get("/freight/:id", optionalAuthenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const id = parseInt(raw, 10);
  if (isNaN(id)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  const result = await freightWithShipper(id);
  if (!result) {
    res.status(404).json({ error: "Freight not found" });
    return;
  }
  res.json(result);
});

router.patch("/freight/:id", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const id = parseInt(raw, 10);
  if (isNaN(id)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  const parsed = UpdateFreightBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const updateData: Record<string, any> = { ...parsed.data };
  await db.update(freightRequestsTable).set(updateData).where(eq(freightRequestsTable.id, id));
  const result = await freightWithShipper(id);
  if (!result) {
    res.status(404).json({ error: "Freight not found" });
    return;
  }
  res.json(result);
});

router.delete("/freight/:id", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const id = parseInt(raw, 10);
  if (isNaN(id)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  await db.update(freightRequestsTable).set({ status: "cancelled" }).where(eq(freightRequestsTable.id, id));
  res.sendStatus(204);
});

router.patch("/freight/:id/status", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const id = parseInt(raw, 10);
  if (isNaN(id)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  const parsed = UpdateFreightStatusBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  await db.update(freightRequestsTable).set({ status: parsed.data.status }).where(eq(freightRequestsTable.id, id));
  const result = await freightWithShipper(id);
  if (!result) {
    res.status(404).json({ error: "Freight not found" });
    return;
  }
  res.json(result);
});

export default router;
