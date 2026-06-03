export interface VehicleRecommendation {
  truckType: string;
  capacityRange: string;
  reason: string;
  examples: string[];
  features: string[];
  estimatedCost: number;
  riskLevel: string;
  recommendationStrength: number;
}

const TRUCK_INFO: Record<string, {
  capacityRange: string;
  examples: string[];
  features: string[];
  riskLevel: string;
}> = {
  pickup: {
    capacityRange: "0.5–1.5 tons",
    examples: ["Toyota Hilux", "Isuzu D-Max", "Ford Ranger"],
    features: ["Fast turnaround", "Door-to-door", "Flexible scheduling"],
    riskLevel: "low",
  },
  light_truck: {
    capacityRange: "2–5 tons",
    examples: ["Isuzu NPR", "Mitsubishi Canter", "Foton Aumark"],
    features: ["Economical for short hauls", "Urban maneuverability", "Refrigerated options available"],
    riskLevel: "low",
  },
  medium_truck: {
    capacityRange: "5–15 tons",
    examples: ["Isuzu FRR", "MAN TGL", "Hino 500"],
    features: ["High payload", "Good for intercity", "Multiple body options"],
    riskLevel: "medium",
  },
  heavy_truck: {
    capacityRange: "15–40 tons",
    examples: ["MAN TGX", "Volvo FH", "Mercedes Actros", "Scania R-Series"],
    features: ["Long haul specialist", "High fuel efficiency", "GPS tracking standard"],
    riskLevel: "medium",
  },
  tanker: {
    capacityRange: "10,000–30,000 L",
    examples: ["Isuzu Tanker", "MAN TGS Tanker", "Sinotruk Tanker"],
    features: ["Hazardous certified", "Sealed containment", "Temperature monitoring"],
    riskLevel: "high",
  },
  refrigerated: {
    capacityRange: "3–15 tons",
    examples: ["Isuzu Refrigerated Van", "MAN Refrigerated Truck", "Carrier Transicold"],
    features: ["Temperature control", "GPS tracking", "Hygienic certification"],
    riskLevel: "medium",
  },
  flatbed: {
    capacityRange: "5–25 tons",
    examples: ["MAN TGS Flatbed", "Volvo FM Flatbed", "Scania G-Series"],
    features: ["Oversized cargo", "Easy loading", "Secure strapping"],
    riskLevel: "medium",
  },
  tipper: {
    capacityRange: "15–30 tons",
    examples: ["MAN TGS Tipper", "Sinotruk Tipper", "Shacman F2000"],
    features: ["Bulk unloading", "Construction ready", "High ground clearance"],
    riskLevel: "medium",
  },
};

const CARGO_VEHICLE_MAP: Record<string, string[]> = {
  fuel: ["tanker"],
  livestock: ["flatbed", "medium_truck"],
  electronics: ["medium_truck", "light_truck"],
  perishables: ["refrigerated", "medium_truck"],
  machinery: ["flatbed", "heavy_truck"],
  cement: ["tipper", "heavy_truck"],
  grain: ["tipper", "heavy_truck"],
  construction: ["tipper", "heavy_truck", "flatbed"],
  furniture: ["light_truck", "medium_truck"],
  other: ["medium_truck", "light_truck"],
};

export function recommendVehicle(
  weightTons: number,
  cargoType: string,
  distanceKm?: number,
  volumeM3?: number
): VehicleRecommendation {
  const cargoTypeNorm = (cargoType || "other").toLowerCase();
  const preferredTypes = CARGO_VEHICLE_MAP[cargoTypeNorm] || CARGO_VEHICLE_MAP.other;

  // Select based on weight and distance
  let selectedType: string;
  const isLongHaul = distanceKm && distanceKm > 200;
  const isHeavy = weightTons > 15;
  const isMedium = weightTons > 5;
  const isLight = weightTons <= 5;

  if (cargoTypeNorm === "fuel") {
    selectedType = "tanker";
  } else if (cargoTypeNorm === "perishables") {
    selectedType = "refrigerated";
  } else if (cargoTypeNorm === "cement" || cargoTypeNorm === "grain") {
    selectedType = "tipper";
  } else if (isHeavy) {
    selectedType = "heavy_truck";
  } else if (isMedium && isLongHaul) {
    selectedType = "heavy_truck";
  } else if (isMedium) {
    selectedType = "medium_truck";
  } else if (isLight) {
    selectedType = "light_truck";
  } else {
    selectedType = preferredTypes[0];
  }

  const info = TRUCK_INFO[selectedType];

  // Build recommendation reason
  let reason: string;
  if (cargoTypeNorm === "fuel") {
    reason = `Fuel requires a certified tanker truck for safety compliance. ${info.examples[0]} is ideal for this hazardous cargo.`;
  } else if (cargoTypeNorm === "perishables") {
    reason = `Perishables require temperature-controlled transport. ${info.examples[0]} with refrigeration ensures cargo quality.`;
  } else if (cargoTypeNorm === "cement" || cargoTypeNorm === "grain") {
    reason = `Bulk materials are best in a ${selectedType} for efficient loading and unloading. ${info.examples[0]} handles ${weightTons} tons well.`;
  } else if (weightTons > 15) {
    reason = `Heavy cargo (${weightTons} tons) requires a ${selectedType} with ${info.capacityRange} capacity. ${info.examples[0]} is recommended.`;
  } else if (isLongHaul) {
    reason = `Long haul (${distanceKm}km) with ${weightTons} tons cargo needs a ${selectedType} for fuel efficiency and reliability.`;
  } else if (isLight) {
    reason = `Light cargo (${weightTons} tons) fits a ${selectedType} for cost-effective short hauls. ${info.examples[0]} is ideal.`;
  } else {
    reason = `${info.examples[0]} is a ${selectedType} with ${info.capacityRange} capacity, perfect for ${weightTons} tons of ${cargoTypeNorm}.`;
  }

  return {
    truckType: selectedType,
    capacityRange: info.capacityRange,
    reason,
    examples: info.examples,
    features: info.features,
    estimatedCost: calculateCost(selectedType, weightTons, distanceKm ?? 300),
    riskLevel: info.riskLevel,
    recommendationStrength: 95,
  };
}

function calculateCost(truckType: string, weightTons: number, distanceKm: number): number {
  const typeMultipliers: Record<string, number> = {
    pickup: 0.7, light_truck: 0.85, medium_truck: 1.0,
    heavy_truck: 1.3, tanker: 1.6, refrigerated: 1.4,
    flatbed: 1.2, tipper: 1.1,
  };
  const base = 45 * distanceKm * (1 + weightTons * 0.08);
  return Math.round(base * (typeMultipliers[truckType] ?? 1.0));
}
