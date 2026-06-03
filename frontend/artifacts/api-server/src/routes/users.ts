import { Router, type IRouter } from "express";
import { eq } from "drizzle-orm";
import { db, usersTable } from "@workspace/db";
import { UpdateMeBody } from "@workspace/api-zod";
import { authenticate, type AuthRequest } from "../middlewares/authenticate";

const router: IRouter = Router();

function safeUser(u: typeof usersTable.$inferSelect) {
  const { passwordHash: _pw, ...rest } = u;
  return rest;
}

router.get("/auth/me", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const [user] = await db.select().from(usersTable).where(eq(usersTable.id, req.userId!));
  if (!user) {
    res.status(404).json({ error: "User not found" });
    return;
  }
  res.json(safeUser(user));
});

router.patch("/users/me", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const parsed = UpdateMeBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const [user] = await db
    .update(usersTable)
    .set(parsed.data)
    .where(eq(usersTable.id, req.userId!))
    .returning();
  res.json(safeUser(user));
});

router.patch("/users/:id", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const id = parseInt(raw, 10);
  if (isNaN(id)) { res.status(400).json({ error: "Invalid ID" }); return; }
  if (req.userId !== id && req.userRole !== "admin") {
    res.status(403).json({ error: "Forbidden" }); return;
  }
  const { name, phone, address, businessName, avatarUrl, preferredLanguage } = req.body;
  const update: Record<string, string | undefined> = {};
  if (name !== undefined) update.name = name;
  if (phone !== undefined) update.phone = phone;
  if (address !== undefined) update.address = address;
  if (businessName !== undefined) update.businessName = businessName;
  if (avatarUrl !== undefined) update.avatarUrl = avatarUrl;
  if (preferredLanguage !== undefined) update.preferredLanguage = preferredLanguage;
  const [user] = await db.update(usersTable).set(update).where(eq(usersTable.id, id)).returning();
  if (!user) { res.status(404).json({ error: "User not found" }); return; }
  res.json(safeUser(user));
});

router.get("/users", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const { sql } = await import("drizzle-orm");
  const limit = parseInt(String(req.query.limit ?? "50"), 10);
  const offset = parseInt(String(req.query.offset ?? "0"), 10);
  const users = await db.select().from(usersTable).limit(limit).offset(offset);
  const [{ count }] = await db.select({ count: sql<number>`count(*)` }).from(usersTable);
  res.json({ users: users.map(safeUser), total: Number(count) });
});

router.get("/users/:id", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const id = parseInt(raw, 10);
  if (isNaN(id)) {
    res.status(400).json({ error: "Invalid ID" });
    return;
  }
  const [user] = await db.select().from(usersTable).where(eq(usersTable.id, id));
  if (!user) {
    res.status(404).json({ error: "User not found" });
    return;
  }
  res.json(safeUser(user));
});

export default router;
