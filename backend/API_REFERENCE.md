# EthioLoadAI Backend API Reference

## Overview

This document summarizes the Laravel REST API endpoints exposed by the backend service in `backend/routes/api.php`.

- Base path: `/api`
- Authentication: Laravel Sanctum token auth
- Protected endpoints require `Authorization: Bearer <token>`

---

## Authentication

### Register

- Method: `POST`
- URL: `/api/register`
- Request body:
    - `full_name` (string, required)
    - `phone` (string, required, unique)
    - `email` (string, required, unique)
    - `password` (string, required)
    - `role` (string, required: `shipper`, `driver`, or `admin`)
- Response:
    - `user` object
    - `token` string

### Login

- Method: `POST`
- URL: `/api/login`
- Request body:
    - `email` (string, required)
    - `password` (string, required)
- Response:
    - `user` object
    - `token` string

### Logout

- Method: `POST`
- URL: `/api/logout`
- Authentication: required
- Response:
    - `message` string

### Current user

- Method: `GET`
- URL: `/api/me`
- Authentication: required
- Response:
    - authenticated user object

---

## Users

All user endpoints are protected.

### List users

- Method: `GET`
- URL: `/api/users`
- Response: array of user objects

### Create user

- Method: `POST`
- URL: `/api/users`
- Request body:
    - `full_name` (string, required)
    - `phone` (string, required, unique)
    - `email` (string, required, unique)
    - `password` (string, required)
    - `role` (string, required)
- Response: created user object

### Retrieve user

- Method: `GET`
- URL: `/api/users/{id}`
- Response: user object

### Update user

- Method: `PUT` / `PATCH`
- URL: `/api/users/{id}`
- Request body: any of
    - `full_name`
    - `phone`
    - `email`
    - `password`
    - `role`
- Response: updated user object

### Delete user

- Method: `DELETE`
- URL: `/api/users/{id}`
- Response:
    - `message` string

---

## Vehicles

All vehicle endpoints are protected.

### Register vehicle

- Method: `POST`
- URL: `/api/vehicle/register`
- Request body:
    - `truck_type` (string, required)
    - `plate_number` (string, required, unique)
    - `capacity` (numeric, required)
    - `current_city` (string, required)
    - `latitude` (numeric, optional)
    - `longitude` (numeric, optional)
    - `availability_status` (string, optional)
    - `rating` (numeric, optional)
- Response: created vehicle object

### List vehicles

- Method: `GET`
- URL: `/api/vehicles`
- Response: array of vehicle objects

### Retrieve vehicle

- Method: `GET`
- URL: `/api/vehicles/{id}`
- Response: vehicle object

### Update vehicle

- Method: `PUT` / `PATCH`
- URL: `/api/vehicles/{id}`
- Request body: any of
    - `truck_type`
    - `plate_number`
    - `capacity`
    - `current_city`
    - `latitude`
    - `longitude`
    - `availability_status`
    - `rating`
- Response: updated vehicle object

### Delete vehicle

- Method: `DELETE`
- URL: `/api/vehicles/{id}`
- Response:
    - `message` string

### Nearby vehicles

- Method: `GET`
- URL: `/api/vehicle/nearby`
- Query params:
    - `latitude` (numeric, required)
    - `longitude` (numeric, required)
    - `radius_km` (numeric, optional, default 50)
- Response: array of available vehicle objects near the given coordinates

### Update driver location

- Method: `PATCH`
- URL: `/api/vehicles/{id}/location`
- Request body:
    - `latitude` (numeric, required)
    - `longitude` (numeric, required)
    - `accuracy` (numeric, optional)
- Response: updated vehicle object

---

## Cargo Requests

All cargo request endpoints are protected.

### Create cargo request

- Method: `POST`
- URL: `/api/cargo-requests`
- Alternate URL: `/api/cargo/create`
- Request body:
    - `pickup_location` (string, required)
    - `destination` (string, required)
    - `material_type` (string, required)
    - `weight` (numeric, required)
    - `urgency_level` (string, required)
    - `budget` (numeric, optional)
    - `status` (string, optional: `pending`, `matched`, `completed`)
- Response: created cargo request object

### List cargo requests

- Method: `GET`
- URL: `/api/cargo-requests`
- Response: array of cargo request objects

### Retrieve cargo request

- Method: `GET`
- URL: `/api/cargo-requests/{id}`
- Response: cargo request object

### Update cargo request

- Method: `PUT` / `PATCH`
- URL: `/api/cargo-requests/{id}`
- Request body: any of
    - `pickup_location`
    - `destination`
    - `material_type`
    - `weight`
    - `urgency_level`
    - `budget`
    - `status`
- Response: updated cargo request object

### Delete cargo request

- Method: `DELETE`
- URL: `/api/cargo-requests/{id}`
- Response:
    - `message` string

---

## Bookings

All booking endpoints are protected.

### Create booking

- Method: `POST`
- URL: `/api/bookings`
- Alternate URL: `/api/booking/create`
- Request body:
    - `cargo_request_id` (integer, required)
    - `vehicle_id` (integer, required)
    - `driver_id` (integer, required)
    - `booking_status` (string, required)
    - `estimated_price` (numeric, required)
    - `commission_fee` (numeric, required)
- Response: created booking object

### List bookings

- Method: `GET`
- URL: `/api/bookings`
- Response: array of booking objects

### Retrieve booking

- Method: `GET`
- URL: `/api/bookings/{id}`
- Response: booking object

### Update booking

- Method: `PUT` / `PATCH`
- URL: `/api/bookings/{id}`
- Request body: any of
    - `cargo_request_id`
    - `vehicle_id`
    - `driver_id`
    - `booking_status`
    - `estimated_price`
    - `commission_fee`
- Response: updated booking object

### Delete booking

- Method: `DELETE`
- URL: `/api/bookings/{id}`
- Response:
    - `message` string

---

## AI Engine Proxy

These endpoints forward requests to the FastAPI AI engine via `App\Services\AiEngineService`.

### Recommend truck

- Method: `POST`
- URL: `/api/ai/recommend-truck`
- Request body: AI payload depends on the engine implementation
- Response: JSON result from AI engine

### Backhaul opportunities

- Method: `POST`
- URL: `/api/ai/backhaul-opportunities`
- Request body: AI payload depends on the engine implementation
- Response: JSON result from AI engine

### Predict price

- Method: `POST`
- URL: `/api/ai/predict-price`
- Request body: AI payload depends on the engine implementation
- Response: JSON result from AI engine

---

## Notes

- The API uses resource transformers for consistent JSON output.
- Use the Bearer token from `/api/login` or `/api/register` for authenticated calls.
- `POST /api/cargo/create` and `POST /api/booking/create` are duplicate helper endpoints for the corresponding resource store actions.
