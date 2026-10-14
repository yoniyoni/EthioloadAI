import { useEffect, useRef } from "react";

export type CinematicPhase = "addis" | "ethiopia" | "africa" | "globe";

type Point = { lat: number; lon: number; label: string; accent?: boolean };
type Route = { from: Point; to: Point; progress: number; speed: number; color?: string };

const ADDIS: Point = { lat: 9.03, lon: 38.74, label: "ADDIS ABABA", accent: true };
const NODES: Point[] = [
  ADDIS,
  { lat: 51.5, lon: -0.1, label: "LONDON" },
  { lat: 40.7, lon: -74, label: "NEW YORK" },
  { lat: 1.3, lon: 103.8, label: "SINGAPORE" },
  { lat: 35.7, lon: 139.7, label: "TOKYO" },
  { lat: -33.9, lon: 18.4, label: "CAPE TOWN" },
  { lat: 25.2, lon: 55.3, label: "DUBAI" },
  { lat: 6.52, lon: 3.38, label: "LAGOS" },
  { lat: 19.43, lon: -99.13, label: "MEXICO CITY" },
  { lat: -23.55, lon: -46.63, label: "SAO PAULO" },
  { lat: -31.95, lon: 115.86, label: "PERTH" },
  { lat: 28.61, lon: 77.2, label: "DELHI" },
];

const CONTINENTS: Point[][] = [
  [
    { lat: 37, lon: -17, label: "" }, { lat: 36, lon: 8, label: "" }, { lat: 31, lon: 32, label: "" },
    { lat: 12, lon: 43, label: "" }, { lat: -35, lon: 34, label: "" }, { lat: -35, lon: 18, label: "" },
    { lat: -5, lon: 10, label: "" }, { lat: 4, lon: -17, label: "" },
  ],
  [
    { lat: 72, lon: -25, label: "" }, { lat: 70, lon: 40, label: "" }, { lat: 58, lon: 80, label: "" },
    { lat: 45, lon: 145, label: "" }, { lat: 25, lon: 125, label: "" }, { lat: 8, lon: 104, label: "" },
    { lat: 18, lon: 70, label: "" }, { lat: 28, lon: 35, label: "" },
  ],
  [
    { lat: 72, lon: -165, label: "" }, { lat: 52, lon: -125, label: "" }, { lat: 15, lon: -85, label: "" },
    { lat: -55, lon: -70, label: "" }, { lat: -10, lon: -80, label: "" }, { lat: 10, lon: -60, label: "" },
    { lat: 45, lon: -60, label: "" },
  ],
];

const ETHIOPIAN_NODES: Point[] = [
  ADDIS,
  { lat: 11.59, lon: 37.39, label: "BAHIR DAR" },
  { lat: 11.13, lon: 39.74, label: "DESSIE" },
  { lat: 9.32, lon: 42.12, label: "DIRE DAWA" },
  { lat: 7.06, lon: 38.48, label: "HAWASSA" },
  { lat: 8.98, lon: 38.78, label: "ADAMA" },
  { lat: 8.54, lon: 39.27, label: "JIMMA" },
  { lat: 13.5, lon: 39.47, label: "MEKELLE" },
];

const AFRICAN_NODES: Point[] = [
  ...ETHIOPIAN_NODES,
  { lat: 2.05, lon: 45.32, label: "SOMALIA" },
  { lat: -1.29, lon: 36.82, label: "KENYA" },
  { lat: -6.79, lon: 39.21, label: "TANZANIA" },
  { lat: 6.52, lon: 3.38, label: "LAGOS" },
  { lat: 30.04, lon: 31.24, label: "CAIRO" },
  { lat: -26.2, lon: 28.05, label: "JOHANNESBURG" },
  { lat: -33.92, lon: 18.42, label: "CAPE TOWN" },
];

