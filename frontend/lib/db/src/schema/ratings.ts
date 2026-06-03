import {
  pgTable,
  serial,
  integer,
  text,
  timestamp,
  pgEnum,
} from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { z } from "zod/v4";
import { freightRequestsTable } from "./freight";
import { usersTable } from "./users";

export const ratingTypeEnum = pgEnum("rating_type", [
  "shipper_to_driver",
  "driver_to_shipper",
]);

export const ratingsTable = pgTable("ratings", {
  id: serial("id").primaryKey(),
  freightId: integer("freight_id")
    .notNull()
    .references(() => freightRequestsTable.id),
  raterId: integer("rater_id")
    .notNull()
    .references(() => usersTable.id),
  rateeId: integer("ratee_id")
    .notNull()
    .references(() => usersTable.id),
  stars: integer("stars").notNull(),
  review: text("review"),
  type: ratingTypeEnum("type").notNull(),
  createdAt: timestamp("created_at", { withTimezone: true }).notNull().defaultNow(),
});

export const insertRatingSchema = createInsertSchema(ratingsTable).omit({
  id: true,
  createdAt: true,
});
export type InsertRating = z.infer<typeof insertRatingSchema>;
export type Rating = typeof ratingsTable.$inferSelect;
