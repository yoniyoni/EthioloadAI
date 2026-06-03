import { db, driversTable, vehiclesTable, usersTable, freightApplicationsTable, ratingsTable } from "@workspace/db";
import { eq, and, avg, count, sql } from "drizzle-orm";

export interface DriverMatchInput {
  freightId: number;
  weightTons: number;
  cargoType: string;
  pickupLat?: number;
  pickupLng?: number;
  deliveryLat?: number;
  deliveryLng?: number;
  budget?: number;
}

export interface DriverMatchResult {
  driverId: number;
  score: number;
  matchScore: number;
  breakdown: {
    distanceScore: number;
    ratingScore: number;
    successRateScore: number;
    priceScore: number;
    capacityScore: number;
    availabilityScore: number;
  };
  driver: {
    id: number;
    name: string;
    email: string;
    rating: number;
    totalDeliveries: number;
    successRate: number;
    cancellationRate: number;
    yearsExperience: number;
    licenseNumber: string | null;
    currentLatitude: number | null;
    currentLongitude: number | null;
    distanceKm: number | null;
    estimatedPrice: number;
    vehicleType: string;
    vehicleCapacity: number;
  };
  estimatedPrice: number;
  distanceKm: number | null;
}

const WEIGHTS = {
  distance: 0.35,
  rating: 0.25,
  successRate: 0.20,
  price: 0.10,
  capacity: 0.07,
  availability: 0.03,
};

function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function capacityScoreFn(vehicleCapacity: number, required: number): number {
  if (vehicleCapacity < required) return 0;
  const excess = vehicleCapacity / required;
  if (excess <= 1.2) return 100;
  if (excess <= 1.5) return 90;
  if (excess <= 2.0) return 75;
  return 60;
}

function distanceScoreFn(km: number | null): number {
  if (km === null) return 50;
  if (km <= 10) return 100;
  if (km <= 50) return 90;
  if (km <= 100) return 75;
  if (km <= 200) return 55;
  if (km <= 500) return 35;
  return 20;
}

function ratingScoreFn(rating: number, deliveries: number): number {
  if (deliveries === 0) return 60;
  return Math.min(100, rating * 20);
}

function successRateScoreFn(successRate: number): number {
  return Math.min(100, successRate * 100);
}

function priceScoreFn(budget: number | null, estimatedPrice: number): number {
  if (!budget) return 70;
  const ratio = budget / estimatedPrice;
  if (ratio >= 1.3) return 100;
  if (ratio >= 1.0) return 85;
  if (ratio >= 0.8) return 60;
  if (ratio >= 0.6) return 35;
  return 10;
}

function availabilityScoreFn(isAvailable: boolean, recentApplications: number): number {
  if (!isAvailable) return 0;
  if (recentApplications > 5) return 60;
  if (recentApplications > 2) return 80;
  return 100;
}

function estimatePrice(weightTons: number, distanceKm: number, cargoType: string): number {
  const baseRatePerKm = 45;
  const weightMultiplier = 1 + weightTons * 0.08;
  const cargoMultipliers: Record<string, number> = {
    fuel: 1.5, livestock: 1.3, electronics: 1.4, perishables: 1.35,
    machinery: 1.2, cement: 0.9, grain: 0.95, construction: 1.1, furniture: 1.0, other: 1.0,
  };
  const multiplier = cargoMultipliers[cargoType] ?? cargoMultipliers.other;
  return Math.round(baseRatePerKm * distanceKm * weightMultiplier * multiplier);
}

export async function findMatchingDrivers(input: DriverMatchInput): Promise<DriverMatchResult[]> {
  const { freightId, weightTons, cargoType, pickupLat, pickupLng, budget } = input;

  // Fetch all available drivers with their vehicles
  const drivers = await db.select().from(driversTable).where(eq(driversTable.isAvailable, true));

  const results: DriverMatchResult[] = [];

  for (const driver of drivers) {
    const vehicles = await db.select().from(vehiclesTable).where(
      and(eq(vehiclesTable.driverId, driver.id), eq(vehiclesTable.isAvailable, true))
    );
    const suitableVehicle = vehicles.find(v => v.capacityTons >= weightTons);
    if (!suitableVehicle) continue;

    const distKm =
      driver.currentLatitude && driver.currentLongitude && pickupLat && pickupLng
        ? haversineKm(driver.currentLatitude, driver.currentLongitude, pickupLat, pickupLng)
        : null;

    const estimatedDistance = input.deliveryLat && input.deliveryLng && pickupLat && pickupLng
      ? haversineKm(pickupLat, pickupLng, input.deliveryLat, input.deliveryLng)
      : 300;

    const estimated = estimatePrice(weightTons, estimatedDistance, cargoType);

    const capScore = capacityScoreFn(suitableVehicle.capacityTons, weightTons);
    const distScore = distanceScoreFn(distKm);
    const ratScore = ratingScoreFn(driver.rating, driver.totalDeliveries);
    const succScore = successRateScoreFn(driver.successRate);
    const prcScore = priceScoreFn(budget ?? null, estimated);
    const availScore = availabilityScoreFn(driver.isAvailable, 0);

    const matchScore =
      distScore * WEIGHTS.distance +
      ratScore * WEIGHTS.rating +
      succScore * WEIGHTS.successRate +
      prcScore * WEIGHTS.price +
      capScore * WEIGHTS.capacity +
      availScore * WEIGHTS.availability;

    const [user] = await db.select().from(usersTable).where(eq(usersTable.id, driver.userId));
    if (!user) continue;

    results.push({
      driverId: driver.id,
      score: Math.round(matchScore) / 100,
      matchScore: Math.round(matchScore * 10) / 10,
      breakdown: {
        distanceScore: distScore,
        ratingScore: ratScore,
        successRateScore: succScore,
        priceScore: prcScore,
        capacityScore: capScore,
        availabilityScore: availScore,
      },
      driver: {
        id: driver.id,
        name: user.name,
        email: user.email,
        rating: driver.rating,
        totalDeliveries: driver.totalDeliveries,
        successRate: driver.successRate,
        cancellationRate: driver.cancellationRate,
        yearsExperience: driver.yearsExperience ?? 0,
        licenseNumber: driver.licenseNumber,
        currentLatitude: driver.currentLatitude,
        currentLongitude: driver.currentLongitude,
        distanceKm: distKm ? Math.round(distKm) : null,
        estimatedPrice: estimated,
        vehicleType: suitableVehicle.truckType,
        vehicleCapacity: suitableVehicle.capacityTons,
      },
      estimatedPrice: estimated,
      distanceKm: distKm ? Math.round(distKm) : null,
    });
  }

  return results.sort((a, b) => b.matchScore - a.matchScore).slice(0, 10);
}

export async function getDriverStats(driverId: number): Promise<{
  avgRating: number;
  totalRatings: number;
  recentRating: number;
  onTimeRate: number;
}> {
  const ratingData = await db
    .select({
      avgStars: sql<number>`AVG(${ratingsTable.stars})`,
      count: sql<number>`COUNT(*)`,
    })
    .from(ratingsTable)
    .where(eq(ratingsTable.rateeId, driverId));

  return {
    avgRating: ratingData[0]?.avgStars ?? 0,
    totalRatings: ratingData[0]?.count ?? 0,
    recentRating: 0,
    onTimeRate: 0,
  };
}
