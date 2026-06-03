import {
  pgTable,
  serial,
  integer,
  real,
  timestamp,
} from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";
import { freightRequestsTable } from "./freight";
import { driversTable } from "./drivers";

export const trackingLocationsTable = pgTable("tracking_locations", {
  id: serial("id").primaryKey(),
  freightId: integer("freight_id")
    .notNull()
    .references(() => freightRequestsTable.id, { onDelete: "cascade" }),
  driverId: integer("driver_id")
    .notNull()
    .references(() => driversTable.id),
  latitude: real("latitude").notNull(),
  longitude: real("longitude").notNull(),
  speed: real("speed"),
  heading: real("heading"),
  timestamp: timestamp("timestamp", { withTimezone: true }).notNull().defaultNow(),
});

export const insertTrackingSchema = createInsertSchema(trackingLocationsTable).omit({
  id: true,
  timestamp: true,
});
export type InsertTracking = z.infer<typeof insertTrackingSchema>;
export type TrackingLocation = typeof trackingLocationsTable.$inferSelect;
