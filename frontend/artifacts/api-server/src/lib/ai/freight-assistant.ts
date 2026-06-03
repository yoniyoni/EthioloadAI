import { predictPrice } from "./price-predictor";
import { recommendVehicle } from "./vehicle-recommender";

export type AssistantIntent =
  | "price_estimate"
  | "vehicle_recommendation"
  | "driver_help"
  | "status_explanation"
  | "shipment_help"
  | "general_help";

export type DetectedLang = "en" | "am" | "om" | "ti";

export interface AssistantMessage {
  role: "user" | "assistant";
  content: string;
  intent?: AssistantIntent;
  data?: any;
}

export interface AssistantResponse {
  text: string;
  intent: AssistantIntent;
  suggestions?: string[];
  action?: "show_price" | "show_vehicle" | "show_drivers" | "show_status" | "none";
  data?: any;
  detectedLang?: DetectedLang;
  draft?: {
    pickup?: string;
    delivery?: string;
    cargoType?: string;
    weightTons?: number;
  };
}

const CARGO_TYPES = ["fuel", "livestock", "electronics", "perishables", "machinery", "cement", "grain", "construction", "furniture", "other"];
const ETHIOPIAN_CITIES = [
  "addis ababa", "adama", "hawassa", "bahir dar", "gondar", "mekelle", "dire dawa",
  "jima", "arba minch", "dessie", "bonga", "shashemene", "axum", "lalibela", "harar",
  "debre markos", "sodo", "wolaita", "nazret", "asosa", "gambela", "semerra", "werabe",
  "dilla", "bure", "adigrat", "maychew", "wukro", "kobo", "kemise", "kombolcha", "dubti",
];

// Amharic/Ge'ez script detection
const AMHARIC_CHARS = /[\u1200-\u137F]/;
const TIGRINYA_CHARS = /[\u1200-\u137F]/;
const OROMO_MARKERS = ["irraa", "irra", "itti", "gara", "dha", "barbaada", "geessu", "qamadii", "toonii", "dura"];

function detectLanguage(text: string): DetectedLang {
  if (AMHARIC_CHARS.test(text)) return "am";
  const lower = text.toLowerCase();
  for (const m of OROMO_MARKERS) {
    if (lower.includes(m)) return "om";
  }
  // Tigrinya often uses same script but may have distinct markers
  if (lower.match(/zeyhale|naye|zhale|yaqonay|seb/)) return "ti";
  return "en";
}

function extractNumbers(text: string): number[] {
  return (text.match(/\d+(?:\.\d+)?/g) || []).map(Number);
}

function detectCargoType(text: string, lang: DetectedLang): string | null {
  const lower = text.toLowerCase();

  // Multilingual cargo detection
  const multilingualCargo: Record<string, string[]> = {
    grain: ["grain", "wheat", "teff", "maize", "corn", "barley", "sorghum",
      "s\\u1294d\\u12dd", "s\\u1295d\\u12dd", "\\u1230\\u1295\\u12f5", "qamadii", "\\u1290\\u1235\\u130d\\u1295"],
    fuel: ["fuel", "oil", "gasoline", "diesel", "petrol",
      "\\u1235\\u1295\\u123d", "\\u1235\\u129b\\u123d", "biillii"],
    livestock: ["livestock", "cow", "cattle", "sheep", "goat", "horse",
      "\\u1265\\u1245", "\\u1265\\u1245\\u1275", "\\u1235\\u1245\\u1290", "horee"],
    electronics: ["electronics", "phone", "computer", "laptop", "tv",
      "\\u1260\\u122b", "\\u1274\\u1209\\u1260\\u1295", "\\u12d3\\u12f0\\u12f0", "laappii"],
    perishables: ["perishables", "food", "fruit", "vegetable", "meat", "milk",
      "\\u12ed\\u12b5\\u12f5", "\\u12ee\\u12ab\\u12d5", "dhiqa", "\\u12b5\\u127d\\u12b3\\u12b5\\u127d"],
    cement: ["cement", "concrete", "\\u12e8\\u12d3\\u12d8", "\\u12d8\\u12d8\\u121d", "\\u12d8\\u12d8\\u121d\\u1235\\u122d\\u12a8\\u12ab\\u12a8", "\\u12c8\\u12f0\\u12d8"],
    construction: ["construction", "steel", "brick", "sand", "stone", "wood",
      "\\u1230\\u12ed\\u121d", "\\u1230\\u12a8\\u120b\\u12d5", "\\u1273\\u1238", "\\u120c\\u130d\\u12d8"],
    furniture: ["furniture", "sofa", "bed", "chair", "table",
      "\\u1270\\u120d\\u12e8\\u12f0", "\\u130d\\u1235\\u122d", "\\u1236\\u1238", "\\u1218\\u12dd\\u1236", "\\u121b\\u1236"],
  };

  for (const [type, words] of Object.entries(multilingualCargo)) {
    for (const word of words) {
      if (lower.includes(word.toLowerCase())) return type;
    }
  }

  for (const ct of CARGO_TYPES) {
    if (lower.includes(ct)) return ct;
  }

  return null;
}

