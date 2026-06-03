import {
  pgTable,
  serial,
  integer,
  text,
  real,
  timestamp,
} from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";
import { freightRequestsTable } from "./freight";
import { driversTable } from "./drivers";

export const priceHistoryTable = pgTable("price_history", {
  id: serial("id").primaryKey(),
  freightId: integer("freight_id").references(() => freightRequestsTable.id),
  cargoType: text("cargo_type").notNull(),
  weightTons: real("weight_tons").notNull(),
  distanceKm: real("distance_km").notNull(),
  actualPrice: real("actual_price").notNull(),
  estimatedPrice: real("estimated_price").notNull(),
  driverId: integer("driver_id").references(() => driversTable.id),
  vehicleType: text("vehicle_type"),
  pickupRegion: text("pickup_region"),
  deliveryRegion: text("delivery_region"),
  season: text("season"),
  fuelPrice: real("fuel_price"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

export const insertPriceHistorySchema = createInsertSchema(priceHistoryTable).omit({
  id: true,
  createdAt: true,
});
export type InsertPriceHistory = z.infer<typeof insertPriceHistorySchema>;
export type PriceHistory = typeof priceHistoryTable.$inferSelect;

export const contractsTable = pgTable("contracts", {
  id: serial("id").primaryKey(),
  freightId: integer("freight_id").notNull().references(() => freightRequestsTable.id),
  driverId: integer("driver_id").notNull().references(() => driversTable.id),
  shipperId: integer("shipper_id").notNull(),
  agreedPrice: real("agreed_price").notNull(),
  status: text("status").notNull().default("draft"),
  pickupLocation: text("pickup_location").notNull(),
  deliveryLocation: text("delivery_location").notNull(),
  deadline: timestamp("deadline", { withTimezone: true }),
  paymentStatus: text("payment_status").notNull().default("pending"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow().$onUpdate(() => new Date()),
});

export const insertContractSchema = createInsertSchema(contractsTable).omit({
  id: true,
  createdAt: true,
  updatedAt: true,
});
export type InsertContract = z.infer<typeof insertContractSchema>;
export type Contract = typeof contractsTable.$inferSelect;
