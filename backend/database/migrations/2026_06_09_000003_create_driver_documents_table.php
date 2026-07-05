<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('driver_documents', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->constrained('users')->onDelete('cascade');
            $table->enum('document_type', [
                'license',             // Driver's license
                'national_id',         // National ID / Kebele ID
                'vehicle_registration',// Vehicle registration
                'insurance',           // Insurance certificate
                'tin',                 // Tax Identification Number
            ]);
            $table->string('file_path');
            $table->string('original_name');
            $table->enum('status', ['pending', 'approved', 'rejected'])->default('pending');
            $table->text('rejection_reason')->nullable();
            $table->foreignId('reviewed_by')->nullable()->constrained('users')->onDelete('set null');
            $table->timestamp('reviewed_at')->nullable();
            $table->timestamps();

            // One document type per driver — replace on re-upload
            $table->unique(['user_id', 'document_type']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('driver_documents');
    }
};