const GLOBE_LIGHTS: Point[] = [
  { lat: 51.5, lon: -0.1, label: "" },
  { lat: 40.7, lon: -74, label: "" },
  { lat: 25.2, lon: 55.3, label: "" },
  { lat: 1.3, lon: 103.8, label: "" },
  { lat: 35.7, lon: 139.7, label: "" },
  { lat: -23.5, lon: -46.6, label: "" },
  { lat: -33.9, lon: 18.4, label: "" },
  { lat: 28.6, lon: 77.2, label: "" },
  { lat: 31.2, lon: 121.5, label: "" },
  { lat: 48.9, lon: 2.35, label: "" },
];

const CITY_LIGHT_CLUSTERS: Point[] = [
  { lat: 52.5, lon: 13.4, label: "" }, { lat: 48.2, lon: 16.4, label: "" },
  { lat: 41.9, lon: 12.5, label: "" }, { lat: 40.4, lon: -3.7, label: "" },
  { lat: 30.0, lon: 31.2, label: "" }, { lat: 24.7, lon: 46.7, label: "" },
  { lat: 13.7, lon: 100.5, label: "" }, { lat: 22.3, lon: 114.2, label: "" },
  { lat: 37.6, lon: 127.0, label: "" }, { lat: 35.7, lon: 139.7, label: "" },
  { lat: 34.0, lon: -118.2, label: "" }, { lat: 41.9, lon: -87.6, label: "" },
  { lat: -1.3, lon: 36.8, label: "" }, { lat: -6.8, lon: 39.2, label: "" },
  { lat: -26.2, lon: 28.0, label: "" }, { lat: -33.9, lon: 18.4, label: "" },
  { lat: -23.5, lon: -46.6, label: "" }, { lat: -34.6, lon: -58.4, label: "" },
];

const CONTINENT_OUTLINES: Point[][] = [
  [
    { lat: 37, lon: -17, label: "" }, { lat: 36, lon: -5, label: "" }, { lat: 37, lon: 10, label: "" },
    { lat: 32, lon: 25, label: "" }, { lat: 22, lon: 36, label: "" }, { lat: 12, lon: 43, label: "" },
    { lat: 2, lon: 51, label: "" }, { lat: -12, lon: 48, label: "" }, { lat: -26, lon: 42, label: "" },
    { lat: -35, lon: 32, label: "" }, { lat: -35, lon: 18, label: "" }, { lat: -25, lon: 13, label: "" },
    { lat: -18, lon: 12, label: "" }, { lat: -5, lon: 9, label: "" }, { lat: 4, lon: -5, label: "" },
    { lat: 8, lon: -15, label: "" }, { lat: 20, lon: -17, label: "" },
  ],
  [
    { lat: 72, lon: -25, label: "" }, { lat: 71, lon: 8, label: "" }, { lat: 66, lon: 35, label: "" },
    { lat: 58, lon: 62, label: "" }, { lat: 48, lon: 86, label: "" }, { lat: 42, lon: 119, label: "" },
    { lat: 28, lon: 126, label: "" }, { lat: 18, lon: 109, label: "" }, { lat: 8, lon: 104, label: "" },
    { lat: 18, lon: 75, label: "" }, { lat: 28, lon: 52, label: "" }, { lat: 36, lon: 35, label: "" },
    { lat: 42, lon: 15, label: "" }, { lat: 48, lon: -5, label: "" },
  ],
  [
    { lat: 72, lon: -168, label: "" }, { lat: 70, lon: -125, label: "" }, { lat: 58, lon: -102, label: "" },
    { lat: 50, lon: -82, label: "" }, { lat: 35, lon: -78, label: "" }, { lat: 15, lon: -86, label: "" },
    { lat: 8, lon: -78, label: "" }, { lat: -10, lon: -80, label: "" }, { lat: -55, lon: -70, label: "" },
    { lat: -50, lon: -60, label: "" }, { lat: -10, lon: -35, label: "" }, { lat: 8, lon: -60, label: "" },
    { lat: 35, lon: -58, label: "" }, { lat: 55, lon: -62, label: "" },
  ],
];

