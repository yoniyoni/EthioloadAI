import { drizzle } from "drizzle-orm/node-postgres";
import pg from "pg";
import bcrypt from "bcryptjs";
import { eq } from "drizzle-orm";

import { db, usersTable, driversTable } from "@workspace/db";

const { Pool } = pg;

if (!process.env.DATABASE_URL) {
  throw new Error("DATABASE_URL must be set");
}

function hashPassword(password: string): Promise<string> {
  return bcrypt.hash(password, 10);
}

async function seed() {
  console.log("Seeding database...");

  // Check if admin already exists
  const existingAdmin = await db.select().from(usersTable)
    .where(eq(usersTable.email, "admin@freightlink.et"));

  if (existingAdmin.length > 0) {
    console.log("Admin already exists. Skipping seed.");
    return;
  }

  // Create admin user
  const adminHash = await hashPassword("admin123");
  const [admin] = await db.insert(usersTable).values({
    name: "Admin",
    email: "admin@freightlink.et",
    passwordHash: adminHash,
    phone: "+251911000001",
    role: "admin",
    isVerified: true,
  }).returning();
  console.log("Created admin:", admin.email);

  // Create demo shipper
  const shipperHash = await hashPassword("shipper123");
  const [shipper] = await db.insert(usersTable).values({
    name: "Tigist Mesfin",
    email: "tigist@shipper.et",
    passwordHash: shipperHash,
    phone: "+251911000002",
    role: "shipper",
    isVerified: true,
  }).returning();
  console.log("Created shipper:", shipper.email);

  // Create demo driver
  const driverHash = await hashPassword("driver123");
  const [driver] = await db.insert(usersTable).values({
    name: "Bekele Girma",
    email: "bekele@driver.et",
    passwordHash: driverHash,
    phone: "+251911000003",
    role: "driver",
    isVerified: true,
  }).returning();
  console.log("Created driver:", driver.email);

  // Create driver profile
  await db.insert(driversTable).values({
    userId: driver.id,
    licenseNumber: "ETH-DRV-12345",
    nationalId: "ETH-123456789",
    yearsExperience: 5,
    status: "active",
    rating: 4.5,
    totalDeliveries: 120,
    successRate: 96,
    cancellationRate: 2,
    isAvailable: true,
  });
  console.log("Created driver profile");

  console.log("Seed complete!");
  console.log("\nDemo accounts:");
  console.log("  Admin:    admin@freightlink.et / admin123");
  console.log("  Shipper:  tigist@shipper.et / shipper123");
  console.log("  Driver:   bekele@driver.et / driver123");
}

seed().catch(console.error).finally(() => process.exit(0));
