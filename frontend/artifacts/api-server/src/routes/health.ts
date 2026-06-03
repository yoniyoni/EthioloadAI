import { Router, type IRouter, type Request, type Response } from "express";
import { HealthCheckResponse } from "@workspace/api-zod";
import { db } from "@workspace/db";

const router: IRouter = Router();

router.get("/healthz", (_req, res) => {
  const data = HealthCheckResponse.parse({ status: "ok" });
  res.json(data);
});

/**
 * Database Health Check Endpoint
 * GET /api/health/db
 * 
 * Tests the database connection and returns status information
 */
router.get("/db", async (req: Request, res: Response) => {
  try {
    // Try to query the database with a simple test
    const result = await db.execute("SELECT NOW() as timestamp");
    
    res.status(200).json({
      status: "healthy",
      message: "Database connection successful",
      timestamp: new Date().toISOString(),
      database: {
        connected: true,
        currentTime: new Date().toISOString(),
      },
    });
  } catch (error: any) {
    console.error("Database health check failed:", error);
    
    res.status(503).json({
      status: "unhealthy",
      message: "Database connection failed",
      error: error?.message || "Unknown error",
      timestamp: new Date().toISOString(),
    });
  }
});

/**
 * Tables Info Endpoint
 * GET /api/health/tables
 * 
 * Lists all tables in the database
 */
router.get("/tables", async (req: Request, res: Response) => {
  try {
    const result = await db.execute(`
      SELECT table_name 
      FROM information_schema.tables 
      WHERE table_schema = 'public'
      ORDER BY table_name
    `);
    
    const tables = result.rows?.map((row: any) => row.table_name) || [];
    
    res.status(200).json({
      status: "success",
      message: "Database tables retrieved",
      tableCount: tables.length,
      tables: tables,
      timestamp: new Date().toISOString(),
    });
  } catch (error: any) {
    console.error("Failed to retrieve tables:", error);
    
    res.status(500).json({
      status: "error",
      message: "Failed to retrieve database tables",
      error: error?.message || "Unknown error",
      timestamp: new Date().toISOString(),
    });
  }
});

export default router;