function detectCities(text: string): string[] {
  const lower = text.toLowerCase();
  const found: string[] = [];
  for (const city of ETHIOPIAN_CITIES) {
    if (lower.includes(city)) found.push(city);
  }
  // Amharic city names
  const amharicCities: Record<string, string> = {
    "\\u12a0\\u12f2\\u1235": "addis ababa",
    "\\u12d3\\u12d8\\u12f5": "bahir dar",
    "\\u130d\\u1295\\u12f5\\u122d": "gondar",
    "\\u121c\\u12a8\\u12d8\\u120d": "mekelle",
    "\\u120c\\u12cb\\u1233": "hawassa",
    "\\u12a0\\u12f3\\u121a": "adama",
    "\\u12e8\\u122d\\u122d": "harar",
    "\\u1300\\u12f3\\u12e8": "dire dawa",
    "\\u130b\\u12a8\\u12ab": "jima",
    "\\u12a8\\u12f6\\u1266\\u12da": "kombolcha",
    "\\u12a0\\u12f2\\u130d\\u12f5\\u1275": "adigrat",
    "\\u12a0\\u12f5\\u12a8 \\u121c\\u1295\\u12c8 \\u12a0\\u12f5\\u12a8": "adama",
    "\\u12e8\\u12f5\\u12e8": "dessie",
    "\\u1236\\u12f5\\u12e6": "sodo",
    "\\u12a5\\u12ed\\u123a": "axum",
    "\\u12c8\\u1275\\u12e8": "axum",
    "\\u12a0\\u122d\\u12a8 \\u121c\\u1295\\u12c8": "arba minch",
    "\\u1208\\u12a8\\u12a8\\u120b": "lalibela",
    "\\u1226\\u120d\\u12f5\\u122d": "woldia",
    "\\u12a0\\u12ce\\u12e9": "asosa",
    "\\u12d8\\u120d\\u120d": "dilla",
    "\\u130a\\u12ed \\u12ed\\u129b\\u12ad": "bonga",
    "\\u12e8\\u12d3 \\u12d8\\u12f5": "gambela",
    "\\u12c8\\u12e9\\u12d8\\u12f0\\u120d": "nazret",
  };
  for (const [amName, enName] of Object.entries(amharicCities)) {
    if (lower.includes(amName)) {
      if (!found.includes(enName)) found.push(enName);
    }
  }
  return found;
}

