import { Router, type IRouter } from "express";
import { eq, and } from "drizzle-orm";
import { db, freightRequestsTable, driversTable, vehiclesTable, usersTable } from "@workspace/db";
import { authenticate, optionalAuthenticate, type AuthRequest } from "../middlewares/authenticate";

const router: IRouter = Router();

function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function capacityScore(vehicleCapacity: number, required: number): number {
  if (vehicleCapacity < required) return 0;
  if (vehicleCapacity <= required * 1.5) return 100;
  if (vehicleCapacity <= required * 2) return 80;
  return 60;
}

function distanceScoreFn(km: number | null): number {
  if (km === null) return 50;
  if (km <= 50) return 100;
  if (km <= 100) return 80;
  if (km <= 200) return 60;
  if (km <= 500) return 40;
  return 20;
}

function ratingScoreFn(rating: number, deliveries: number): number {
  if (deliveries === 0) return 50;
  return Math.min(100, rating * 20);
}

function priceScoreFn(budget: number | null, estimatedPrice: number): number {
  if (!budget) return 50;
  const ratio = budget / estimatedPrice;
  if (ratio >= 1.2) return 100;
  if (ratio >= 1.0) return 80;
  if (ratio >= 0.8) return 50;
  return 20;
}

function estimatePrice(weightTons: number, distanceKm: number, cargoType: string): number {
  const baseRatePerKm = 45;
  const weightMultiplier = 1 + weightTons * 0.1;
  const cargoMultipliers: Record<string, number> = {
    fuel: 1.5, livestock: 1.3, electronics: 1.4, perishables: 1.35,
    machinery: 1.2, cement: 0.9, grain: 0.95, default: 1.0,
  };
  const multiplier = cargoMultipliers[cargoType] ?? cargoMultipliers.default;
  return Math.round(baseRatePerKm * distanceKm * weightMultiplier * multiplier);
}

async function runMatchFreight(req: AuthRequest, res: any): Promise<void> {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) { res.status(400).json({ error: "Invalid ID" }); return; }

  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, freightId));
  if (!freight) { res.status(404).json({ error: "Freight not found" }); return; }

  const drivers = await db.select().from(driversTable).where(eq(driversTable.isAvailable, true));

  const results = await Promise.all(
    drivers.map(async (driver) => {
      const vehicles = await db.select().from(vehiclesTable).where(
        and(eq(vehiclesTable.driverId, driver.id), eq(vehiclesTable.isAvailable, true))
      );
      const suitableVehicle = vehicles.find(v => v.capacityTons >= freight.weightTons);
      if (!suitableVehicle) return null;

      const distKm =
        driver.currentLatitude && driver.currentLongitude && freight.pickupLatitude && freight.pickupLongitude
          ? haversineKm(driver.currentLatitude, driver.currentLongitude, freight.pickupLatitude, freight.pickupLongitude)
          : null;

      const estimatedDistance = freight.distanceKm ?? 300;
      const estimated = estimatePrice(freight.weightTons, estimatedDistance, freight.cargoType);

      const capScore = capacityScore(suitableVehicle.capacityTons, freight.weightTons);
      const distScore = distanceScoreFn(distKm);
      const ratScore = ratingScoreFn(driver.rating, driver.totalDeliveries);
      const priceScore = priceScoreFn(freight.budget, estimated);
      const matchScore = capScore * 0.3 + distScore * 0.3 + ratScore * 0.25 + priceScore * 0.15;

      const [user] = await db.select().from(usersTable).where(eq(usersTable.id, driver.userId));
      const { passwordHash: _pw, ...safeUser } = user ?? {};

      return {
        driverId: driver.id,
        score: Math.round(matchScore) / 100,
        rating: driver.rating,
        totalDeliveries: driver.totalDeliveries,
        driver: { ...driver, user: safeUser, vehicles },
        matchScore: Math.round(matchScore * 10) / 10,
        capacityScore: capScore,
        distanceScore: distScore,
        ratingScore: ratScore,
        priceScore,
        distanceKm: distKm ? Math.round(distKm) : null,
        estimatedPrice: estimated,
      };
    })
  );

  const sorted = results
    .filter(Boolean)
    .sort((a, b) => (b?.matchScore ?? 0) - (a?.matchScore ?? 0))
    .slice(0, 10);

  res.json({ matches: sorted });
}

