import {
  pgTable,
  serial,
  integer,
  text,
  real,
  timestamp,
  pgEnum,
} from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";
import { freightRequestsTable } from "./freight";
import { usersTable } from "./users";
import { paymentsTable } from "./payments";

export const disputeStatusEnum = pgEnum("dispute_status", [
  "open",
  "under_review",
  "resolved",
  "closed",
]);

export const disputeResolutionEnum = pgEnum("dispute_resolution", [
  "none",
  "release_to_driver",
  "refund_to_shipper",
  "split_payment",
  "escalated",
]);

export const disputesTable = pgTable("disputes", {
  id: serial("id").primaryKey(),
  freightId: integer("freight_id")
    .notNull()
    .references(() => freightRequestsTable.id, { onDelete: "cascade" }),
  paymentId: integer("payment_id")
    .references(() => paymentsTable.id),
  initiatedBy: integer("initiated_by")
    .notNull()
    .references(() => usersTable.id),
  reason: text("reason").notNull(),
  description: text("description"),
  evidence: text("evidence"),
  status: disputeStatusEnum("status").notNull().default("open"),
  resolution: disputeResolutionEnum("resolution").notNull().default("none"),
  resolutionNotes: text("resolution_notes"),
  refundAmount: real("refund_amount"),
  driverAmount: real("driver_amount"),
  resolvedBy: integer("resolved_by")
    .references(() => usersTable.id),
  resolvedAt: timestamp("resolved_at", { withTimezone: true }),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow().$onUpdate(() => new Date()),
});

export const insertDisputeSchema = createInsertSchema(disputesTable).omit({
  id: true,
  createdAt: true,
  updatedAt: true,
});
export type InsertDispute = z.infer<typeof insertDisputeSchema>;
export type Dispute = typeof disputesTable.$inferSelect;
