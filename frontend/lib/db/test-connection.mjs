/**
 * Database Connection Test Script
 * 
 * Usage: pnpm --filter @workspace/db exec node test-connection.mjs
 * 
 * This script tests the PostgreSQL connection without starting the full application
 */

import pg from "pg";
import * as dotenv from "dotenv";

dotenv.config({ path: "../../.env" });

const { Pool } = pg;

async function testConnection() {
  const DATABASE_URL = process.env.DATABASE_URL;
  
  if (!DATABASE_URL) {
    console.error("❌ Error: DATABASE_URL is not set");
    console.error("   Please create a .env file or set the DATABASE_URL environment variable");
    process.exit(1);
  }

  console.log("🔄 Testing PostgreSQL connection...");
  console.log(`   Database URL: ${DATABASE_URL.replace(/:[^:]*@/, ":***@")}`);
  console.log("");

  const pool = new Pool({ connectionString: DATABASE_URL });

  try {
    // Test connection
    const client = await pool.connect();
    console.log("✓ Connected to PostgreSQL");

    // Get version
    const versionResult = await client.query("SELECT VERSION()");
    console.log(`✓ PostgreSQL Version: ${versionResult.rows[0].version}`);

    // Get current time
    const timeResult = await client.query("SELECT NOW()");
    console.log(`✓ Server Time: ${timeResult.rows[0].now}`);

    // List tables
    const tablesResult = await client.query(`
      SELECT table_name 
      FROM information_schema.tables 
      WHERE table_schema = 'public'
      ORDER BY table_name
    `);
    
    const tables = tablesResult.rows.map((row) => row.table_name);
    
    console.log("");
    console.log(`✓ Database Tables (${tables.length}):`);
    if (tables.length > 0) {
      tables.forEach((table) => console.log(`   - ${table}`));
    } else {
      console.log("   (No tables found - run migrations to create them)");
    }

    // Get table details
    console.log("");
    for (const table of tables) {
      const columnResult = await client.query(`
        SELECT column_name, data_type, is_nullable
        FROM information_schema.columns
        WHERE table_name = $1
        ORDER BY ordinal_position
      `, [table]);
      
      console.log(`   📋 ${table} (${columnResult.rows.length} columns):`);
      columnResult.rows.forEach((col) => {
        console.log(`      - ${col.column_name}: ${col.data_type} ${col.is_nullable === 'NO' ? 'NOT NULL' : ''}`);
      });
    }

    client.release();
    
    console.log("");
    console.log("✓ Connection test successful!");
    console.log("");
    console.log("Next steps:");
    console.log("  1. Run migrations: pnpm --filter @workspace/db run push");
    console.log("  2. Start API server: pnpm --filter @workspace/api-server run dev");
    
  } catch (error) {
    console.error("❌ Connection test failed!");
    console.error("");
    if (error instanceof Error) {
      console.error(`Error: ${error.message}`);
    } else {
      console.error("Unknown error occurred");
    }
    
    // Provide helpful debugging info
    console.error("");
    console.error("Troubleshooting:");
    console.error("  1. Ensure PostgreSQL is running");
    console.error("  2. Verify credentials in .env file:");
    console.error(`     - DATABASE_URL=${DATABASE_URL}`);
    console.error("  3. Check if database exists:");
    console.error("     - psql -U korebdan -h localhost -l");
    console.error("  4. Test with psql directly:");
    console.error("     - psql -U korebdan -h localhost -d freight");
    
    process.exit(1);
  } finally {
    await pool.end();
  }
}

testConnection();