const ETHIOPIA_SHAPE = [
  [33, 3], [39, 5], [43, 14], [42, 26], [47, 37], [42, 48],
  [35, 51], [30, 45], [27, 36], [28, 26], [25, 17], [29, 9],
].map(([x, y]) => ({ x, y }));

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function lerp(from: number, to: number, amount: number) {
  return from + (to - from) * amount;
}

function easeInOut(value: number) {
  const t = clamp(value, 0, 1);
  return t * t * (3 - 2 * t);
}

const MAPTILER_KEY = import.meta.env.VITE_MAPTILER_KEY as string | undefined;
const TILE_SIZE = 256;
const tileCache = new Map<string, HTMLImageElement | null>();
const earthTexture = new Image();
earthTexture.crossOrigin = "anonymous";
earthTexture.src = "https://unpkg.com/three-globe/example/img/earth-night.jpg";

function worldPixel(lat: number, lon: number, zoom: number) {
  const scale = TILE_SIZE * 2 ** zoom;
  const clampedLat = clamp(lat, -85.0511, 85.0511);
  const sinLat = Math.sin((clampedLat * Math.PI) / 180);
  return {
    x: ((lon + 180) / 360) * scale,
    y: (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * scale,
  };
}

function requestTile(key: string, z: number, x: number, y: number) {
  if (tileCache.has(key) || !MAPTILER_KEY) return;
  const image = new Image();
  image.crossOrigin = "anonymous";
  tileCache.set(key, null);
  image.onload = () => tileCache.set(key, image);
  image.onerror = () => tileCache.delete(key);
  image.src = `https://api.maptiler.com/tiles/satellite-v2/${z}/${x}/${y}.jpg?key=${encodeURIComponent(MAPTILER_KEY)}`;
}

function drawMapTiles(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  center: { lat: number; lon: number },
  zoom: number,
) {
  if (!MAPTILER_KEY) return;
  const tileZoom = clamp(Math.floor(zoom), 1, 14);
  const zoomScale = 2 ** (zoom - tileZoom);
  const centerPixel = worldPixel(center.lat, center.lon, tileZoom);
  const left = centerPixel.x - width / (2 * zoomScale);
  const top = centerPixel.y - height / (2 * zoomScale);
  const worldTiles = 2 ** tileZoom;
  const minX = Math.floor(left / TILE_SIZE) - 1;
  const maxX = Math.floor((left + width) / TILE_SIZE) + 1;
  const minY = Math.max(0, Math.floor(top / TILE_SIZE) - 1);
  const maxY = Math.min(worldTiles - 1, Math.floor((top + height) / TILE_SIZE) + 1);

  ctx.save();
  ctx.globalAlpha = 0.88;
  for (let tileX = minX; tileX <= maxX; tileX += 1) {
    const wrappedX = ((tileX % worldTiles) + worldTiles) % worldTiles;
    for (let tileY = minY; tileY <= maxY; tileY += 1) {
      const key = `${tileZoom}/${wrappedX}/${tileY}`;
      const image = tileCache.get(key);
      if (image) {
        ctx.drawImage(
          image,
          (tileX * TILE_SIZE - left) * zoomScale,
          (tileY * TILE_SIZE - top) * zoomScale,
          TILE_SIZE * zoomScale,
          TILE_SIZE * zoomScale,
        );
      } else {
        requestTile(key, tileZoom, wrappedX, tileY);
      }
    }
  }
  ctx.restore();
}

function project(point: Point, rotation: number, radius: number, cx: number, cy: number) {
  const lat = (point.lat * Math.PI) / 180;
  const lon = (point.lon * Math.PI) / 180 + rotation;
  const cosLat = Math.cos(lat);
  const x = cosLat * Math.cos(lon);
  const y = Math.sin(lat);
  const z = cosLat * Math.sin(lon);
  return { x: cx + x * radius, y: cy - y * radius, z };
}

function drawArc(
  ctx: CanvasRenderingContext2D,
  from: { x: number; y: number; z: number },
  to: { x: number; y: number; z: number },
  radius: number,
) {
  const distance = Math.hypot(to.x - from.x, to.y - from.y);
  const lift = Math.min(radius * 0.44, Math.max(24, distance * 0.26));
  const midX = (from.x + to.x) / 2;
  const midY = (from.y + to.y) / 2 - lift;
  ctx.beginPath();
  ctx.moveTo(from.x, from.y);
  ctx.quadraticCurveTo(midX, midY, to.x, to.y);
  ctx.stroke();
  return { midX, midY };
}

function drawEarthTexture(ctx: CanvasRenderingContext2D, cx: number, cy: number, radius: number, rotation: number) {
  if (!earthTexture.complete || earthTexture.naturalWidth === 0) return;
  ctx.save();
  ctx.globalAlpha = 0.92;
  const sourceWidth = earthTexture.naturalWidth / 2;
  // The texture is equirectangular; this offset keeps East Africa facing the camera first.
  const sourceX = (((rotation / (Math.PI * 2)) + 0.217) % 1) * earthTexture.naturalWidth;
  const drawWrappedTexture = (x: number, sourceStart: number, width: number) => {
    ctx.drawImage(
      earthTexture,
      sourceStart,
      0,
      width,
      earthTexture.naturalHeight,
      x,
      cy - radius,
      (width / sourceWidth) * radius * 2,
      radius * 2,
    );
  };
  if (sourceX + sourceWidth <= earthTexture.naturalWidth) {
    drawWrappedTexture(cx - radius, sourceX, sourceWidth);
  } else {
    const firstWidth = earthTexture.naturalWidth - sourceX;
    drawWrappedTexture(cx - radius, sourceX, firstWidth);
    drawWrappedTexture(cx - radius + (firstWidth / sourceWidth) * radius * 2, 0, sourceWidth - firstWidth);
  }
  ctx.restore();
}

function drawGlow(ctx: CanvasRenderingContext2D, x: number, y: number, color: string, radius: number) {
  const glow = ctx.createRadialGradient(x, y, 0, x, y, radius);
  glow.addColorStop(0, color);
  glow.addColorStop(1, "rgba(0,0,0,0)");
  ctx.fillStyle = glow;
  ctx.beginPath();
  ctx.arc(x, y, radius, 0, Math.PI * 2);
  ctx.fill();
}

function mapProject(point: Point, center: Point, zoom: number, width: number, height: number) {
  const pointPixel = worldPixel(point.lat, point.lon, zoom);
  const centerPixel = worldPixel(center.lat, center.lon, zoom);
  return {
    x: width / 2 + pointPixel.x - centerPixel.x,
    y: height / 2 + pointPixel.y - centerPixel.y,
  };
}

function drawMapRoutes(ctx: CanvasRenderingContext2D, width: number, height: number, center: Point, zoom: number, nodes: Point[], time: number, showLabels: boolean) {
  const scale = 2 ** zoom;
  const visibleNodes = nodes.map((node) => ({ node, point: mapProject(node, center, zoom, width, height) }));
  const routes: Route[] = nodes.slice(1).map((to, index) => ({
    from: ADDIS,
    to,
    progress: (time * (0.00007 + index * 0.000006) + index * 0.17) % 1,
    speed: 0.00007 + index * 0.000006,
    color: index % 3 === 0 ? "rgba(117, 219, 255, 0.8)" : "rgba(255, 190, 102, 0.78)",
  }));

  routes.forEach((route) => {
    const from = mapProject(route.from, center, zoom, width, height);
    const to = mapProject(route.to, center, zoom, width, height);
    if ([from.x, from.y, to.x, to.y].some((value) => !Number.isFinite(value))) return;
    const curve = Math.min(100, Math.hypot(to.x - from.x, to.y - from.y) * 0.24 + 18);
    const midX = (from.x + to.x) / 2;
    const midY = (from.y + to.y) / 2 - curve;
    ctx.strokeStyle = route.color ?? "rgba(255, 190, 102, 0.78)";
    ctx.lineWidth = Math.max(0.7, Math.min(1.6, scale / 1200));
    ctx.shadowColor = ctx.strokeStyle;
    ctx.shadowBlur = 8;
    ctx.beginPath();
    ctx.moveTo(from.x, from.y);
    ctx.quadraticCurveTo(midX, midY, to.x, to.y);
    ctx.stroke();
    ctx.shadowBlur = 0;

    const t = route.progress;
    const inverse = 1 - t;
    const particleX = inverse * inverse * from.x + 2 * inverse * t * midX + t * t * to.x;
    const particleY = inverse * inverse * from.y + 2 * inverse * t * midY + t * t * to.y;
    drawGlow(ctx, particleX, particleY, "rgba(255, 207, 130, 0.48)", 15);
    ctx.fillStyle = "#fff0c4";
    ctx.beginPath();
    ctx.arc(particleX, particleY, 2.1, 0, Math.PI * 2);
    ctx.fill();
  });

  visibleNodes.forEach(({ node, point }) => {
    if (point.x < -50 || point.x > width + 50 || point.y < -50 || point.y > height + 50) return;
    const isAddis = node === ADDIS;
    drawGlow(ctx, point.x, point.y, isAddis ? "rgba(255, 182, 76, 0.6)" : "rgba(255, 210, 135, 0.35)", isAddis ? 34 : 16);
    ctx.fillStyle = isAddis ? "#fff2c2" : "#ffd28a";
    ctx.beginPath();
    ctx.arc(point.x, point.y, isAddis ? 4 : 2.4, 0, Math.PI * 2);
    ctx.fill();
    if (showLabels && (isAddis || zoom >= 5.2)) {
      ctx.fillStyle = "rgba(244, 250, 255, 0.9)";
      ctx.font = `${isAddis ? "600" : "500"} ${isAddis ? 14 : 10}px sans-serif`;
      ctx.fillText(node.label, point.x + 9, point.y - 8);
    }
  });
}

function drawMapPhase(ctx: CanvasRenderingContext2D, width: number, height: number, phase: CinematicPhase, progress: number, time: number) {
  const phases = {
    addis: { lat: 9.03, lon: 38.74, zoom: 8.8 },
    ethiopia: { lat: 9.03, lon: 38.74, zoom: 5.4 },
    africa: { lat: 5.5, lon: 28.5, zoom: 3.1 },
  } as const;
  const target = phases[phase as "addis" | "ethiopia" | "africa"];
  const previous = phase === "addis" ? target : phase === "ethiopia" ? phases.addis : phases.ethiopia;
  const eased = easeInOut(progress);
  const center = {
    lat: lerp(previous.lat, target.lat, eased),
    lon: lerp(previous.lon, target.lon, eased),
  };
  const zoom = lerp(previous.zoom, target.zoom, eased);
  drawMapTiles(ctx, width, height, center, zoom);
  drawMapRoutes(ctx, width, height, { ...center, label: "CENTER" }, zoom, phase === "addis" ? ETHIOPIAN_NODES.slice(0, 6) : phase === "ethiopia" ? ETHIOPIAN_NODES : AFRICAN_NODES, time, phase === "addis");
}

function drawGlobe(ctx: CanvasRenderingContext2D, width: number, height: number, progress: number, time: number, reducedMotion: boolean) {
  const globeProgress = easeInOut(progress);
  const radius = Math.min(width, height) * lerp(0.5, 0.64, globeProgress);
  const cx = width * 0.5;
  const cy = height * 0.5;
  const rotation = reducedMotion ? 0.88 : 0.88 + Math.max(0, time - 1400) * 0.000012;

  ctx.fillStyle = "rgba(0, 4, 12, 0.58)";
  ctx.fillRect(0, 0, width, height);
  drawGlow(ctx, cx, cy, "rgba(39, 130, 214, 0.2)", radius * 1.55);

  const globeGradient = ctx.createRadialGradient(cx - radius * 0.34, cy - radius * 0.38, radius * 0.08, cx, cy, radius * 1.08);
  globeGradient.addColorStop(0, "rgba(42, 93, 122, 0.98)");
  globeGradient.addColorStop(0.58, "rgba(11, 34, 59, 0.99)");
  globeGradient.addColorStop(1, "rgba(1, 7, 18, 1)");
  ctx.fillStyle = globeGradient;
  ctx.beginPath();
  ctx.arc(cx, cy, radius, 0, Math.PI * 2);
  ctx.fill();

  ctx.save();
  ctx.beginPath();
  ctx.arc(cx, cy, radius, 0, Math.PI * 2);
  ctx.clip();
  drawEarthTexture(ctx, cx, cy, radius, rotation);
  const surfaceShade = ctx.createRadialGradient(cx - radius * 0.34, cy - radius * 0.38, radius * 0.12, cx, cy, radius * 1.04);
  surfaceShade.addColorStop(0, "rgba(35, 79, 102, 0.08)");
  surfaceShade.addColorStop(0.72, "rgba(1, 9, 20, 0.12)");
  surfaceShade.addColorStop(1, "rgba(0, 3, 10, 0.74)");
  ctx.fillStyle = surfaceShade;
  ctx.beginPath();
  ctx.arc(cx, cy, radius, 0, Math.PI * 2);
  ctx.fill();
  GLOBE_LIGHTS.forEach((light) => {
    const point = project(light, rotation, radius, cx, cy);
    if (point.z < -0.1) return;
    drawGlow(ctx, point.x, point.y, "rgba(255, 194, 102, 0.38)", 12);
    ctx.fillStyle = "rgba(255, 220, 157, 0.84)";
    ctx.beginPath();
    ctx.arc(point.x, point.y, 1.5, 0, Math.PI * 2);
    ctx.fill();
  });
  CITY_LIGHT_CLUSTERS.forEach((light) => {
    const point = project(light, rotation, radius, cx, cy);
    if (point.z < 0.05) return;
    ctx.fillStyle = "rgba(255, 217, 143, 0.48)";
    ctx.beginPath();
    ctx.arc(point.x, point.y, 0.8, 0, Math.PI * 2);
    ctx.fill();
  });
  ctx.restore();

  const rim = ctx.createRadialGradient(cx, cy, radius * 0.82, cx, cy, radius * 1.08);
  rim.addColorStop(0, "rgba(0, 0, 0, 0)");
  rim.addColorStop(0.82, "rgba(81, 170, 239, 0.03)");
  rim.addColorStop(0.96, "rgba(109, 193, 255, 0.24)");
  rim.addColorStop(1, "rgba(0, 0, 0, 0)");
  ctx.fillStyle = rim;
  ctx.beginPath();
  ctx.arc(cx, cy, radius * 1.08, 0, Math.PI * 2);
  ctx.fill();

  const visibleNodes = NODES.map((node) => ({ node, point: project(node, rotation, radius, cx, cy) }))
    .filter(({ point }) => point.z > -0.12);
  const routePairs = NODES.slice(1).map((to, index) => ({ from: ADDIS, to, index }))
    .concat(NODES.slice(1, -1).map((from, index) => ({ from, to: NODES[index + 2], index: index + 8 })));
  const routes: Route[] = routePairs.map(({ from, to, index }) => ({
    from,
    to,
    progress: ((time * (0.00012 + index * 0.000006) + index * 0.13) % 1),
    speed: 0.00012 + index * 0.000006,
  }));

  routes.forEach((route, index) => {
    const from = project(route.from, rotation, radius, cx, cy);
    const to = project(route.to, rotation, radius, cx, cy);
    if (from.z < -0.25 || to.z < -0.25) return;
    ctx.strokeStyle = index % 3 === 0 ? "rgba(255, 218, 155, 0.7)" : "rgba(178, 221, 255, 0.52)";
    ctx.lineWidth = 0.85;
    ctx.shadowColor = ctx.strokeStyle;
    ctx.shadowBlur = 5;
    const { midX, midY } = drawArc(ctx, from, to, radius);
    ctx.shadowBlur = 0;
    const t = route.progress;
    const oneMinus = 1 - t;
    const dotX = oneMinus * oneMinus * from.x + 2 * oneMinus * t * midX + t * t * to.x;
    const dotY = oneMinus * oneMinus * from.y + 2 * oneMinus * t * midY + t * t * to.y;
    drawGlow(ctx, dotX, dotY, "rgba(255, 215, 145, 0.55)", 13);
    ctx.fillStyle = "#ffe8ae";
    ctx.beginPath();
    ctx.arc(dotX, dotY, 1.8, 0, Math.PI * 2);
    ctx.fill();
  });

  visibleNodes.forEach(({ node, point }) => {
    const alpha = clamp(point.z + 0.35, 0.25, 1);
    ctx.fillStyle = node.accent ? "#ffe7a9" : `rgba(255, 221, 163, ${alpha})`;
    ctx.beginPath();
    ctx.arc(point.x, point.y, node.accent ? 4 : 2.1, 0, Math.PI * 2);
    ctx.fill();
    if (node.accent) drawGlow(ctx, point.x, point.y, "rgba(255, 190, 91, 0.55)", 30);
  });

}

export function CinematicTransport({
  onComplete,
  className = "",
}: {
  onComplete?: () => void;
  className?: string;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const completeRef = useRef(false);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const context = canvas.getContext("2d");
    if (!context) return;

    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const duration = reducedMotion ? 2200 : 12000;
    const startedAt = performance.now();
    let frame = 0;
    let width = 0;
    let height = 0;

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      width = window.innerWidth;
      height = window.innerHeight;
      canvas.width = Math.floor(width * dpr);
      canvas.height = Math.floor(height * dpr);
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;
      context.setTransform(dpr, 0, 0, dpr, 0, 0);
    };

    const draw = (now: number) => {
      const elapsed = now - startedAt;
      const normalized = clamp(elapsed / duration, 0, 1);
      const phase: CinematicPhase = normalized < 0.25
        ? "addis"
        : normalized < 0.5
          ? "ethiopia"
          : normalized < 0.75
            ? "africa"
            : "globe";
      const phaseProgress = phase === "addis"
        ? normalized / 0.25
        : phase === "ethiopia"
          ? (normalized - 0.25) / 0.25
          : phase === "africa"
            ? (normalized - 0.5) / 0.25
            : (normalized - 0.75) / 0.25;

      context.fillStyle = "#020a17";
      context.fillRect(0, 0, width, height);
      const atmosphere = context.createLinearGradient(0, 0, width, height);
      atmosphere.addColorStop(0, "rgba(5, 28, 45, 0.96)");
      atmosphere.addColorStop(0.55, "rgba(2, 14, 30, 0.98)");
      atmosphere.addColorStop(1, "rgba(2, 8, 18, 1)");
      context.fillStyle = atmosphere;
      context.fillRect(0, 0, width, height);

      if (phase !== "globe") {
        drawMapPhase(context, width, height, phase, phaseProgress, now);
      }
      else drawGlobe(context, width, height, phaseProgress, elapsed - 0.75 * duration, reducedMotion);

      if (normalized >= 1 && !completeRef.current) {
        completeRef.current = true;
        onComplete?.();
      }
      frame = requestAnimationFrame(draw);
    };

    resize();
    window.addEventListener("resize", resize);
    frame = requestAnimationFrame(draw);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("resize", resize);
    };
  }, [onComplete]);

  return (
    <div
      className={`absolute inset-0 z-0 overflow-hidden bg-[#020a17] text-white ${className}`}
      aria-hidden="true"
    >
      <canvas ref={canvasRef} className="absolute inset-0 block" />
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-40 bg-gradient-to-t from-[#020a17] to-transparent" />
    </div>
  );
}
