<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        DB::statement("ALTER TABLE driver_documents DROP CONSTRAINT IF EXISTS driver_documents_document_type_check");
        DB::statement("ALTER TABLE driver_documents ADD CONSTRAINT driver_documents_document_type_check CHECK (document_type IN ('license','national_id','vehicle_registration','insurance','tin','professional_permit'))");
    }

    public function down(): void
    {
        DB::statement("ALTER TABLE driver_documents DROP CONSTRAINT IF EXISTS driver_documents_document_type_check");
        DB::statement("ALTER TABLE driver_documents ADD CONSTRAINT driver_documents_document_type_check CHECK (document_type IN ('license','national_id','vehicle_registration','insurance','tin'))");
    }
};
