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
import { driversTable } from "./drivers";

export const applicationStatusEnum = pgEnum("application_status", [
  "pending",
  "accepted",
  "rejected",
  "withdrawn",
]);

export const freightApplicationsTable = pgTable("freight_applications", {
  id: serial("id").primaryKey(),
  freightId: integer("freight_id")
    .notNull()
    .references(() => freightRequestsTable.id, { onDelete: "cascade" }),
  driverId: integer("driver_id")
    .notNull()
    .references(() => driversTable.id, { onDelete: "cascade" }),
  proposedPrice: real("proposed_price"),
  message: text("message"),
  status: applicationStatusEnum("status").notNull().default("pending"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow().$onUpdate(() => new Date()),
});

export const insertApplicationSchema = createInsertSchema(freightApplicationsTable).omit({
  id: true,
  createdAt: true,
  updatedAt: true,
});
export type InsertApplication = z.infer<typeof insertApplicationSchema>;
export type FreightApplication = typeof freightApplicationsTable.$inferSelect;