function detectIntent(text: string, lang: DetectedLang): AssistantIntent {
  const lower = text.toLowerCase();

  // Multilingual intent detection
  const priceKeywords: Record<string, string[]> = {
    en: ["price", "cost", "how much", "etb", "birr", "budget", "estimate", "charge", "fee", "expensive", "cheap"],
    am: ["\\u12cb\\u130b", "\\u12e8\\u12ab\\u1321", "\\u12e8\\u12b0\\u12d8", "\\u12d8\\u12a5\\u122d", "\\u12e8\\u12b8\\u132d\\u12e8", "\\u12d8\\u12e8\\u1275", "\\u12e8\\u132d\\u12d5\\u12a8", "\\u12e8\\u12ba\\u1343\\u1341", "\\u12e8\\u12d8\\u12d8\\u121d\\u12a8\\u122d", "\\u12e8\\u1323\\u12e8\\u12e8", "\\u12e8\\u12a8\\u12d0\\u12e8\\u12d8"],
    om: ["gatii", "baasii", "maqa", "gatiin", "meeshaa", "harkisaa", "birr", "meeshaa", "qarshii", "gatiin", "firii", "dhibbaa", "maqa"],
    ti: ["\\u12e9\\u12e8", "\\u12d8\\u12e8", "\\u12d8\\u12d8\\u12e8\\u12e8", "\\u12b5\\u12d8\\u12e8", "\\u1265\\u122d", "\\u12e9\\u12e8"],
  };
  for (const kw of (priceKeywords[lang] || priceKeywords.en)) {
    if (lower.includes(kw.toLowerCase())) return "price_estimate";
  }

  const truckKeywords: Record<string, string[]> = {
    en: ["truck", "vehicle", "car", "lorry", "van", "transport type", "which truck", "what truck", "best truck"],
    am: ["\\u1270\\u123d\\u12a8\\u122d\\u12ab\\u122a", "\\u12a8\\u122d", "\\u121b\\u123d\\u1290", "\\u1270\\u123d\\u12a8\\u122d\\u12ab\\u122a", "\\u12a5\\u1295\\u12f0\\u12f5\\u122d \\u1270\\u123d\\u12a8\\u122d\\u12ab\\u122a", "\\u1218\\u12cd\\u12d9\\u12f5\\u121d", "\\u12eb\\u12d8\\u12ed\\u12e8\\u12ab \\u1270\\u123d\\u12a8\\u122d\\u12ab\\u122a", "\\u1218\\u1290\\u12d8\\u12d8\\u12d8"],
    om: ["konkolaataa", "garba", "lukkoo", "konkolaataa", "gabaa", "konkolaataa", "gaarii", "qarshii", "konkolaataa", "konkolaataa"],
    ti: ["\\u12d8\\u12a8\\u122d\\u12d9\\u12d8", "\\u12d8\\u12d8\\u12d8", "\\u12d8\\u12d8", "\\u12d8\\u12d8\\u12d8", "\\u12d8\\u12d8\\u12d8", "\\u12d8\\u12d8\\u12d8"],
  };
  for (const kw of (truckKeywords[lang] || truckKeywords.en)) {
    if (lower.includes(kw.toLowerCase())) return "vehicle_recommendation";
  }

  const driverKeywords: Record<string, string[]> = {
    en: ["driver", "best driver", "which driver", "recommend driver", "top driver", "who drive"],
    am: ["\\u12a0\\u123d\\u12a8\\u122d\\u12ab\\u122a", "\\u121d\\u122d\\u1325 \\u12a0\\u123d\\u12a8\\u122d\\u12ab\\u122a", "\\u12a5\\u1295\\u12f0\\u12f5\\u122d \\u12a0\\u123d\\u12a8\\u122d\\u12ab\\u122a", "\\u12ab\\u1295\\u12ab\\u12ab\\u122a \\u12a0\\u123d\\u12a8\\u122d\\u12ab\\u122a", "\\u12a8\\u1265\\u12e8\\u122d \\u12a0\\u123d\\u12a8\\u122d\\u12ab\\u122a"],
    om: ["konkolataa", "konkolaataa", "gaarii", "konkolaataa", "gaarii", "konkolaataa"],
    ti: ["\\u12d8\\u12d8\\u12d8", "\\u12d8\\u12d8", "\\u12d8\\u12d8\\u12d8", "\\u12d8\\u12d8"],
  };
  for (const kw of (driverKeywords[lang] || driverKeywords.en)) {
    if (lower.includes(kw.toLowerCase())) return "driver_help";
  }

  const statusKeywords: Record<string, string[]> = {
    en: ["status", "where is", "tracking", "location", "delivered", "in transit", "pending", "cancelled"],
    am: ["\\u1201\\u1294\\u1273", "\\u12a5\\u12d3\\u12d8\\u1295", "\\u12a5\\u12d8\\u12d8\\u12e8\\u122d", "\\u12c8\\u12f0", "\\u12a5\\u12d9\\u12d8\\u12e8\\u12d8\\u12d8", "\\u12eb\\u12d8\\u12d8\\u12d8", "\\u12e8\\u12e8\\u12d8\\u12d8\\u12d8", "\\u12e8\\u12d8\\u12d8\\u12d8\\u12d8"],
    om: ["haala", "eessaa", "hordoffii", "bakka", "ga\'eera", "deemuu jiru", "eegaa", "haquame"],
    ti: ["\\u12d8\\u12d8\\u12d8", "\\u12d8\\u12d8\\u12d8", "\\u12d8\\u12d8\\u12d8", "\\u12d8\\u12d8", "\\u12d8\\u12d8\\u12d8", "\\u12d8\\u12d8\\u12d8"],
  };
  for (const kw of (statusKeywords[lang] || statusKeywords.en)) {
    if (lower.includes(kw.toLowerCase())) return "status_explanation";
  }

  const shipmentKeywords: Record<string, string[]> = {
    en: ["shipment", "cargo", "load", "freight", "post", "create", "how to", "how do", "send"],
    am: ["\\u132d\\u1290\\u1275", "\\u12d8\\u12d8\\u12d8\\u12d8", "\\u121d\\u12f0\\u122d\\u12d8\\u12d8\\u12d8", "\\u12e8\\u12ab\\u12d8\\u12d8\\u12d8", "\\u12e8\\u12d8\\u12d8\\u12d8\\u12d8", "\\u12e8\\u12d8\\u12d8\\u12d8\\u12d8"],
    om: ["qabeenya", "konkolaataa", "lukkoo", "maxxansa", "uuma", "akkan", "erga", "geessu"],
    ti: ["\\u12d8\\u12d8\\u12d8", "\\u12d8\\u12d8\\u12d8", "\\u12d8\\u12d8", "\\u12d8\\u12d8\\u12d8", "\\u12d8\\u12d8\\u12d8", "\\u12d8\\u12d8\\u12d8"],
  };
  for (const kw of (shipmentKeywords[lang] || shipmentKeywords.en)) {
    if (lower.includes(kw.toLowerCase())) return "shipment_help";
  }

  return "general_help";
}

