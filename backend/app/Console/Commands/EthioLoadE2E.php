<?php

namespace App\Console\Commands;

use Illuminate\Console\Command;

class EthioLoadE2E extends Command
{
    protected $signature   = 'ethioload:e2e {--reset : Wipe all previous test data before running (always performed)}';
    protected $description = 'Full end-to-end system verification — all phases, all roles';

    public function handle(): int
    {
        $this->line('');
        $this->line('  EthioLoadAI — ethioload:e2e');
        $this->line('  Delegates to test:scenario (full implementation)');
        if ($this->option('reset')) {
            $this->line('  --reset: test data will be wiped before each run (default behaviour)');
        }
        $this->line('');

        return $this->call('test:scenario');
    }
}
