-- V14: Create the ai_recommendations table.
--
-- Stores AI engine scoring results for a (cargo_request, vehicle) pair.
-- Used to persist price predictions and truck recommendation scores so they
-- can be retrieved by the frontend without re-calling the AI engine on every page load.
--
-- recommendation_score: 0.00–1.00, overall match score for this vehicle+cargo pair.
-- predicted_price:      AI-estimated price in ETB (DECIMAL(15,2) for large amounts).
-- backhaul_probability: 0.00–1.00, probability the driver can find a return load.

CREATE TABLE ai_recommendations (
    id                    BIGSERIAL PRIMARY KEY,
    cargo_request_id      BIGINT        NOT NULL REFERENCES freight(id)   ON DELETE CASCADE,
    vehicle_id            BIGINT        NOT NULL REFERENCES vehicles(id)  ON DELETE CASCADE,
    recommendation_score  DECIMAL(5, 4) NOT NULL CHECK (recommendation_score >= 0 AND recommendation_score <= 1),
    predicted_price       DECIMAL(15, 2) NOT NULL CHECK (predicted_price >= 0),
    backhaul_probability  DECIMAL(5, 4) NOT NULL CHECK (backhaul_probability >= 0 AND backhaul_probability <= 1),
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_rec_cargo_request_id ON ai_recommendations (cargo_request_id);
CREATE INDEX idx_ai_rec_vehicle_id       ON ai_recommendations (vehicle_id);
-- Top recommendations for a cargo request sorted by score
CREATE INDEX idx_ai_rec_cargo_score ON ai_recommendations (cargo_request_id, recommendation_score DESC);