// Response templates in different languages
const RESPONSE_TEMPLATES: Record<string, Record<string, string>> = {
  en: {
    priceIntro: "For {{weight}} tons of {{cargo}} over {{distance}} km, I recommend **ETB {{price}}**.",
    priceRange: "Price range: ETB {{min}} – ETB {{max}}",
    priceConfidence: "Confidence: {{confidence}}%",
    pricePerKm: "Per km: ETB {{perKm}}",
    pricePerTon: "Per ton: ETB {{perTon}}",
    vehicleIntro: "I recommend a **{{truckType}}** ({{capacity}} capacity).",
    vehicleWhy: "Why: {{reason}}",
    vehicleModels: "Popular models: {{models}}",
    vehicleCost: "Estimated cost: ETB {{cost}}",
    vehicleRisk: "Risk level: {{risk}}",
    driverIntro: "To find the best driver for your freight:",
    driverStep1: "1. Post your load on the freight board with pickup, delivery, and cargo details",
    driverStep2: "2. Our AI will automatically match you with top-rated drivers",
    driverStep3: "3. Review match scores and select your preferred driver",
    statusTitle: "Freight Status Guide:",
    shipmentTitle: "How to create a freight shipment:",
    helpIntro: "I'm your FreightLink AI logistics assistant. I can help you with:",
    helpPrice: "Price estimates",
    helpTruck: "Truck recommendations",
    helpDriver: "Driver matching",
    helpStatus: "Status help",
    helpShipment: "Shipment guidance",
    draftCreated: "I understood your request! Here's a draft shipment:",
    draftPickup: "Pickup: {{pickup}}",
    draftDelivery: "Delivery: {{delivery}}",
    draftCargo: "Cargo: {{cargo}}",
    draftWeight: "Weight: {{weight}} tons",
    draftConfirm: "Would you like me to help you post this load?",
  },
  am: {
    priceIntro: "**ETB {{price}}** እንደምን መከረዎ እንዲሆን ይረዳዎናል.",
    priceRange: "የታቀደው የዋጋ መከለያ: ETB {{min}} – ETB {{max}}",
    priceConfidence: "እምነት: {{confidence}}%",
    pricePerKm: "በ1 ኪ.ሜ: ETB {{perKm}}",
    pricePerTon: "በ1 ቶን: ETB {{perTon}}",
    vehicleIntro: "**{{truckType}}** ({{capacity}}) መኪና እንዲመረጥ እንመክራለን.",
    vehicleWhy: "ምክንያት: {{reason}}",
    vehicleModels: "ዝቅተኛ ሞዴሎች: {{models}}",
    vehicleCost: "የተገመተ ወጪ: ETB {{cost}}",
    vehicleRisk: "የደህንነት ደረጃ: {{risk}}",
    driverIntro: "ለመኪና መረጣ ምክንያት:",
    driverStep1: "1. የመነሻ እና የመድረሻ ቦታ እና የዕቃ ዝርዝር ያስገቡ",
    driverStep2: "2. የኛ AI ምርጥ ዝማኔዎችን ያቀርባል",
    driverStep3: "3. የመመዝገብ ምርጫ ያድርጉ",
    statusTitle: "የመኪና ሁኔታ:",
    shipmentTitle: "አዲስ ጭነት እንዴት መፈጠር ይቻላል:",
    helpIntro: "እንዴት መርዳት እችላለሁ?",
    helpPrice: "የዋጋ ግምት",
    helpTruck: "የመኪና ምክር",
    helpDriver: "የዘማኝ መመዝገብ",
    helpStatus: "የሁኔታ መረጃ",
    helpShipment: "የጭነት መመሪያ",
    draftCreated: "ጭነት መረጃ መቀመጥ ተሳክቷል!",
    draftPickup: "መነሻ: {{pickup}}",
    draftDelivery: "መድረሻ: {{delivery}}",
    draftCargo: "ዕቃ: {{cargo}}",
    draftWeight: "ክብደት: {{weight}} ቶን",
    draftConfirm: "ይህን ጭነት መለጠፍ ይፈልጋሉ?",
  },
  om: {
    priceIntro: "{{weight}} tonnii {{cargo}} {{distance}} km irraa gatiin **ETB {{price}}** jira.",
    priceRange: "Gatii gadii: ETB {{min}} – ETB {{max}}",
    priceConfidence: "Amantii: {{confidence}}%",
    pricePerKm: "Km tokko: ETB {{perKm}}",
    pricePerTon: "Tonnii tokko: ETB {{perTon}}",
    vehicleIntro: "**{{truckType}}** ({{capacity}}) konkolaataa dha.",
    vehicleWhy: "Sababa: {{reason}}",
    vehicleModels: "Moodeelli beekamoo: {{models}}",
    vehicleCost: "Gatii ibsame: ETB {{cost}}",
    vehicleRisk: "Dhibee: {{risk}}",
    driverIntro: "Konkolataa gaarii qabaachuuf:",
    driverStep1: "1. Bakka fudhannaa fi bakka geesannaa galchi",
    driverStep2: "2. AI keenya konkolataa gaarii qabaatee",
    driverStep3: "3. Scoori ilaali fi filadhu",
    statusTitle: "Haala Qabeenyaa:",
    shipmentTitle: "Qabeenya haaraa akkamitti uuma?",
    helpIntro: "Ani gargaarsa AI FreightLink dha. Akkan gargaaru:",
    helpPrice: "Gatii ibsuu",
    helpTruck: "Konkolaataa filachuu",
    helpDriver: "Konkolataa gaarii",
    helpStatus: "Haala hubachuu",
    helpShipment: "Qabeenya uumuu",
    draftCreated: "Gaaffiin kee hubatame! Draft qabeenyaa:",
    draftPickup: "Fudhannaa: {{pickup}}",
    draftDelivery: "Geesannaa: {{delivery}}",
    draftCargo: "Qabeenya: {{cargo}}",
    draftWeight: "Cimina: {{weight}} tonnii",
    draftConfirm: "Qabeenya kana maxxansuu barbaaddaa?",
  },
  ti: {
    priceIntro: "**ETB {{price}}** nayizokka'ayo.",
    priceRange: "Nayizokka'ayo: ETB {{min}} – ETB {{max}}",
    priceConfidence: "Amantii: {{confidence}}%",
    pricePerKm: "Km 1: ETB {{perKm}}",
    pricePerTon: "Tonn 1: ETB {{perTon}}",
    vehicleIntro: "**{{truckType}}** ({{capacity}}) naykonay'ayo.",
    vehicleWhy: "Sababa: {{reason}}",
    vehicleModels: "Moodeelli: {{models}}",
    vehicleCost: "Gatii: ETB {{cost}}",
    vehicleRisk: "Dhibee: {{risk}}",
    driverIntro: "Naykonay'ayo:",
    driverStep1: "1. Bakka fudhannaa fi bakka geesannaa galchi",
    driverStep2: "2. AI naykonay'ayo",
    driverStep3: "3. Scoori ilaali fi filadhu",
    statusTitle: "Haala:",
    shipmentTitle: "Akka naykone:",
    helpIntro: "Naykonay'ayo:",
    helpPrice: "Gatii",
    helpTruck: "Konkolaataa",
    helpDriver: "Konkolataa",
    helpStatus: "Haala",
    helpShipment: "Qabeenya",
    draftCreated: "Naykonay'ayo!",
    draftPickup: "Fudhannaa: {{pickup}}",
    draftDelivery: "Geesannaa: {{delivery}}",
    draftCargo: "Qabeenya: {{cargo}}",
    draftWeight: "Cimina: {{weight}} tonn",
    draftConfirm: "Naykonay'ayo?",
  },
};

