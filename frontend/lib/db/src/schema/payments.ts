import {
  pgTable,
  serial,
  integer,
  text,
  real,
  boolean,
  timestamp,
  pgEnum,
} from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";
import { freightRequestsTable } from "./freight";
import { usersTable } from "./users";
import { driversTable } from "./drivers";

export const paymentProviderEnum = pgEnum("payment_provider", [
  "chapa",
  "cbe_birr",
  "telebirr",
]);

export const paymentStatusEnum = pgEnum("payment_status", [
  "pending",
  "processing",
  "completed",
  "failed",
  "refunded",
  "partially_refunded",
]);

export const escrowStatusEnum = pgEnum("escrow_status", [
  "pending_payment",
  "payment_held",
  "in_transit",
  "delivered",
  "disputed",
  "released",
  "refunded",
  "split",
]);

export const paymentTypeEnum = pgEnum("payment_type", [
  "escrow_deposit",
  "escrow_release",
  "refund",
  "commission",
  "platform_fee",
]);

export const paymentsTable = pgTable("payments", {
  id: serial("id").primaryKey(),
  freightId: integer("freight_id")
    .notNull()
    .references(() => freightRequestsTable.id, { onDelete: "cascade" }),
  shipperId: integer("shipper_id")
    .notNull()
    .references(() => usersTable.id),
  driverId: integer("driver_id")
    .references(() => driversTable.id),
  amount: real("amount").notNull(),
  platformCommission: real("platform_commission").notNull().default(0),
  driverAmount: real("driver_amount").notNull().default(0),
  provider: paymentProviderEnum("provider").notNull().default("chapa"),
  providerTransactionId: text("provider_transaction_id"),
  status: paymentStatusEnum("status").notNull().default("pending"),
  escrowStatus: escrowStatusEnum("escrow_status").notNull().default("pending_payment"),
  currency: text("currency").notNull().default("ETB"),
  metadata: text("metadata"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow().$onUpdate(() => new Date()),
});

export const insertPaymentSchema = createInsertSchema(paymentsTable).omit({
  id: true,
  createdAt: true,
  updatedAt: true,
});
export type InsertPayment = z.infer<typeof insertPaymentSchema>;
export type Payment = typeof paymentsTable.$inferSelect;

export const escrowTransactionsTable = pgTable("escrow_transactions", {
  id: serial("id").primaryKey(),
  paymentId: integer("payment_id")
    .notNull()
    .references(() => paymentsTable.id, { onDelete: "cascade" }),
  freightId: integer("freight_id")
    .notNull()
    .references(() => freightRequestsTable.id),
  type: paymentTypeEnum("type").notNull(),
  amount: real("amount").notNull(),
  fromParty: text("from_party").notNull(),
  toParty: text("to_party").notNull(),
  status: text("status").notNull().default("pending"),
  notes: text("notes"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

export const insertEscrowTransactionSchema = createInsertSchema(escrowTransactionsTable).omit({
  id: true,
  createdAt: true,
});
export type InsertEscrowTransaction = z.infer<typeof insertEscrowTransactionSchema>;
export type EscrowTransaction = typeof escrowTransactionsTable.$inferSelect;
