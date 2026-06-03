import { Router, type IRouter } from "express";
import { eq, and, desc } from "drizzle-orm";
import { db, messagesTable, freightRequestsTable, paymentsTable, usersTable, driversTable } from "@workspace/db";
import { authenticate, type AuthRequest } from "../middlewares/authenticate";

const router: IRouter = Router();

// Phone number regex pattern
const PHONE_REGEX = /\+?[0-9]{10,15}/g;
const ETH_PHONE_REGEX = /\b(0?9[0-9]{8}|2519[0-9]{8}|\+2519[0-9]{8})\b/g;

function maskPhoneNumbers(content: string): string {
  return content
    .replace(ETH_PHONE_REGEX, "[PHONE MASKED]")
    .replace(PHONE_REGEX, "[PHONE MASKED]");
}

function detectPaymentRequest(content: string): boolean {
  const paymentKeywords = ["pay", "payment", "cash", "money", "transfer", "send money", "bank", "deposit", "etb", "birr"];
  const lower = content.toLowerCase();
  return paymentKeywords.some((k) => lower.includes(k));
}

function hasPhoneNumber(content: string): boolean {
  return ETH_PHONE_REGEX.test(content) || PHONE_REGEX.test(content);
}

// POST /messages — Send a message
router.post("/messages", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const { freightId, receiverId, content, type } = req.body;
  if (!receiverId || !content) {
    res.status(400).json({ error: "receiverId and content are required" });
    return;
  }

  // Check if active freight exists and has payment
  const paymentRequired = freightId ? await checkPaymentRequired(freightId) : false;

  const maskedContent = paymentRequired ? maskPhoneNumbers(content) : content;
  const phoneDetected = hasPhoneNumber(content);
  const paymentDetected = detectPaymentRequest(content);

  const [message] = await db
    .insert(messagesTable)
    .values({
      freightId: freightId ? Number(freightId) : null,
      senderId: req.userId!,
      receiverId: Number(receiverId),
      type: type || "text",
      content,
      maskedContent: paymentRequired ? maskedContent : null,
      hasPhoneNumber: phoneDetected,
      hasPaymentRequest: paymentDetected,
    })
    .returning();

  // If payment attempt detected, send warning
  if (paymentDetected && paymentRequired) {
    await db.insert(messagesTable).values({
      freightId: freightId ? Number(freightId) : null,
      senderId: 0,
      receiverId: req.userId!,
      type: "system",
      content: "Payment exchange outside the platform is prohibited. All payments must go through escrow to ensure safety and trust.",
      hasPhoneNumber: false,
      hasPaymentRequest: false,
    });
  }

  res.status(201).json({
    message,
    warning: paymentDetected && paymentRequired ? "Payment requests outside the platform are prohibited" : null,
  });
});

// GET /messages/:freightId — Get messages for a freight
router.get("/messages/:freightId", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.freightId) ? req.params.freightId[0] : req.params.freightId;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) { res.status(400).json({ error: "Invalid ID" }); return; }

  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, freightId));
  if (!freight) { res.status(404).json({ error: "Freight not found" }); return; }

  // Verify user is part of the freight
  const isParticipant = freight.shipperId === req.userId || req.userRole === "admin";
  let isDriver = false;
  if (!isParticipant && freight.matchedDriverId) {
    const [driver] = await db.select().from(driversTable).where(eq(driversTable.userId, req.userId!));
    isDriver = driver?.id === freight.matchedDriverId;
  }

  if (!isParticipant && !isDriver) {
    res.status(403).json({ error: "Not authorized to view messages" });
    return;
  }

  const messages = await db
    .select()
    .from(messagesTable)
    .where(eq(messagesTable.freightId, freightId))
    .orderBy(desc(messagesTable.createdAt));

  // Enrich with sender info
  const enriched = await Promise.all(
    messages.map(async (msg) => {
      const [sender] = await db.select().from(usersTable).where(eq(usersTable.id, msg.senderId));
      return {
        ...msg,
        sender: sender ? { name: sender.name, role: sender.role } : null,
      };
    })
  );

  res.json({ messages: enriched, total: messages.length });
});

// GET /messages/conversations — Get conversations for current user
router.get("/messages/conversations", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const allMessages = await db
    .select()
    .from(messagesTable)
    .where(
      and(
        eq(messagesTable.senderId, req.userId!),
        eq(messagesTable.type, "text")
      )
    );

  const conversations = allMessages.reduce((acc: any, msg) => {
    const key = msg.freightId || msg.receiverId;
    if (!acc[key]) acc[key] = { lastMessage: msg, unread: 0 };
    return acc;
  }, {});

  res.json({ conversations: Object.values(conversations) });
});

// POST /messages/mark-read — Mark messages as read
router.post("/messages/mark-read", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const { freightId } = req.body;
  if (!freightId) { res.status(400).json({ error: "freightId required" }); return; }

  await db
    .update(messagesTable)
    .set({ isRead: true })
    .where(
      and(
        eq(messagesTable.freightId, Number(freightId)),
        eq(messagesTable.receiverId, req.userId!)
      )
    );

  res.json({ message: "Messages marked as read" });
});

async function checkPaymentRequired(freightId: number): Promise<boolean> {
  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, freightId));
  if (!freight) return false;
  if (freight.status === "in_transit" || freight.status === "delivered" || freight.status === "completed") {
    return true;
  }
  const [payment] = await db.select().from(paymentsTable).where(eq(paymentsTable.freightId, freightId));
  return payment !== undefined;
}

export default router;