function renderTemplate(key: string, lang: DetectedLang, vars: Record<string, string | number>): string {
  const tpl = RESPONSE_TEMPLATES[lang]?.[key] ?? RESPONSE_TEMPLATES.en[key] ?? key;
  return tpl.replace(/\{\{(\w+)\}\}/g, (_, v) => String(vars[v] ?? ""));
}

export async function processAssistantQuery(
  text: string,
  context?: { userRole?: string; userId?: number; language?: string }
): Promise<AssistantResponse> {
  const detectedLang = detectLanguage(text);
  const lang = context?.language ? (context.language as DetectedLang) : detectedLang;
  const intent = detectIntent(text, lang);
  const numbers = extractNumbers(text);
  const cargoType = detectCargoType(text, lang);
  const cities = detectCities(text);

  // Build draft if enough info extracted
  const draft: { pickup?: string; delivery?: string; cargoType?: string; weightTons?: number } = {};
  if (cities.length >= 2) {
    draft.pickup = cities[0];
    draft.delivery = cities[1];
  }
  if (cargoType) draft.cargoType = cargoType;
  const weight = numbers.find(n => n > 0 && n <= 100);
  if (weight) draft.weightTons = weight;

  switch (intent) {
    case "price_estimate": {
      const weight = numbers.find(n => n > 0 && n <= 100) ?? 10;
      const dist = numbers.find(n => n > 100 && n <= 2000) ??
        (cities.length >= 2 ? estimateDistance(cities[0], cities[1]) : 300);
      const cargo = cargoType ?? "other";

      const priceResult = await predictPrice({
        cargoType: cargo,
        weightTons: weight,
        distanceKm: dist,
      });

      const text = renderTemplate("priceIntro", lang, {
        weight, cargo, distance: Math.round(dist),
        price: priceResult.recommendedPrice.toLocaleString(),
      }) + "\n\n" +
        renderTemplate("priceRange", lang, { min: priceResult.minPrice.toLocaleString(), max: priceResult.maxPrice.toLocaleString() }) + "\n" +
        renderTemplate("priceConfidence", lang, { confidence: Math.round(priceResult.confidence * 100) }) + "\n" +
        renderTemplate("pricePerKm", lang, { perKm: priceResult.pricePerKm }) + "\n" +
        renderTemplate("pricePerTon", lang, { perTon: priceResult.pricePerTon });

      return {
        text,
        intent: "price_estimate",
        action: "show_price",
        data: priceResult,
        detectedLang: lang,
        draft,
        suggestions: (lang === "am" ? ["ይህን ጭነት መለጠፍ ይፈልጋሉ?", "ምን ዓይነት መኪና ይፈልጋሉ?", "ምን ያህል ጊዜ ይወስዳል?"] :
          lang === "om" ? ["Qabeenya kana maxxansuu barbaaddaa?", "Konkolaataa gaarii?", "Yeroo gafsi?"] :
          ["What truck should I use for this?", "Which drivers are best for this route?", "How do I post this load?"]),
      };
    }

    case "vehicle_recommendation": {
      const weight = numbers.find(n => n > 0 && n <= 100) ?? 10;
      const cargo = cargoType ?? "other";
      const dist = numbers.find(n => n > 100 && n <= 2000) ?? 300;
      const rec = recommendVehicle(weight, cargo, dist);

      const text = renderTemplate("vehicleIntro", lang, {
        truckType: rec.truckType.replace(/_/g, " "),
        capacity: rec.capacityRange,
      }) + "\n\n" +
        renderTemplate("vehicleWhy", lang, { reason: rec.reason }) + "\n" +
        renderTemplate("vehicleModels", lang, { models: rec.examples.join(", ") }) + "\n" +
        renderTemplate("vehicleCost", lang, { cost: rec.estimatedCost.toLocaleString() }) + "\n" +
        renderTemplate("vehicleRisk", lang, { risk: rec.riskLevel });

      return {
        text,
        intent: "vehicle_recommendation",
        action: "show_vehicle",
        data: rec,
        detectedLang: lang,
        draft,
        suggestions: (lang === "am" ? ["ይህን ጭነት መለጠፍ ይፈልጋሉ?", "ምን ያህል ጊዜ ይወስዳል?", "ምን ያህል ይከፈላል?"] :
          lang === "om" ? ["Qabeenya kana maxxansuu barbaaddaa?", "Yeroo gafsi?", "Gatii maali?"] :
          ["How much will this cost?", "Show me available drivers", "Post this load now"]),
      };
    }

    case "driver_help": {
      const text = renderTemplate("driverIntro", lang, {}) + "\n\n" +
        renderTemplate("driverStep1", lang, {}) + "\n" +
        renderTemplate("driverStep2", lang, {}) + "\n" +
        renderTemplate("driverStep3", lang, {}) + "\n\n" +
        (lang === "am" ? "የማንኛውም እርዳታ ቢፈልጉ ይጠይቁኝ." :
         lang === "om" ? "Yoo gargaarsa barbaaddan, na gafadhaa." :
         lang === "ti" ? "Naykonay'ayo." :
         "Drivers are ranked by total score, so you see the best matches first.");
      return {
        text,
        intent: "driver_help",
        action: "show_drivers",
        detectedLang: lang,
        draft,
        suggestions: (lang === "am" ? ["ምን ያህል ይከፈላል?", "አዲስ ጭነት መፈጠር", "የተለጠፈ ጭነቶች"] :
          lang === "om" ? ["Gatii maali?", "Qabeenya haaraa", "Qabeenya maxxaname"] :
          ["How does matching work?", "Post a new load", "Check my posted loads"]),
      };
    }

    case "status_explanation": {
      const text = lang === "am" ? `**የመኪና ሁኔታ መረጃ:**

• **Draft** — ጭነት በመፈጠር ላይ
• **Posted** — ጭነት በመረጃ ሰሌዳ ላይ
• **Matched** — ጭነት ከሾፌር ጋር ተመሳሳል
• **Accepted** — ሁለቱም ወገኖች ተስማሙ
• **In Transit** — ጭነት በመንገድ ላይ
• **Delivered** — ጭነት ተደርሷል
• **Completed** — ጭነት ተረጋገጠ
• **Cancelled** — ጭነት ተሰርዟል

**ምክር:** "የኔ ጭነቶች" ላይ ጠቅ በማድረጉ የእርስዎን ጭነቶች ሁኔታ ይመልከቱ.` :
        lang === "om" ? `**Haala Qabeenyaa:**

• **Draft** — Qabeenya uumamaa jira
• **Posted** — Qabeenya maxxaname
• **Matched** — Qabeenya michuun
• **Accepted** — Lammii lamaan waliigalan
• **In Transit** — Qabeenya deemuu jira
• **Delivered** — Qabeenya geesame
• **Completed** — Qabeenya xumurame
• **Cancelled** — Qabeenya haquame

**Akeekkachiisa:** "Qabeenya Koo" irratti ilaaluu dandeessu.` :
        lang === "ti" ? `**Haala:**

• **Draft** — Naye
• **Posted** — Naye
• **Matched** — Naye
• **Accepted** — Naye
• **In Transit** — Naye
• **Delivered** — Naye
• **Completed** — Naye
• **Cancelled** — Naye

**Naye.**` :
        `**Freight Status Guide:**

• **Draft** — Load is being created, not yet visible
• **Posted** — Load is live on the board, drivers can see it
• **Matched** — A driver has been selected, waiting for agreement
• **Accepted** — Both parties agree, preparing for shipment
• **In Transit** — Cargo is on the road, live tracking active
• **Delivered** — Cargo reached destination, pending confirmation
• **Completed** — Delivery confirmed, payment released
• **Cancelled** — Load was cancelled by either party

**Tip:** Use "My Freight" to see all your loads and their current status.`;

      return {
        text,
        intent: "status_explanation",
        action: "show_status",
        detectedLang: lang,
        draft,
        suggestions: (lang === "am" ? ["የአሁን ጭነት መከታተል", "እንዴት መደረሻ መረጋገጥ ይቻላል?", "ክፍያ መቼ ይለቀቃል?"] :
          lang === "om" ? ["Qabeenya ammaa hordofuu", "Akkamitti geesuu?", "Kaffalti yeroo?"] :
          ["Track my current shipment", "How do I confirm delivery?", "When is payment released?"]),
      };
    }

    case "shipment_help": {
      const text = lang === "am" ? `**አዲስ ጭነት እንዴት መፈጠር ይቻላል:**

1. **"አዲስ ጭነት መለጠፍ"** ጠቅ ያድርጉ
2. **የመነሻ ቦታ** ያስገቡ
3. **የመድረሻ ቦታ** ያስገቡ
4. **የዕቃ ዓይነት** ይምረጡ
5. **ክብደት** በቶን ያስገቡ
6. **የመጨረሻ ጊዜ** ይምረጡ
7. **የበጀት** ይምረጡ (AI ይጠቁማል)
8. **የተረዳታኝ መረጃ** ያስገቡ
9. **ይላኩ** — ጭነት በረጃ ሰሌዳ ላይ!

AI ወዲያውኑ:
- የዋጋ ግምት ይሰጣል
- ምርጥ የመኪና ዓይነት ይጠቁማል
- ምርጥ ሾፌሮችን ያመሳስል

ከተለጠፈ በኋላ ሾፌሮች በዋጋ መጠቆም ይችላሉ.` :
        lang === "om" ? `**Qabeenya haaraa akkamitti uuma?**

1. **"Qabeenya Maxxansi"** cuqaasi
2. **Bakka fudhannaa** galchi
3. **Bakka geesannaa** galchi
4. **Gosa qabeenyaa** filadhu
5. **Cimina** tonnii galchi
6. **Yeroo xumuraa** filadhu
7. **Bajata** filadhu (AI gorsa)
8. **Ibsa** galchi
9. **Uumu** — qabeenya maxxaname!

AI amma:
- Gatii ibsa
- Konkolaataa gaarii gorsa
- Konkolataa gaarii qabatee

Konkolataan qabeenya dhiheessuun danda\'ama.` :
        lang === "ti" ? `**Naye:**

1. **Naye**
2. **Naye**
3. **Naye**
4. **Naye**
5. **Naye**
6. **Naye**
7. **Naye**
8. **Naye**
9. **Naye**

Naye.

Naye.

Naye.

Naye.` :
        `**How to create a freight shipment:**

1. **Click "Post Freight"** or go to the freight board
2. **Enter pickup location** (city or specific address)
3. **Enter delivery location**
4. **Select cargo type** (grain, fuel, electronics, etc.)
5. **Enter weight** in tons
6. **Set your deadline** (when cargo must arrive)
7. **Set a budget** (optional — our AI will suggest a price)
8. **Add any special instructions** (fragile, temperature needs, etc.)
9. **Submit** — your load is live!

Our AI will immediately:
- Estimate the fair price
- Recommend the best truck type
- Match you with top-rated drivers

Once posted, drivers can apply with their bids.`;

      return {
        text,
        intent: "shipment_help",
        action: "none",
        detectedLang: lang,
        draft,
        suggestions: (lang === "am" ? ["ምን ያህል ጊዜ ይወስዳል?", "ምን ዓይነት መኪና ይፈልጋሉ?", "የጭነት ገደብ አለ?"] :
          lang === "om" ? ["Yeroo gafsi?", "Konkolaataa gaarii?", "Qabeenya kana?"] :
          ["How much should I budget?", "What truck do I need?", "How do driver bids work?"]),
      };
    }

    default: {
      const text = lang === "am" ? `የFreightLink AI የጭነት መረጃ ጠበቃ ነኝ። እንዴት መርዳት እችላለሁ?

• **የዋጋ ግምት** — "ከአዲስ ወደ ሃዋሳ 5 ቶን የሚያዘዋውን ዋጋ?"
• **የመኪና ምክር** — "10 ቶን ሲሚንት ለመጓጓዝ ምን ዓይነት መኪና ያስፈልጋል?"
• **የሾፌር ምከር** — "ለዚህ መስመር ምን ዓይነት ሾፌር ይመረጣል?"
• **የሁኔታ መረጃ** — "In transit ምን ያለ?"
• **የጭነት መመሪያ** — "አዲስ ጭነት እንዴት መፈጠር ይቻላል?"

ከኢትዮጵያ ጭነት ጋር በተያያዘ ማንኛውም ጥያቄ ጠይቁኝ!` :
        lang === "om" ? `Ani gargaarsa AI FreightLink dha. Akkamitti gargaaru?

• **Gatii ibsuu** — "Addis irraa Hawassa gara 5 tonnii gatii maali?"
• **Konkolaataa filachuu** — "10 tonnii simintoo gara?"
• **Konkolataa gaarii** — "Karaa kanaaf konkolataa gaarii?"
• **Haala hubachuu** — "In transit maali?"
• **Qabeenya uumuu** — "Qabeenya haaraa akkamitti uuma?"

Yoo gargaarsa barbaaddan, na gafadhaa!` :
        lang === "ti" ? `Naye.

• **Naye**
• **Naye**
• **Naye**
• **Naye**
• **Naye**

Naye.` :
        `I'm your FreightLink AI logistics assistant. I can help you with:

• **Price estimates** — "How much to transport 5 tons from Addis to Hawassa?"
• **Truck recommendations** — "What truck for 10 tons of cement?"
• **Driver matching** — "Which driver is best for my route?"
• **Status help** — "What does 'in transit' mean?"
• **Shipment guidance** — "How do I post a new load?"

Just ask me anything about freight logistics in Ethiopia!`;

      return {
        text,
        intent: "general_help",
        action: "none",
        detectedLang: lang,
        draft,
        suggestions: (lang === "am" ? ["ከአዲስ ወደ ጎንደር 10 ቶን ስንዴ ዋጋ?", "ከአዲስ ወደ ሃዋሳ ዋጋ?", "አዲስ ጭነት እንዴት መፈጠር ይቻላል?"] :
          lang === "om" ? ["Addis irraa Gondar gara 10 tonnii qamadii gatii?", "Addis irraa Hawassa gatii?", "Qabeenya haaraa akkamitti uuma?"] :
          ["How much for 10 tons of grain to Gondar?", "What truck for fuel transport?", "How do I post a load?"]),
      };
    }
  }
}

