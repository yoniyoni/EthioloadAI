import { Router, type IRouter } from "express";
import { authenticate, optionalAuthenticate, type AuthRequest } from "../middlewares/authenticate";
import { predictPrice, recordPriceHistory } from "../lib/ai/price-predictor";
import { findMatchingDrivers, getDriverStats } from "../lib/ai/driver-matcher";
import { recommendVehicle } from "../lib/ai/vehicle-recommender";
import { processAssistantQuery } from "../lib/ai/freight-assistant";
import { z } from "zod";

const router: IRouter = Router();

const pricePredictionSchema = z.object({
  cargoType: z.string().min(1),
  weightTons: z.number().positive(),
  distanceKm: z.number().positive(),
  pickupRegion: z.string().optional(),
  deliveryRegion: z.string().optional(),
  vehicleType: z.string().optional(),
  fuelPrice: z.number().optional(),
});

const driverMatchSchema = z.object({
  freightId: z.number().int().positive(),
  weightTons: z.number().positive(),
  cargoType: z.string().min(1),
  pickupLat: z.number().optional(),
  pickupLng: z.number().optional(),
  deliveryLat: z.number().optional(),
  deliveryLng: z.number().optional(),
  budget: z.number().optional(),
});

const vehicleRecSchema = z.object({
  weightTons: z.number().positive(),
  cargoType: z.string().min(1),
  distanceKm: z.number().optional(),
  volumeM3: z.number().optional(),
});

const assistantSchema = z.object({
  message: z.string().min(1),
  context: z.object({
    userRole: z.string().optional(),
    userId: z.number().optional(),
    language: z.string().optional(),
  }).optional(),
});

// POST /ai/price-prediction — Real AI price predictor
router.post("/ai/price-prediction", optionalAuthenticate, async (req: AuthRequest, res): Promise<void> => {
  const parsed = pricePredictionSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  try {
    const result = await predictPrice(parsed.data);
    res.json({
      success: true,
      prediction: result,
    });
  } catch (err: any) {
    res.status(500).json({ error: err.message || "Prediction failed" });
  }
});

// GET /ai/price-prediction — Query-based price prediction
router.get("/ai/price-prediction", optionalAuthenticate, async (req: AuthRequest, res): Promise<void> => {
  const weightTons = parseFloat(String(req.query.weightTons));
  const distanceKm = parseFloat(String(req.query.distanceKm));
  const cargoType = String(req.query.cargoType || "other");
  if (isNaN(weightTons) || isNaN(distanceKm)) {
    res.status(400).json({ error: "weightTons and distanceKm are required" });
    return;
  }
  try {
    const result = await predictPrice({ cargoType, weightTons, distanceKm });
    res.json({ success: true, prediction: result });
  } catch (err: any) {
    res.status(500).json({ error: err.message || "Prediction failed" });
  }
});

// POST /ai/driver-recommendations — Real driver matching
router.post("/ai/driver-recommendations", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const parsed = driverMatchSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  try {
    const matches = await findMatchingDrivers(parsed.data);
    res.json({
      success: true,
      freightId: parsed.data.freightId,
      matches,
      totalMatches: matches.length,
    });
  } catch (err: any) {
    res.status(500).json({ error: err.message || "Matching failed" });
  }
});

// GET /ai/vehicle-recommendation — Smart vehicle recommendation
router.get("/ai/vehicle-recommendation", optionalAuthenticate, async (req: AuthRequest, res): Promise<void> => {
  const weightTons = parseFloat(String(req.query.weightTons));
  const cargoType = String(req.query.cargoType || "other");
  const distanceKm = parseFloat(String(req.query.distanceKm || "300"));
  const volumeM3 = parseFloat(String(req.query.volumeM3 || "0")) || undefined;
  if (isNaN(weightTons)) {
    res.status(400).json({ error: "weightTons is required" });
    return;
  }
  const rec = recommendVehicle(weightTons, cargoType, distanceKm, volumeM3);
  res.json({ success: true, recommendation: rec });
});

// POST /ai/vehicle-recommendation — Body-based
router.post("/ai/vehicle-recommendation", optionalAuthenticate, async (req: AuthRequest, res): Promise<void> => {
  const parsed = vehicleRecSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  const rec = recommendVehicle(parsed.data.weightTons, parsed.data.cargoType, parsed.data.distanceKm, parsed.data.volumeM3);
  res.json({ success: true, recommendation: rec });
});

// POST /ai/freight-assistant — AI logistics assistant
router.post("/ai/freight-assistant", optionalAuthenticate, async (req: AuthRequest, res): Promise<void> => {
  const parsed = assistantSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: parsed.error.message });
    return;
  }
  try {
    const response = await processAssistantQuery(parsed.data.message, parsed.data.context);
    res.json({
      success: true,
      response,
    });
  } catch (err: any) {
    res.status(500).json({ error: err.message || "Assistant failed" });
  }
});

// POST /ai/record-price — Record completed price for ML training
router.post("/ai/record-price", authenticate, async (req: AuthRequest, res): Promise<void> => {
  const { freightId, actualPrice, estimatedPrice } = req.body;
  if (!freightId || !actualPrice) {
    res.status(400).json({ error: "freightId and actualPrice are required" });
    return;
  }
  try {
    await recordPriceHistory({
      freightId,
      cargoType: req.body.cargoType || "other",
      weightTons: req.body.weightTons || 0,
      distanceKm: req.body.distanceKm || 300,
      actualPrice,
      estimatedPrice: estimatedPrice || actualPrice,
      driverId: req.body.driverId,
      vehicleType: req.body.vehicleType,
      pickupRegion: req.body.pickupRegion,
      deliveryRegion: req.body.deliveryRegion,
    });
    res.json({ success: true, message: "Price recorded for training" });
  } catch (err: any) {
    res.status(500).json({ error: err.message });
  }
});

export default router;
