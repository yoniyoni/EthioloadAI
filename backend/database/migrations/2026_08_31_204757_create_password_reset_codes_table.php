<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * One table, one 6-digit-code mechanism for both channels — see
     * PasswordResetService. `identifier` is the normalized email or phone
     * (same shapes AuthController::login already looks up by), `channel`
     * records which one so the service knows how to deliver without
     * re-detecting from a possibly-ambiguous stored value.
     */
    public function up(): void
    {
        Schema::create('password_reset_codes', function (Blueprint $table) {
            $table->id();
            $table->string('identifier');
            $table->enum('channel', ['email', 'phone']);
            $table->string('code_hash');
            $table->timestamp('expires_at');
            $table->unsignedTinyInteger('attempts')->default(0);
            $table->timestamp('consumed_at')->nullable();
            $table->timestamps();

            $table->index(['identifier', 'consumed_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('password_reset_codes');
    }
};
