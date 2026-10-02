<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        DB::unprepared(<<<'SQL'
            CREATE OR REPLACE FUNCTION sync_vehicle_position() RETURNS trigger AS $$
            BEGIN
              IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
                NEW.position := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
              END IF;
              RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;

            CREATE TRIGGER trg_sync_vehicle_position
            BEFORE INSERT OR UPDATE OF latitude, longitude ON vehicles
            FOR EACH ROW EXECUTE FUNCTION sync_vehicle_position();

            CREATE OR REPLACE FUNCTION sync_cargo_request_points() RETURNS trigger AS $$
            BEGIN
              IF NEW.pickup_lat IS NOT NULL AND NEW.pickup_lng IS NOT NULL THEN
                NEW.pickup_point := ST_SetSRID(ST_MakePoint(NEW.pickup_lng, NEW.pickup_lat), 4326)::geography;
              END IF;
              IF NEW.destination_lat IS NOT NULL AND NEW.destination_lng IS NOT NULL THEN
                NEW.destination_point := ST_SetSRID(ST_MakePoint(NEW.destination_lng, NEW.destination_lat), 4326)::geography;
              END IF;
              RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;

            CREATE TRIGGER trg_sync_cargo_request_points
            BEFORE INSERT OR UPDATE OF pickup_lat, pickup_lng, destination_lat, destination_lng ON cargo_requests
            FOR EACH ROW EXECUTE FUNCTION sync_cargo_request_points();
        SQL);
    }

    public function down(): void
    {
        DB::unprepared(<<<'SQL'
            DROP TRIGGER IF EXISTS trg_sync_vehicle_position ON vehicles;
            DROP FUNCTION IF EXISTS sync_vehicle_position();
            DROP TRIGGER IF EXISTS trg_sync_cargo_request_points ON cargo_requests;
            DROP FUNCTION IF EXISTS sync_cargo_request_points();
        SQL);
    }
};
