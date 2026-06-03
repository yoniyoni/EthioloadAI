import {
  pgTable,
  serial,
  integer,
  text,
  boolean,
  timestamp,
  pgEnum,
} from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";
import { usersTable } from "./users";
import { freightRequestsTable } from "./freight";

export const messageTypeEnum = pgEnum("message_type", [
  "text",
  "system",
  "payment_reminder",
  "contract_update",
  "status_update",
]);

export const messagesTable = pgTable("messages", {
  id: serial("id").primaryKey(),
  freightId: integer("freight_id")
    .references(() => freightRequestsTable.id, { onDelete: "cascade" }),
  senderId: integer("sender_id")
    .notNull()
    .references(() => usersTable.id),
  receiverId: integer("receiver_id")
    .notNull()
    .references(() => usersTable.id),
  type: messageTypeEnum("type").notNull().default("text"),
  content: text("content").notNull(),
  maskedContent: text("masked_content"),
  hasPhoneNumber: boolean("has_phone_number").notNull().default(false),
  hasPaymentRequest: boolean("has_payment_request").notNull().default(false),
  isRead: boolean("is_read").notNull().default(false),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

export const insertMessageSchema = createInsertSchema(messagesTable).omit({
  id: true,
  createdAt: true,
});
export type InsertMessage = z.infer<typeof insertMessageSchema>;
export type Message = typeof messagesTable.$inferSelect;
