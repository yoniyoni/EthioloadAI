import { db, priceHistoryTable } from "@workspace/db";
import { sql } from "drizzle-orm";

export interface PricePredictionInput {
  cargoType: string;
  weightTons: number;
  distanceKm: number;
  pickupRegion?: string;
  deliveryRegion?: string;
  vehicleType?: string;
  season?: string;
  fuelPrice?: number;
}

export interface PricePredictionResult {
  recommendedPrice: number;
  minPrice: number;
  maxPrice: number;
  confidence: number;
  pricePerKm: number;
  pricePerTon: number;
  model: string;
  breakdown: {
    baseDistance: number;
    weightFactor: number;
    cargoPremium: number;
    demandAdjustment: number;
    historicalAvg: number;
  };
}

const CARGO_MULTIPLIERS: Record<string, number> = {
  fuel: 1.5,
  livestock: 1.3,
  electronics: 1.4,
  perishables: 1.35,
  machinery: 1.2,
  cement: 0.9,
  grain: 0.95,
  construction: 1.1,
  furniture: 1.0,
  other: 1.0,
};

const BASE_RATE_PER_KM = 45;

/**
 * Statistical price prediction combining historical data with rule-based fallback.
 * When enough historical data exists, it uses the mean of similar shipments.
 * Falls back to formula-based prediction when data is sparse.
 */
export async function predictPrice(input: PricePredictionInput): Promise<PricePredictionResult> {
  const { cargoType, weightTons, distanceKm, pickupRegion, deliveryRegion } = input;

  const multiplier = CARGO_MULTIPLIERS[cargoType] ?? CARGO_MULTIPLIERS.default;

  // Try to find historical data for similar shipments
  const historicalData = await db
    .select({
      avgPrice: sql<number>`AVG(${priceHistoryTable.actualPrice})`,
      minPrice: sql<number>`MIN(${priceHistoryTable.actualPrice})`,
      maxPrice: sql<number>`MAX(${priceHistoryTable.actualPrice})`,
      count: sql<number>`COUNT(*)`,
      stddev: sql<number>`STDDEV(${priceHistoryTable.actualPrice})`,
    })
    .from(priceHistoryTable)
    .where(
      sql`${priceHistoryTable.cargoType} = ${cargoType}
        AND ${priceHistoryTable.distanceKm} BETWEEN ${distanceKm * 0.7} AND ${distanceKm * 1.3}
        AND ${priceHistoryTable.weightTons} BETWEEN ${weightTons * 0.5} AND ${weightTons * 2}`
    );

  const hist = historicalData[0];
  const hasEnoughData = hist.count > 5 && hist.avgPrice > 0;

  const weightFactor = 1 + weightTons * 0.08;
  const baseDistance = BASE_RATE_PER_KM * distanceKm;
  const formulaPrice = Math.round(baseDistance * weightFactor * multiplier);

  let recommendedPrice: number;
  let confidence: number;
  let demandAdjustment = 0;

  if (hasEnoughData) {
    // Blend historical average with formula prediction
    const historicalWeight = Math.min(hist.count / 50, 0.7);
    const formulaWeight = 1 - historicalWeight;
    recommendedPrice = Math.round(hist.avgPrice * historicalWeight + formulaPrice * formulaWeight);

    // Standard deviation determines confidence
    const stddevPct = hist.stddev / hist.avgPrice;
    confidence = Math.max(0.5, Math.min(0.95, 1 - stddevPct));

    // Adjust min/max based on historical spread
    const spread = Math.max(hist.stddev, formulaPrice * 0.1);
    const minPrice = Math.round(hist.minPrice * 0.9);
    const maxPrice = Math.round(hist.maxPrice * 1.1);

    return {
      recommendedPrice,
      minPrice,
      maxPrice,
      confidence: Math.round(confidence * 100) / 100,
      pricePerKm: Math.round(recommendedPrice / distanceKm),
      pricePerTon: Math.round(recommendedPrice / weightTons),
      model: "historical+formula",
      breakdown: {
        baseDistance: Math.round(baseDistance),
        weightFactor: Math.round(weightFactor * 100) / 100,
        cargoPremium: Math.round(baseDistance * (multiplier - 1)),
        demandAdjustment: Math.round(demandAdjustment),
        historicalAvg: Math.round(hist.avgPrice),
      },
    };
  } else {
    // Formula-based fallback when not enough historical data
    confidence = 0.6;
    recommendedPrice = formulaPrice;

    return {
      recommendedPrice,
      minPrice: Math.round(formulaPrice * 0.85),
      maxPrice: Math.round(formulaPrice * 1.2),
      confidence: Math.round(confidence * 100) / 100,
      pricePerKm: Math.round(formulaPrice / distanceKm),
      pricePerTon: Math.round(formulaPrice / weightTons),
      model: "formula-only",
      breakdown: {
        baseDistance: Math.round(baseDistance),
        weightFactor: Math.round(weightFactor * 100) / 100,
        cargoPremium: Math.round(baseDistance * (multiplier - 1)),
        demandAdjustment: Math.round(demandAdjustment),
        historicalAvg: 0,
      },
    };
  }
}

/**
 * Record a completed shipment's price for future ML training.
 */
export async function recordPriceHistory(
  data: {
    freightId: number;
    cargoType: string;
    weightTons: number;
    distanceKm: number;
    actualPrice: number;
    estimatedPrice: number;
    driverId?: number;
    vehicleType?: string;
    pickupRegion?: string;
    deliveryRegion?: string;
  }
): Promise<void> {
  await db.insert(priceHistoryTable).values({
    ...data,
    season: getCurrentSeason(),
  });
}

function getCurrentSeason(): string {
  const month = new Date().getMonth();
  if (month <= 2) return "bega";
  if (month <= 5) return "belg";
  if (month <= 8) return "kiremt";
  return "meher";
}
