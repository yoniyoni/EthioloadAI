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
import { driversTable } from "./drivers";

export const truckTypeEnum = pgEnum("truck_type", [
  "pickup",
  "light_truck",
  "medium_truck",
  "heavy_truck",
  "tanker",
  "refrigerated",
  "flatbed",
  "tipper",
]);

export const fuelTypeEnum = pgEnum("fuel_type", [
  "diesel",
  "petrol",
  "gas",
  "electric",
]);

export const vehiclesTable = pgTable("vehicles", {
  id: serial("id").primaryKey(),
  driverId: integer("driver_id")
    .notNull()
    .references(() => driversTable.id, { onDelete: "cascade" }),
  truckType: truckTypeEnum("truck_type").notNull(),
  plateNumber: text("plate_number").notNull().unique(),
  capacityTons: real("capacity_tons").notNull(),
  volumeM3: real("volume_m3"),
  fuelType: fuelTypeEnum("fuel_type").notNull().default("diesel"),
  isAvailable: boolean("is_available").notNull().default(true),
  photoUrl: text("photo_url"),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow().$onUpdate(() => new Date()),
});

export const insertVehicleSchema = createInsertSchema(vehiclesTable).omit({
  id: true,
  createdAt: true,
  updatedAt: true,
});
export type InsertVehicle = z.infer<typeof insertVehicleSchema>;
export type Vehicle = typeof vehiclesTable.$inferSelect;