router.get("/matching/freight/:id", optionalAuthenticate, runMatchFreight);
router.get("/freight/:id/matches", authenticate, runMatchFreight);

router.get("/matching/price-prediction/:id", optionalAuthenticate, async (req: AuthRequest, res): Promise<void> => {
  const raw = Array.isArray(req.params.id) ? req.params.id[0] : req.params.id;
  const freightId = parseInt(raw, 10);
  if (isNaN(freightId)) { res.status(400).json({ error: "Invalid ID" }); return; }
  const [freight] = await db.select().from(freightRequestsTable).where(eq(freightRequestsTable.id, freightId));
  if (!freight) { res.status(404).json({ error: "Freight not found" }); return; }
  const distanceKm = freight.distanceKm ?? 300;
  const base = estimatePrice(freight.weightTons, distanceKm, freight.cargoType);
  res.json({
    suggestedPrice: base,
    minPrice: Math.round(base * 0.85),
    maxPrice: Math.round(base * 1.2),
    currency: "ETB",
    breakdown: {
      base_rate: Math.round(45 * distanceKm),
      weight_factor: Math.round(base * 0.1),
      cargo_premium: Math.round(base - 45 * distanceKm),
    },
  });
});

router.get("/predict/price", optionalAuthenticate, async (req: AuthRequest, res): Promise<void> => {
  const weightTons = parseFloat(String(req.query.weightTons));
  const distanceKm = parseFloat(String(req.query.distanceKm));
  const cargoType = String(req.query.cargoType || "other");
  if (isNaN(weightTons) || isNaN(distanceKm)) {
    res.status(400).json({ error: "weightTons and distanceKm are required numbers" }); return;
  }
  const recommended = estimatePrice(weightTons, distanceKm, cargoType);
  res.json({
    minPrice: Math.round(recommended * 0.85),
    maxPrice: Math.round(recommended * 1.2),
    recommendedPrice: recommended,
    currency: "ETB",
    pricePerKm: Math.round(recommended / distanceKm),
    pricePerTon: Math.round(recommended / weightTons),
  });
});

router.get("/recommend/vehicle", optionalAuthenticate, async (req: AuthRequest, res): Promise<void> => {
  const weightTons = parseFloat(String(req.query.weightTons));
  const cargoType = String(req.query.cargoType || "other");
  if (isNaN(weightTons)) { res.status(400).json({ error: "weightTons is required" }); return; }

  let truckType: string, capacityRange: string, reason: string, examples: string[];
  if (cargoType === "fuel") {
    truckType = "tanker"; capacityRange = "10,000–30,000 L";
    reason = "Fuel requires a certified tanker truck";
    examples = ["Isuzu Tanker", "MAN TGS Tanker"];
  } else if (cargoType === "perishables") {
    truckType = "refrigerated"; capacityRange = "3–15 tons";
    reason = "Perishables require refrigerated transport";
    examples = ["Isuzu Refrigerated Van", "MAN Refrigerated Truck"];
  } else if (weightTons <= 1.5) {
    truckType = "pickup"; capacityRange = "0.5–1.5 tons";
    reason = "Light cargo fits a pickup truck";
    examples = ["Toyota Hilux", "Isuzu D-Max"];
  } else if (weightTons <= 5) {
    truckType = "light_truck"; capacityRange = "2–5 tons";
    reason = "Light cargo truck is ideal for this load";
    examples = ["Isuzu NPR", "Mitsubishi Canter"];
  } else if (weightTons <= 15) {
    truckType = "medium_truck"; capacityRange = "5–15 tons";
    reason = "Medium cargo truck is ideal for this load";
    examples = ["Isuzu FRR", "MAN TGL", "Hino 300"];
  } else if (["grain", "cement", "construction"].includes(cargoType)) {
    truckType = "tipper"; capacityRange = "15–30 tons";
    reason = "Bulk materials are best in tipper trucks";
    examples = ["MAN TGS Tipper", "Sinotruk Tipper"];
  } else {
    truckType = "heavy_truck"; capacityRange = "15–40 tons";
    reason = "Heavy cargo requires a heavy duty truck";
    examples = ["MAN TGX", "Volvo FH", "Mercedes Actros"];
  }
  res.json({ truckType, capacityRange, reason, examples });
});

export default router;
