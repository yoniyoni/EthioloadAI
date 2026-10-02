--
-- PostgreSQL database dump
--

\restrict NNw2hpzI5Zu4W1kGgpgFG5giDldD09G7wJ6X5brMzJ6mul4g2aN7BSCbeB6vgqP

-- Dumped from database version 15.18
-- Dumped by pg_dump version 15.18

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: update_admin_notifications_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_admin_notifications_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


--
-- Name: update_backhaul_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_backhaul_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


--
-- Name: update_contracts_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_contracts_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


--
-- Name: update_disputes_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_disputes_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


--
-- Name: update_trip_stops_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_trip_stops_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: admin_notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_notifications (
    id bigint NOT NULL,
    type character varying(50) NOT NULL,
    driver_id bigint NOT NULL,
    vehicle_id bigint,
    message text NOT NULL,
    is_read boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: admin_notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_notifications_id_seq OWNED BY public.admin_notifications.id;


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id bigint NOT NULL,
    actor_id bigint,
    actor_role character varying(30),
    action character varying(100) NOT NULL,
    entity_type character varying(50) NOT NULL,
    entity_id bigint NOT NULL,
    old_value text,
    new_value text,
    notes text,
    ip_address character varying(45),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_logs_id_seq OWNED BY public.audit_logs.id;


--
-- Name: backhaul_recommendations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.backhaul_recommendations (
    id bigint NOT NULL,
    trip_id bigint NOT NULL,
    pickup_location character varying(255) NOT NULL,
    pickup_latitude numeric(10,8),
    pickup_longitude numeric(11,8),
    destination character varying(255) NOT NULL,
    destination_latitude numeric(10,8),
    destination_longitude numeric(11,8),
    material_type character varying(100),
    weight numeric(10,2),
    urgency_level character varying(50),
    budget numeric(12,2),
    distance_km numeric(10,2),
    estimated_revenue numeric(12,2),
    match_score numeric(3,2),
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    dismissed_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: backhaul_recommendations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.backhaul_recommendations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: backhaul_recommendations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.backhaul_recommendations_id_seq OWNED BY public.backhaul_recommendations.id;


--
-- Name: bids; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bids (
    id bigint NOT NULL,
    freight_id bigint NOT NULL,
    driver_id bigint NOT NULL,
    vehicle_id bigint,
    bid_amount numeric(15,2) NOT NULL,
    estimated_duration_hours integer,
    notes text,
    status character varying(20) DEFAULT 'pending'::character varying NOT NULL,
    is_counter_offer boolean DEFAULT false NOT NULL,
    counter_offer_amount numeric(15,2),
    counter_offer_notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT bids_bid_amount_check CHECK ((bid_amount >= (1)::numeric)),
    CONSTRAINT bids_counter_offer_amount_check CHECK ((counter_offer_amount >= (1)::numeric)),
    CONSTRAINT bids_estimated_duration_hours_check CHECK (((estimated_duration_hours >= 1) AND (estimated_duration_hours <= 720))),
    CONSTRAINT bids_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'REJECTED'::character varying, 'WITHDRAWN'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: bids_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bids_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bids_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bids_id_seq OWNED BY public.bids.id;


--
-- Name: bookings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bookings (
    id bigint NOT NULL,
    freight_id bigint NOT NULL,
    bid_id bigint,
    shipper_id bigint NOT NULL,
    driver_id bigint NOT NULL,
    vehicle_id bigint,
    agreed_price numeric(15,2) NOT NULL,
    pickup_scheduled_at timestamp without time zone,
    pickup_actual_at timestamp without time zone,
    delivery_scheduled_at timestamp without time zone,
    delivery_actual_at timestamp without time zone,
    pickup_address character varying(255),
    pickup_latitude numeric(10,7),
    pickup_longitude numeric(10,7),
    delivery_address character varying(255),
    delivery_latitude numeric(10,7),
    delivery_longitude numeric(10,7),
    special_instructions text,
    status character varying(20) DEFAULT 'confirmed'::character varying NOT NULL,
    cancellation_reason text,
    cancelled_by character varying(50),
    cancelled_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT bookings_agreed_price_check CHECK ((agreed_price >= (1)::numeric)),
    CONSTRAINT bookings_status_check CHECK (((status)::text = ANY ((ARRAY['CONFIRMED'::character varying, 'PICKUP_SCHEDULED'::character varying, 'PICKED_UP'::character varying, 'IN_TRANSIT'::character varying, 'DELIVERY_SCHEDULED'::character varying, 'DELIVERED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying, 'DISPUTED'::character varying])::text[])))
);


--
-- Name: bookings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bookings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bookings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bookings_id_seq OWNED BY public.bookings.id;


--
-- Name: contracts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contracts (
    id bigint NOT NULL,
    booking_id bigint NOT NULL,
    agreed_price numeric(15,2) NOT NULL,
    pickup_address character varying(255),
    pickup_latitude numeric(10,7),
    pickup_longitude numeric(10,7),
    delivery_address character varying(255),
    delivery_latitude numeric(10,7),
    delivery_longitude numeric(10,7),
    special_instructions text,
    terms_and_conditions text,
    status character varying(20) DEFAULT 'GENERATED'::character varying NOT NULL,
    payment_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT contracts_payment_status_check CHECK (((payment_status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_ESCROW'::character varying, 'RELEASED'::character varying, 'REFUNDED'::character varying])::text[]))),
    CONSTRAINT contracts_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'GENERATED'::character varying, 'SIGNED'::character varying, 'ACTIVE'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: contracts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.contracts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: contracts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.contracts_id_seq OWNED BY public.contracts.id;


--
-- Name: disputes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.disputes (
    id bigint NOT NULL,
    booking_id bigint NOT NULL,
    reported_by bigint NOT NULL,
    reported_against bigint NOT NULL,
    dispute_type character varying(50) NOT NULL,
    description text NOT NULL,
    evidence_urls text,
    status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    resolution_notes text,
    resolved_by bigint,
    resolved_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT disputes_dispute_type_check CHECK (((dispute_type)::text = ANY ((ARRAY['LATE_DELIVERY'::character varying, 'DAMAGED_CARGO'::character varying, 'MISSING_ITEMS'::character varying, 'DRIVER_MISCONDUCT'::character varying, 'SHIPPER_MISCONDUCT'::character varying, 'PAYMENT_ISSUE'::character varying, 'OTHER'::character varying, 'DELIVERY_DELAY'::character varying, 'DAMAGE'::character varying, 'BEHAVIOR'::character varying, 'SAFETY'::character varying])::text[]))),
    CONSTRAINT disputes_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'UNDER_REVIEW'::character varying, 'RESOLVED'::character varying, 'DISMISSED'::character varying])::text[])))
);


--
-- Name: disputes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.disputes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: disputes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.disputes_id_seq OWNED BY public.disputes.id;


--
-- Name: driver_documents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.driver_documents (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    document_type character varying(100) NOT NULL,
    document_label character varying(255) NOT NULL,
    original_name character varying(255) NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    rejection_reason character varying(500),
    file_url character varying(500) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: driver_documents_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.driver_documents_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: driver_documents_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.driver_documents_id_seq OWNED BY public.driver_documents.id;


--
-- Name: driver_locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.driver_locations (
    id bigint NOT NULL,
    driver_id bigint NOT NULL,
    trip_id bigint NOT NULL,
    latitude numeric(10,7) NOT NULL,
    longitude numeric(10,7) NOT NULL,
    speed numeric(10,2),
    heading integer,
    accuracy numeric(10,2),
    "timestamp" timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: driver_locations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.driver_locations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: driver_locations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.driver_locations_id_seq OWNED BY public.driver_locations.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: freight; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.freight (
    id bigint NOT NULL,
    shipper_id bigint NOT NULL,
    pickup_location character varying(255) NOT NULL,
    pickup_latitude numeric(10,7),
    pickup_longitude numeric(10,7),
    destination character varying(255) NOT NULL,
    destination_latitude numeric(10,7),
    destination_longitude numeric(10,7),
    material_type character varying(50) NOT NULL,
    cargo_description text,
    weight_tons numeric(10,2) NOT NULL,
    volume_m3 numeric(10,2),
    budget numeric(15,2) NOT NULL,
    distance_km numeric(10,2),
    deadline date,
    urgency_level character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'posted'::character varying NOT NULL,
    matched_driver_id bigint,
    matched_vehicle_id bigint,
    matched_price numeric(15,2),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT freight_budget_check CHECK ((budget >= (1)::numeric)),
    CONSTRAINT freight_material_type_check CHECK (((material_type)::text = ANY ((ARRAY['grain'::character varying, 'cement'::character varying, 'construction'::character varying, 'perishables'::character varying, 'electronics'::character varying, 'livestock'::character varying, 'fuel'::character varying, 'general'::character varying, 'other'::character varying])::text[]))),
    CONSTRAINT freight_status_check CHECK (((status)::text = ANY ((ARRAY['POSTED'::character varying, 'MATCHED'::character varying, 'IN_TRANSIT'::character varying, 'DELIVERED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT freight_urgency_level_check CHECK (((urgency_level)::text = ANY ((ARRAY['low'::character varying, 'normal'::character varying, 'high'::character varying, 'urgent'::character varying])::text[]))),
    CONSTRAINT freight_weight_tons_check CHECK ((weight_tons >= 0.1))
);


--
-- Name: freight_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.freight_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: freight_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.freight_id_seq OWNED BY public.freight.id;


--
-- Name: location_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.location_history (
    id bigint NOT NULL,
    driver_id bigint NOT NULL,
    trip_id bigint NOT NULL,
    latitude numeric(10,7) NOT NULL,
    longitude numeric(10,7) NOT NULL,
    speed numeric(10,2),
    heading integer,
    accuracy numeric(10,2),
    "timestamp" timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: location_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.location_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: location_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.location_history_id_seq OWNED BY public.location_history.id;


--
-- Name: payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payments (
    id bigint NOT NULL,
    booking_id bigint NOT NULL,
    payer_id bigint NOT NULL,
    payee_id bigint NOT NULL,
    amount numeric(15,2) NOT NULL,
    payment_type character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'pending'::character varying NOT NULL,
    payment_method character varying(50),
    transaction_id character varying(100),
    payment_gateway_response text,
    notes text,
    paid_at timestamp without time zone,
    released_at timestamp without time zone,
    refunded_at timestamp without time zone,
    refund_reason text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payments_amount_check CHECK ((amount >= (1)::numeric)),
    CONSTRAINT payments_payment_type_check CHECK (((payment_type)::text = ANY ((ARRAY['booking_deposit'::character varying, 'full_payment'::character varying, 'driver_payment'::character varying, 'refund'::character varying, 'cancellation_fee'::character varying, 'dispute_settlement'::character varying])::text[]))),
    CONSTRAINT payments_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REFUNDED'::character varying, 'DISPUTED'::character varying])::text[])))
);


--
-- Name: payments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payments_id_seq OWNED BY public.payments.id;


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    id bigint NOT NULL,
    token character varying(500) NOT NULL,
    user_id bigint NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    revoked_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.refresh_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.refresh_tokens_id_seq OWNED BY public.refresh_tokens.id;


--
-- Name: trip_locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trip_locations (
    id bigint NOT NULL,
    trip_id bigint NOT NULL,
    latitude double precision NOT NULL,
    longitude double precision NOT NULL,
    heading double precision,
    speed_kmh double precision,
    recorded_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: trip_locations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.trip_locations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: trip_locations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.trip_locations_id_seq OWNED BY public.trip_locations.id;


--
-- Name: trip_stops; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trip_stops (
    id bigint NOT NULL,
    trip_id bigint NOT NULL,
    stop_order integer NOT NULL,
    location_name character varying(255) NOT NULL,
    pickup_latitude numeric(10,8),
    pickup_longitude numeric(11,8),
    agreed_price numeric(12,2),
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    arrival_time timestamp without time zone,
    departure_time timestamp without time zone,
    notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: trip_stops_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.trip_stops_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: trip_stops_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.trip_stops_id_seq OWNED BY public.trip_stops.id;


--
-- Name: trips; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trips (
    id bigint NOT NULL,
    booking_id bigint NOT NULL,
    driver_id bigint NOT NULL,
    vehicle_id bigint NOT NULL,
    start_location character varying(255),
    start_latitude numeric(10,7),
    start_longitude numeric(10,7),
    end_location character varying(255),
    end_latitude numeric(10,7),
    end_longitude numeric(10,7),
    distance_km numeric(10,2),
    estimated_duration_hours integer,
    actual_duration_hours integer,
    start_time timestamp without time zone,
    end_time timestamp without time zone,
    route_data text,
    status character varying(20) DEFAULT 'scheduled'::character varying NOT NULL,
    notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    arrived_at timestamp without time zone,
    delivered_at timestamp without time zone,
    receiver_name character varying(120),
    receiver_phone character varying(40),
    receiver_signature_url text,
    delivery_notes text,
    CONSTRAINT trips_actual_duration_hours_check CHECK ((actual_duration_hours >= 1)),
    CONSTRAINT trips_distance_km_check CHECK ((distance_km >= 0.1)),
    CONSTRAINT trips_estimated_duration_hours_check CHECK ((estimated_duration_hours >= 1)),
    CONSTRAINT trips_status_check CHECK (((status)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'IN_PROGRESS'::character varying, 'ARRIVED'::character varying, 'DELIVERED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying, 'DELAYED'::character varying])::text[])))
);


--
-- Name: trips_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.trips_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: trips_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.trips_id_seq OWNED BY public.trips.id;


--
-- Name: user_notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_notifications (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    type character varying(80) NOT NULL,
    data text NOT NULL,
    read_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: user_notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_notifications_id_seq OWNED BY public.user_notifications.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    full_name character varying(255) NOT NULL,
    phone character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    role character varying(50) NOT NULL,
    location character varying(255),
    latitude numeric(10,7),
    longitude numeric(10,7),
    verification_status boolean DEFAULT false,
    is_active boolean DEFAULT true,
    fleet_owner_id bigint,
    remember_token character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['shipper'::character varying, 'driver'::character varying, 'admin'::character varying, 'fleet_owner'::character varying])::text[])))
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: vehicles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vehicles (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    fleet_owner_id bigint,
    truck_type character varying(255) NOT NULL,
    vehicle_category character varying(50),
    plate_number character varying(255) NOT NULL,
    capacity double precision NOT NULL,
    current_city character varying(255),
    latitude numeric(10,7),
    longitude numeric(10,7),
    last_location_at timestamp without time zone,
    availability_status character varying(50) DEFAULT 'available'::character varying NOT NULL,
    rating numeric(3,2),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    vehicle_review_status character varying(20) DEFAULT 'SUBMITTED'::character varying NOT NULL,
    CONSTRAINT vehicles_vehicle_category_check CHECK (((vehicle_category)::text = ANY ((ARRAY['heavy'::character varying, 'light'::character varying])::text[]))),
    CONSTRAINT vehicles_vehicle_review_status_check CHECK (((vehicle_review_status)::text = ANY ((ARRAY['UNDER_REVIEW'::character varying, 'SUBMITTED'::character varying, 'APPROVED'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying])::text[])))
);


--
-- Name: vehicles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.vehicles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: vehicles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.vehicles_id_seq OWNED BY public.vehicles.id;


--
-- Name: admin_notifications id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_notifications ALTER COLUMN id SET DEFAULT nextval('public.admin_notifications_id_seq'::regclass);


--
-- Name: audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ALTER COLUMN id SET DEFAULT nextval('public.audit_logs_id_seq'::regclass);


--
-- Name: backhaul_recommendations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.backhaul_recommendations ALTER COLUMN id SET DEFAULT nextval('public.backhaul_recommendations_id_seq'::regclass);


--
-- Name: bids id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bids ALTER COLUMN id SET DEFAULT nextval('public.bids_id_seq'::regclass);


--
-- Name: bookings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings ALTER COLUMN id SET DEFAULT nextval('public.bookings_id_seq'::regclass);


--
-- Name: contracts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts ALTER COLUMN id SET DEFAULT nextval('public.contracts_id_seq'::regclass);


--
-- Name: disputes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disputes ALTER COLUMN id SET DEFAULT nextval('public.disputes_id_seq'::regclass);


--
-- Name: driver_documents id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.driver_documents ALTER COLUMN id SET DEFAULT nextval('public.driver_documents_id_seq'::regclass);


--
-- Name: driver_locations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.driver_locations ALTER COLUMN id SET DEFAULT nextval('public.driver_locations_id_seq'::regclass);


--
-- Name: freight id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.freight ALTER COLUMN id SET DEFAULT nextval('public.freight_id_seq'::regclass);


--
-- Name: location_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.location_history ALTER COLUMN id SET DEFAULT nextval('public.location_history_id_seq'::regclass);


--
-- Name: payments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments ALTER COLUMN id SET DEFAULT nextval('public.payments_id_seq'::regclass);


--
-- Name: refresh_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens ALTER COLUMN id SET DEFAULT nextval('public.refresh_tokens_id_seq'::regclass);


--
-- Name: trip_locations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trip_locations ALTER COLUMN id SET DEFAULT nextval('public.trip_locations_id_seq'::regclass);


--
-- Name: trip_stops id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trip_stops ALTER COLUMN id SET DEFAULT nextval('public.trip_stops_id_seq'::regclass);


--
-- Name: trips id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trips ALTER COLUMN id SET DEFAULT nextval('public.trips_id_seq'::regclass);


--
-- Name: user_notifications id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_notifications ALTER COLUMN id SET DEFAULT nextval('public.user_notifications_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: vehicles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicles ALTER COLUMN id SET DEFAULT nextval('public.vehicles_id_seq'::regclass);


--
-- Name: admin_notifications admin_notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_notifications
    ADD CONSTRAINT admin_notifications_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: backhaul_recommendations backhaul_recommendations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.backhaul_recommendations
    ADD CONSTRAINT backhaul_recommendations_pkey PRIMARY KEY (id);


--
-- Name: bids bids_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bids
    ADD CONSTRAINT bids_pkey PRIMARY KEY (id);


--
-- Name: bookings bookings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT bookings_pkey PRIMARY KEY (id);


--
-- Name: contracts contracts_booking_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_booking_id_key UNIQUE (booking_id);


--
-- Name: contracts contracts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT contracts_pkey PRIMARY KEY (id);


--
-- Name: disputes disputes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disputes
    ADD CONSTRAINT disputes_pkey PRIMARY KEY (id);


--
-- Name: driver_documents driver_documents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.driver_documents
    ADD CONSTRAINT driver_documents_pkey PRIMARY KEY (id);


--
-- Name: driver_locations driver_locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.driver_locations
    ADD CONSTRAINT driver_locations_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: freight freight_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.freight
    ADD CONSTRAINT freight_pkey PRIMARY KEY (id);


--
-- Name: location_history location_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.location_history
    ADD CONSTRAINT location_history_pkey PRIMARY KEY (id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_key UNIQUE (token);


--
-- Name: trip_locations trip_locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trip_locations
    ADD CONSTRAINT trip_locations_pkey PRIMARY KEY (id);


--
-- Name: trip_stops trip_stops_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trip_stops
    ADD CONSTRAINT trip_stops_pkey PRIMARY KEY (id);


--
-- Name: trips trips_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trips
    ADD CONSTRAINT trips_pkey PRIMARY KEY (id);


--
-- Name: user_notifications user_notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_notifications
    ADD CONSTRAINT user_notifications_pkey PRIMARY KEY (id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_phone_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_phone_key UNIQUE (phone);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: vehicles vehicles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicles
    ADD CONSTRAINT vehicles_pkey PRIMARY KEY (id);


--
-- Name: vehicles vehicles_plate_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicles
    ADD CONSTRAINT vehicles_plate_number_key UNIQUE (plate_number);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_admin_notif_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_notif_created_at ON public.admin_notifications USING btree (created_at DESC);


--
-- Name: idx_admin_notif_driver_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_notif_driver_id ON public.admin_notifications USING btree (driver_id);


--
-- Name: idx_admin_notif_is_read; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_notif_is_read ON public.admin_notifications USING btree (is_read);


--
-- Name: idx_admin_notif_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_notif_type ON public.admin_notifications USING btree (type);


--
-- Name: idx_admin_notif_vehicle_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_admin_notif_vehicle_id ON public.admin_notifications USING btree (vehicle_id);


--
-- Name: idx_audit_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_action ON public.audit_logs USING btree (action);


--
-- Name: idx_audit_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_actor ON public.audit_logs USING btree (actor_id);


--
-- Name: idx_audit_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_created_at ON public.audit_logs USING btree (created_at DESC);


--
-- Name: idx_audit_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_entity ON public.audit_logs USING btree (entity_type, entity_id);


--
-- Name: idx_backhaul_destination; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_backhaul_destination ON public.backhaul_recommendations USING btree (destination);


--
-- Name: idx_backhaul_pickup_location; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_backhaul_pickup_location ON public.backhaul_recommendations USING btree (pickup_location);


--
-- Name: idx_backhaul_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_backhaul_status ON public.backhaul_recommendations USING btree (status);


--
-- Name: idx_backhaul_trip_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_backhaul_trip_id ON public.backhaul_recommendations USING btree (trip_id);


--
-- Name: idx_bids_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bids_created_at ON public.bids USING btree (created_at);


--
-- Name: idx_bids_driver_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bids_driver_id ON public.bids USING btree (driver_id);


--
-- Name: idx_bids_driver_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bids_driver_status ON public.bids USING btree (driver_id, status);


--
-- Name: idx_bids_freight_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bids_freight_id ON public.bids USING btree (freight_id);


--
-- Name: idx_bids_freight_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bids_freight_status ON public.bids USING btree (freight_id, status);


--
-- Name: idx_bids_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bids_status ON public.bids USING btree (status);


--
-- Name: idx_bids_vehicle_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bids_vehicle_id ON public.bids USING btree (vehicle_id);


--
-- Name: idx_bookings_bid_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bookings_bid_id ON public.bookings USING btree (bid_id);


--
-- Name: idx_bookings_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bookings_created_at ON public.bookings USING btree (created_at);


--
-- Name: idx_bookings_driver_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bookings_driver_id ON public.bookings USING btree (driver_id);


--
-- Name: idx_bookings_driver_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bookings_driver_status ON public.bookings USING btree (driver_id, status);


--
-- Name: idx_bookings_freight_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bookings_freight_id ON public.bookings USING btree (freight_id);


--
-- Name: idx_bookings_shipper_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bookings_shipper_id ON public.bookings USING btree (shipper_id);


--
-- Name: idx_bookings_shipper_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bookings_shipper_status ON public.bookings USING btree (shipper_id, status);


--
-- Name: idx_bookings_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bookings_status ON public.bookings USING btree (status);


--
-- Name: idx_bookings_vehicle_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bookings_vehicle_id ON public.bookings USING btree (vehicle_id);


--
-- Name: idx_contracts_booking_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contracts_booking_id ON public.contracts USING btree (booking_id);


--
-- Name: idx_contracts_payment_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contracts_payment_status ON public.contracts USING btree (payment_status);


--
-- Name: idx_contracts_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contracts_status ON public.contracts USING btree (status);


--
-- Name: idx_disputes_booking_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_disputes_booking_id ON public.disputes USING btree (booking_id);


--
-- Name: idx_disputes_reported_against; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_disputes_reported_against ON public.disputes USING btree (reported_against);


--
-- Name: idx_disputes_reported_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_disputes_reported_by ON public.disputes USING btree (reported_by);


--
-- Name: idx_disputes_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_disputes_status ON public.disputes USING btree (status);


--
-- Name: idx_driver_documents_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_driver_documents_status ON public.driver_documents USING btree (status);


--
-- Name: idx_driver_documents_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_driver_documents_type ON public.driver_documents USING btree (document_type);


--
-- Name: idx_driver_documents_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_driver_documents_user_id ON public.driver_documents USING btree (user_id);


--
-- Name: idx_driver_location_driver_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_driver_location_driver_id ON public.driver_locations USING btree (driver_id);


--
-- Name: idx_driver_location_timestamp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_driver_location_timestamp ON public.driver_locations USING btree ("timestamp");


--
-- Name: idx_driver_location_trip_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_driver_location_trip_id ON public.driver_locations USING btree (trip_id);


--
-- Name: idx_freight_deadline; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_freight_deadline ON public.freight USING btree (deadline) WHERE (deadline IS NOT NULL);


--
-- Name: idx_freight_destination; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_freight_destination ON public.freight USING btree (destination);


--
-- Name: idx_freight_matched_driver_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_freight_matched_driver_id ON public.freight USING btree (matched_driver_id) WHERE (matched_driver_id IS NOT NULL);


--
-- Name: idx_freight_matched_vehicle_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_freight_matched_vehicle_id ON public.freight USING btree (matched_vehicle_id) WHERE (matched_vehicle_id IS NOT NULL);


--
-- Name: idx_freight_material_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_freight_material_type ON public.freight USING btree (material_type);


--
-- Name: idx_freight_pickup_location; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_freight_pickup_location ON public.freight USING btree (pickup_location);


--
-- Name: idx_freight_shipper_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_freight_shipper_id ON public.freight USING btree (shipper_id);


--
-- Name: idx_freight_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_freight_status ON public.freight USING btree (status);


--
-- Name: idx_location_history_driver_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_history_driver_id ON public.location_history USING btree (driver_id);


--
-- Name: idx_location_history_timestamp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_history_timestamp ON public.location_history USING btree ("timestamp");


--
-- Name: idx_location_history_trip_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_location_history_trip_id ON public.location_history USING btree (trip_id);


--
-- Name: idx_payments_booking_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_booking_id ON public.payments USING btree (booking_id);


--
-- Name: idx_payments_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_created_at ON public.payments USING btree (created_at);


--
-- Name: idx_payments_payee_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_payee_id ON public.payments USING btree (payee_id);


--
-- Name: idx_payments_payee_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_payee_status ON public.payments USING btree (payee_id, status);


--
-- Name: idx_payments_payer_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_payer_id ON public.payments USING btree (payer_id);


--
-- Name: idx_payments_payer_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_payer_status ON public.payments USING btree (payer_id, status);


--
-- Name: idx_payments_payment_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_payment_type ON public.payments USING btree (payment_type);


--
-- Name: idx_payments_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payments_status ON public.payments USING btree (status);


--
-- Name: idx_refresh_tokens_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_expires_at ON public.refresh_tokens USING btree (expires_at);


--
-- Name: idx_refresh_tokens_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_token ON public.refresh_tokens USING btree (token);


--
-- Name: idx_refresh_tokens_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_user_id ON public.refresh_tokens USING btree (user_id);


--
-- Name: idx_trip_locations_trip_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trip_locations_trip_time ON public.trip_locations USING btree (trip_id, recorded_at DESC);


--
-- Name: idx_trip_stops_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trip_stops_status ON public.trip_stops USING btree (status);


--
-- Name: idx_trip_stops_stop_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trip_stops_stop_order ON public.trip_stops USING btree (trip_id, stop_order);


--
-- Name: idx_trip_stops_trip_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trip_stops_trip_id ON public.trip_stops USING btree (trip_id);


--
-- Name: idx_trips_booking_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trips_booking_id ON public.trips USING btree (booking_id);


--
-- Name: idx_trips_driver_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trips_driver_id ON public.trips USING btree (driver_id);


--
-- Name: idx_trips_driver_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trips_driver_status ON public.trips USING btree (driver_id, status);


--
-- Name: idx_trips_end_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trips_end_time ON public.trips USING btree (end_time);


--
-- Name: idx_trips_start_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trips_start_time ON public.trips USING btree (start_time);


--
-- Name: idx_trips_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trips_status ON public.trips USING btree (status);


--
-- Name: idx_trips_vehicle_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_trips_vehicle_id ON public.trips USING btree (vehicle_id);


--
-- Name: idx_user_notifications_read_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_notifications_read_at ON public.user_notifications USING btree (read_at);


--
-- Name: idx_user_notifications_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_notifications_user_id ON public.user_notifications USING btree (user_id);


--
-- Name: idx_users_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_email ON public.users USING btree (email);


--
-- Name: idx_users_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_phone ON public.users USING btree (phone);


--
-- Name: idx_users_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_role ON public.users USING btree (role);


--
-- Name: idx_vehicles_availability_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vehicles_availability_status ON public.vehicles USING btree (availability_status);


--
-- Name: idx_vehicles_current_city; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vehicles_current_city ON public.vehicles USING btree (current_city);


--
-- Name: idx_vehicles_fleet_owner_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vehicles_fleet_owner_id ON public.vehicles USING btree (fleet_owner_id);


--
-- Name: idx_vehicles_location; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vehicles_location ON public.vehicles USING btree (latitude, longitude);


--
-- Name: idx_vehicles_plate_number; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vehicles_plate_number ON public.vehicles USING btree (plate_number);


--
-- Name: idx_vehicles_review_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vehicles_review_status ON public.vehicles USING btree (vehicle_review_status);


--
-- Name: idx_vehicles_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_vehicles_user_id ON public.vehicles USING btree (user_id);


--
-- Name: admin_notifications admin_notifications_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER admin_notifications_updated_at BEFORE UPDATE ON public.admin_notifications FOR EACH ROW EXECUTE FUNCTION public.update_admin_notifications_updated_at();


--
-- Name: backhaul_recommendations backhaul_recommendations_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER backhaul_recommendations_updated_at BEFORE UPDATE ON public.backhaul_recommendations FOR EACH ROW EXECUTE FUNCTION public.update_backhaul_updated_at();


--
-- Name: contracts contracts_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER contracts_updated_at BEFORE UPDATE ON public.contracts FOR EACH ROW EXECUTE FUNCTION public.update_contracts_updated_at();


--
-- Name: disputes disputes_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER disputes_updated_at BEFORE UPDATE ON public.disputes FOR EACH ROW EXECUTE FUNCTION public.update_disputes_updated_at();


--
-- Name: trip_stops trip_stops_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trip_stops_updated_at BEFORE UPDATE ON public.trip_stops FOR EACH ROW EXECUTE FUNCTION public.update_trip_stops_updated_at();


--
-- Name: admin_notifications fk_admin_notif_driver; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_notifications
    ADD CONSTRAINT fk_admin_notif_driver FOREIGN KEY (driver_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: admin_notifications fk_admin_notif_vehicle; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_notifications
    ADD CONSTRAINT fk_admin_notif_vehicle FOREIGN KEY (vehicle_id) REFERENCES public.vehicles(id) ON DELETE CASCADE;


--
-- Name: audit_logs fk_audit_actor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: backhaul_recommendations fk_backhaul_trip; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.backhaul_recommendations
    ADD CONSTRAINT fk_backhaul_trip FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;


--
-- Name: bids fk_bids_driver_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bids
    ADD CONSTRAINT fk_bids_driver_id FOREIGN KEY (driver_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: bids fk_bids_freight_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bids
    ADD CONSTRAINT fk_bids_freight_id FOREIGN KEY (freight_id) REFERENCES public.freight(id) ON DELETE CASCADE;


--
-- Name: bids fk_bids_vehicle_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bids
    ADD CONSTRAINT fk_bids_vehicle_id FOREIGN KEY (vehicle_id) REFERENCES public.vehicles(id) ON DELETE SET NULL;


--
-- Name: bookings fk_bookings_bid_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT fk_bookings_bid_id FOREIGN KEY (bid_id) REFERENCES public.bids(id) ON DELETE SET NULL;


--
-- Name: bookings fk_bookings_driver_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT fk_bookings_driver_id FOREIGN KEY (driver_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: bookings fk_bookings_freight_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT fk_bookings_freight_id FOREIGN KEY (freight_id) REFERENCES public.freight(id) ON DELETE CASCADE;


--
-- Name: bookings fk_bookings_shipper_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT fk_bookings_shipper_id FOREIGN KEY (shipper_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: bookings fk_bookings_vehicle_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT fk_bookings_vehicle_id FOREIGN KEY (vehicle_id) REFERENCES public.vehicles(id) ON DELETE SET NULL;


--
-- Name: contracts fk_contracts_booking_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contracts
    ADD CONSTRAINT fk_contracts_booking_id FOREIGN KEY (booking_id) REFERENCES public.bookings(id) ON DELETE CASCADE;


--
-- Name: disputes fk_disputes_booking_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disputes
    ADD CONSTRAINT fk_disputes_booking_id FOREIGN KEY (booking_id) REFERENCES public.bookings(id) ON DELETE CASCADE;


--
-- Name: disputes fk_disputes_reported_against; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disputes
    ADD CONSTRAINT fk_disputes_reported_against FOREIGN KEY (reported_against) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: disputes fk_disputes_reported_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disputes
    ADD CONSTRAINT fk_disputes_reported_by FOREIGN KEY (reported_by) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: disputes fk_disputes_resolved_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.disputes
    ADD CONSTRAINT fk_disputes_resolved_by FOREIGN KEY (resolved_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: driver_documents fk_driver_documents_user_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.driver_documents
    ADD CONSTRAINT fk_driver_documents_user_id FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: driver_locations fk_driver_locations_driver_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.driver_locations
    ADD CONSTRAINT fk_driver_locations_driver_id FOREIGN KEY (driver_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: driver_locations fk_driver_locations_trip_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.driver_locations
    ADD CONSTRAINT fk_driver_locations_trip_id FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;


--
-- Name: freight fk_freight_matched_driver_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.freight
    ADD CONSTRAINT fk_freight_matched_driver_id FOREIGN KEY (matched_driver_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: freight fk_freight_matched_vehicle_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.freight
    ADD CONSTRAINT fk_freight_matched_vehicle_id FOREIGN KEY (matched_vehicle_id) REFERENCES public.vehicles(id) ON DELETE SET NULL;


--
-- Name: freight fk_freight_shipper_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.freight
    ADD CONSTRAINT fk_freight_shipper_id FOREIGN KEY (shipper_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: location_history fk_location_history_driver_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.location_history
    ADD CONSTRAINT fk_location_history_driver_id FOREIGN KEY (driver_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: location_history fk_location_history_trip_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.location_history
    ADD CONSTRAINT fk_location_history_trip_id FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;


--
-- Name: payments fk_payments_booking_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fk_payments_booking_id FOREIGN KEY (booking_id) REFERENCES public.bookings(id) ON DELETE CASCADE;


--
-- Name: payments fk_payments_payee_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fk_payments_payee_id FOREIGN KEY (payee_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: payments fk_payments_payer_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fk_payments_payer_id FOREIGN KEY (payer_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: refresh_tokens fk_refresh_tokens_user_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user_id FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: trip_stops fk_trip_stops_trip; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trip_stops
    ADD CONSTRAINT fk_trip_stops_trip FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;


--
-- Name: trips fk_trips_booking_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trips
    ADD CONSTRAINT fk_trips_booking_id FOREIGN KEY (booking_id) REFERENCES public.bookings(id) ON DELETE CASCADE;


--
-- Name: trips fk_trips_driver_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trips
    ADD CONSTRAINT fk_trips_driver_id FOREIGN KEY (driver_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: trips fk_trips_vehicle_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trips
    ADD CONSTRAINT fk_trips_vehicle_id FOREIGN KEY (vehicle_id) REFERENCES public.vehicles(id) ON DELETE CASCADE;


--
-- Name: vehicles fk_vehicles_fleet_owner_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicles
    ADD CONSTRAINT fk_vehicles_fleet_owner_id FOREIGN KEY (fleet_owner_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: vehicles fk_vehicles_user_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vehicles
    ADD CONSTRAINT fk_vehicles_user_id FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: trip_locations trip_locations_trip_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trip_locations
    ADD CONSTRAINT trip_locations_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict NNw2hpzI5Zu4W1kGgpgFG5giDldD09G7wJ6X5brMzJ6mul4g2aN7BSCbeB6vgqP

