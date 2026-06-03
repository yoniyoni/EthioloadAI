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
import { usersTable } from "./users";

export const cargoTypeEnum = pgEnum("cargo_type", [
  "grain",
  "cement",
  "fuel",
  "livestock",
  "electronics",
  "furniture",
  "construction",
  "perishables",
  "machinery",
  "other",
]);

export const freightStatusEnum = pgEnum("freight_status", [
  "draft",
  "posted",
  "matched",
  "accepted",
  "in_transit",
  "delivered",
  "completed",
  "cancelled",
]);

export const freightRequestsTable = pgTable("freight_requests", {
  id: serial("id").primaryKey(),
  shipperId: integer("shipper_id")
    .notNull()
    .references(() => usersTable.id),
  matchedDriverId: integer("matched_driver_id"),
  pickupLocation: text("pickup_location").notNull(),
  pickupLatitude: real("pickup_latitude"),
  pickupLongitude: real("pickup_longitude"),
  deliveryLocation: text("delivery_location").notNull(),
  deliveryLatitude: real("delivery_latitude"),
  deliveryLongitude: real("delivery_longitude"),
  cargoType: cargoTypeEnum("cargo_type").notNull(),
  cargoDescription: text("cargo_description"),
  weightTons: real("weight_tons").notNull(),
  volumeM3: real("volume_m3"),
  deadline: timestamp("deadline", { withTimezone: true }),
  budget: real("budget"),
  distanceKm: real("distance_km"),
  specialInstructions: text("special_instructions"),
  status: freightStatusEnum("status").notNull().default("posted"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow().$onUpdate(() => new Date()),
});

export const insertFreightSchema = createInsertSchema(freightRequestsTable).omit({
  id: true,
  createdAt: true,
  updatedAt: true,
});
export type InsertFreight = z.infer<typeof insertFreightSchema>;
export type FreightRequest = typeof freightRequestsTable.$inferSelect;
