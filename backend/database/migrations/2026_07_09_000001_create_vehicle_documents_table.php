<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('vehicle_documents', function (Blueprint $table) {
            $table->id();
            $table->foreignId('vehicle_id')->constrained('vehicles')->onDelete('cascade');
            $table->enum('document_type', [
                'registration_certificate',   // Vehicle registration certificate
                'insurance_certificate',      // Insurance certificate
                'roadworthiness_certificate', // Roadworthiness / inspection certificate
                'ownership_document',         // Ownership document
            ]);
            $table->string('file_path');
            $table->string('original_name');
            $table->enum('status', ['pending', 'approved', 'rejected'])->default('pending');
            $table->text('rejection_reason')->nullable();
            $table->foreignId('reviewed_by')->nullable()->constrained('users')->onDelete('set null');
            $table->timestamp('reviewed_at')->nullable();
            $table->timestamps();

            // One document type per vehicle — replace on re-upload
            $table->unique(['vehicle_id', 'document_type']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('vehicle_documents');
    }
};
