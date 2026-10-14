-- V48: Reconcile existing dispute databases with the Dispute entity/API contract.
ALTER TABLE disputes
    ADD COLUMN IF NOT EXISTS admin_notes VARCHAR(2000);