const DISTANCE_MAP: Record<string, Record<string, number>> = {
  "addis ababa": { "adama": 100, "hawassa": 275, "bahir dar": 565, "gondar": 720, "mekelle": 780, "dire dawa": 445, "jima": 350, "arba minch": 500, "dessie": 400, "shashemene": 250, "dilla": 360, "kombolcha": 380, "wolaita": 450 },
  "adama": { "addis ababa": 100, "hawassa": 175, "dire dawa": 345, "shashemene": 150, "dilla": 260, "jima": 250 },
  "hawassa": { "addis ababa": 275, "adama": 175, "arba minch": 225, "dilla": 85, "shashemene": 25, "sodo": 120, "wolaita": 100 },
  "bahir dar": { "addis ababa": 565, "gondar": 155, "debre markos": 200, "dessie": 250, "kombolcha": 280, "lalibela": 180, "axum": 350 },
  "gondar": { "addis ababa": 720, "bahir dar": 155, "mekelle": 310, "axum": 210, "lalibela": 130, "debre markos": 355, "dessie": 405 },
  "mekelle": { "addis ababa": 780, "gondar": 310, "axum": 200, "adigrat": 110, "wukro": 50, "maychew": 120, "dessie": 380 },
  "dire dawa": { "addis ababa": 445, "adama": 345, "harar": 60, "jima": 395, "shashemene": 295 },
  "jima": { "addis ababa": 350, "adama": 250, "bonga": 120, "dilla": 140, "shashemene": 200, "wolaita": 180 },
  "arba minch": { "addis ababa": 500, "hawassa": 225, "sodo": 105, "wolaita": 85, "dilla": 140, "jima": 280 },
  "dessie": { "addis ababa": 400, "bahir dar": 250, "gondar": 405, "kombolcha": 80, "mekelle": 380, "woldia": 80, "kobo": 50 },
  "kombolcha": { "addis ababa": 380, "bahir dar": 280, "dessie": 80, "woldia": 60, "kemise": 70 },
  "dilla": { "addis ababa": 360, "hawassa": 85, "adama": 260, "jima": 140, "bonga": 90, "shashemene": 110 },
};

function estimateDistance(from: string, to: string): number {
  const f = DISTANCE_MAP[from];
  if (f && f[to]) return f[to];
  // Symmetric lookup
  const t = DISTANCE_MAP[to];
  if (t && t[from]) return t[from];
  // Default
  return 300;
}
