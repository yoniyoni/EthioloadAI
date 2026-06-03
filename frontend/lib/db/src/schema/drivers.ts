import {
  pgTable,
  serial,
  integer,
  text,
  boolean,
  real,
  timestamp,
  pgEnum,
} from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";
import { usersTable } from "./users";

export const driverStatusEnum = pgEnum("driver_status", [
  "pending",
  "submitted",
  "under_review",
  "approved",
  "active",
  "suspended",
]);

export const driversTable = pgTable("drivers", {
  id: serial("id").primaryKey(),
  userId: integer("user_id")
    .notNull()
    .references(() => usersTable.id, { onDelete: "cascade" })
    .unique(),
  licenseNumber: text("license_number"),
  nationalId: text("national_id"),
  yearsExperience: integer("years_experience"),
  status: driverStatusEnum("status").notNull().default("pending"),
  rating: real("rating").notNull().default(0),
  totalDeliveries: integer("total_deliveries").notNull().default(0),
  successRate: real("success_rate").notNull().default(0),
  cancellationRate: real("cancellation_rate").notNull().default(0),
  currentLatitude: real("current_latitude"),
  currentLongitude: real("current_longitude"),
  isAvailable: boolean("is_available").notNull().default(false),
  statusNotes: text("status_notes"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow().$onUpdate(() => new Date()),
});

export const insertDriverSchema = createInsertSchema(driversTable).omit({
  id: true,
  createdAt: true,
  updatedAt: true,
});
export type InsertDriver = z.infer<typeof insertDriverSchema>;
export type Driver = typeof driversTable.$inferSelect;
