# Mobile Integration Plan - Freight Platform Flutter App

## Executive Summary

This document outlines the complete API integration strategy for the Flutter mobile application consuming the existing Express backend. The Flutter app will preserve all workflows from the web platform while providing role-based mobile experiences.

---

## 1. Authentication APIs

### 1.1 User Registration
```
POST /api/auth/register
Body: { name, email, password, phone, role }
Response: { token, user }
Notes:
  - Only shipper can self-register
  - Role-based registration (driver/fleet_owner/admin created by admin)
  - Password hashed with bcryptjs
```

### 1.2 User Login
```
POST /api/auth/login
Body: { email, password }
Response: { token, user }
Notes:
  - Returns JWT token for authorization
  - Token includes userId and role
  - Support refresh token mechanism
```

### 1.3 Get Current User
```
GET /api/auth/me
Headers: Authorization: Bearer {token}
Response: { id, name, email, phone, role, ... }
Notes:
  - Validates JWT and returns user profile
  - Used for session verification
```

### 1.4 Mobile Implementation
- **Secure Token Storage**: Use flutter_secure_storage
- **Biometric Auth**: Implement fingerprint/face recognition
- **Token Refresh**: Auto-refresh expired tokens
- **Offline Support**: Cache auth state locally

---

## 2. User APIs

### 2.1 Get User Profile
```
GET /api/auth/me
Response: User object with all fields
```

### 2.2 Update User Profile
```
PATCH /api/users/me
Body: { name, phone, address, businessName, avatarUrl, preferredLanguage }
Response: Updated user object
```

### 2.3 Get User by ID
```
GET /api/users/:id
Response: User object (public profile)
```

### 2.4 List Users
```
GET /api/users?limit=50&offset=0
Response: { users: [], total: number }
```

### 2.5 Mobile Implementation
- **Profile Caching**: Cache user data with Hive
- **Avatar Upload**: Support image upload to CDN
- **Language Preference**: Store in local settings
- **Offline Profile**: Show cached data when offline

---

## 3. Freight APIs

### 3.1 Create Freight Request
```
POST /api/freight
Headers: Authorization: Bearer {token}
Body: {
  pickupLocation, pickupLatitude, pickupLongitude,
  deliveryLocation, deliveryLatitude, deliveryLongitude,
  cargoType, weightTons, volumeM3,
  budget, deadline, description
}
Response: { id, shipperId, status, ... }
```

### 3.2 List All Freight (Public/Browse)
```
GET /api/freight?limit=20&offset=0&status=pending&cargoType=electronics
Response: { freight: [], total: number }
```

### 3.3 Get My Freight (User's Shipments)
```
GET /api/freight/my?status=pending
Headers: Authorization: Bearer {token}
Response: Freight list for authenticated user
```

### 3.4 Get Freight Details
```
GET /api/freight/:id
Response: { ...freight, shipper: {...} }
```

### 3.5 Update Freight
```
PATCH /api/freight/:id
Body: { status, matchedDriverId, ... }
```

### 3.6 Mobile Implementation
- **Create Wizard**: Multi-step form with validation
- **Map Integration**: Pick locations on Google Maps
- **Real-time List**: Refresh freight list periodically
- **Offline Draft**: Save draft freight requests locally
- **Auto-save**: Save progress during creation

---

## 4. Applications (Agreements) APIs

### 4.1 Driver Applies for Freight
```
POST /api/freight/:id/apply
Headers: Authorization: Bearer {token}
Body: {
  offerPrice, availableVehicleId, estimatedPickupTime, message
}
Response: Application object with freight and driver details
Notes:
  - Driver must have driver profile
  - Updates freight status to "matched"
```

### 4.2 Get Applications for Freight
```
GET /api/freight/:id/applications
Headers: Authorization: Bearer {token}
Response: Array of applications with driver info
```

### 4.3 Accept/Reject Application
```
PATCH /api/applications/:id
Body: { status: "accepted" | "rejected", counterOffer?: number }
Response: Updated application
```

### 4.4 Mobile Implementation
- **Application List**: Show drivers who applied
- **Driver Details**: Display driver profile, rating, vehicles
- **Counter Offer**: Support price negotiation
- **Match History**: Show previous interactions
- **Quick Accept**: One-tap application acceptance

---

## 5. Contract APIs

### 5.1 Generate Contract
```
POST /api/contracts/:freightId/generate
Headers: Authorization: Bearer {token}
Response: { contract, freight, driver, message }
Notes:
  - Generates digital agreement
  - Requires matched driver
  - Status: "active" after generation
```

### 5.2 Get Contract
```
GET /api/contracts/:freightId
Response: Contract details
```

### 5.3 Mobile Implementation
- **Contract View**: Display contract in PDF format
- **E-signature**: Support digital signature
- **Download PDF**: Export contract as PDF
- **Terms Display**: Show readable contract terms
- **Accept/Reject**: Legal agreement acceptance

---

## 6. Payment APIs

### 6.1 Initialize Escrow Payment
```
POST /api/payments/initialize
Headers: Authorization: Bearer {token}
Body: { freightId, amount, provider: "chapa" | "cbe_birr" }
Response: { payment, providerUrl }
Notes:
  - Amount includes platform commission (10%)
  - Escrow status: "pending_payment"
  - Redirects to payment provider
```

### 6.2 Get Payment Status
```
GET /api/payments/:freightId
Response: Payment object with escrow details
Notes:
  - Shows: amount, commission, driver receives, status
```

### 6.3 Payment Webhook
```
POST /api/payments/webhook
Body: { transactionId, status, amount, ... }
Notes:
  - Backend updates escrow and freight status
  - Driver notified when payment confirmed
```

### 6.4 Mobile Implementation
- **Payment Gateway Integration**: Chapa SDK integration
- **Escrow Display**: Show payment breakdown
- **Payment History**: List all transactions
- **Receipt Generation**: Download payment receipts
- **Retry Failed Payment**: Handle payment failures gracefully

---

## 7. Tracking APIs

### 7.1 Post GPS Update
```
POST /api/tracking
Headers: Authorization: Bearer {token}
Body: { freightId, latitude, longitude, altitude, accuracy, speed }
Response: { id, timestamp, ... }
Notes:
  - Called frequently by driver app (every 30-60 sec)
  - Updates driver's current location
  - Stored in tracking_locations table
```

### 7.2 Get Tracking History
```
GET /api/tracking/:freightId
Headers: Authorization: Bearer {token}
Response: Array of location updates ordered by timestamp
```

### 7.3 Get Latest Location
```
GET /api/tracking/:freightId/latest
Response: Most recent tracking update
```

### 7.4 Mobile Implementation
- **Real-time Tracking**: Show driver on map
- **Route Visualization**: Draw route from history
- **ETA Calculation**: Calculate remaining distance/time
- **Notification**: Alert shipper of driver location
- **Map Clustering**: Cluster multiple points for performance
- **Background Tracking**: Continue tracking even when backgrounded

---

## 8. AI Assistant APIs

### 8.1 Price Prediction
```
POST /api/ai/price-prediction
Body: {
  cargoType, weightTons, distanceKm,
  pickupRegion?, deliveryRegion?, vehicleType?, fuelPrice?
}
Response: { success, prediction: { estimatedPrice, range, factors } }

GET /api/ai/price-prediction?cargoType=electronics&weightTons=2&distanceKm=150
Response: { success, prediction: {...} }
```

### 8.2 Driver Matching
```
POST /api/ai/driver-match
Body: {
  freightId, weightTons, cargoType, pickupLat?, pickupLng?,
  deliveryLat?, deliveryLng?, budget?
}
Response: Array of matching drivers sorted by match score
```

### 8.3 Vehicle Recommendation
```
POST /api/ai/vehicle-recommend
Body: { weightTons, cargoType, distanceKm?, volumeM3? }
Response: Recommended vehicle types with specifications
```

### 8.4 Chat Assistant
```
POST /api/ai/assistant
Body: {
  message: "string",
  context: { userRole?, userId?, language? }
}
Response: { reply, suggestions, confidence }
```

### 8.5 Mobile Implementation
- **Price Estimation**: Show estimated cost during freight creation
- **Driver Recommendations**: Display top matching drivers
- **AI Chat**: In-app AI assistant with multi-language support
- **Smart Suggestions**: Vehicle and route recommendations
- **Performance Optimization**: Cache predictions

---

## 9. Chat APIs

### 9.1 Send Message
```
POST /api/messages
Headers: Authorization: Bearer {token}
Body: {
  freightId?, receiverId, content, type: "text" | "image" | "file"
}
Response: { message, warning?: "Payment requests prohibited" }
Notes:
  - Masks phone numbers when payment active
  - Detects payment requests and shows warning
```

### 9.2 Get Messages
```
GET /api/messages/:freightId
Headers: Authorization: Bearer {token}
Response: Array of messages ordered by timestamp
```

### 9.3 Mobile Implementation
- **Real-time Chat**: WebSocket for live messaging
- **Message History**: Paginated message loading
- **File Sharing**: Support image/PDF sharing
- **Notifications**: Push notification for new messages
- **Read Receipts**: Show message status (sent/delivered/read)
- **Offline Queue**: Queue messages to send when online

---

## 10. Driver APIs

### 10.1 Create Driver Profile
```
POST /api/drivers/profile
Headers: Authorization: Bearer {token}
Body: {
  licenseNumber, licenseExpiry, experience,
  bankAccount, emergencyContact, rating: 0, deliveries: 0
}
Response: Driver profile with user info
```

### 10.2 Update Driver Status
```
PATCH /api/drivers/:id
Body: { status: "available" | "offline" | "rejected", isAvailable: boolean }
```

### 10.3 Get Driver Profile
```
GET /api/drivers/:id
Response: Driver object with vehicles and user info
```

### 10.4 List Drivers
```
GET /api/drivers?status=available&available=true&limit=20
Response: Paginated driver list
```

### 10.5 Mobile Implementation
- **Driver Signup**: Complete driver profile form
- **Status Toggle**: Quick availability switching
- **Earnings Dashboard**: Show total earnings
- **Trip History**: List completed deliveries
- **Rating Display**: Show driver rating

---

## 11. Matching APIs

### 11.1 Auto-match Freight
```
GET /api/freight/:id/match
Headers: Authorization: Bearer {token}
Response: Array of matching drivers with scores
Notes:
  - Uses AI matching algorithm
  - Considers: distance, capacity, rating, price, specialization
```

### 11.2 Mobile Implementation
- **AI Recommendations**: Show recommended drivers
- **Match Score**: Display matching percentage
- **Manual Matching**: Allow manual driver selection
- **Counter Offers**: Support price negotiation

---

## 12. Ratings APIs

### 12.1 Rate Driver/Shipper
```
POST /api/ratings
Headers: Authorization: Bearer {token}
Body: { freightId, rateeId, rating: 1-5, comment, categories: {...} }
Response: Rating object
```

### 12.2 Get Ratings
```
GET /api/ratings/user/:userId
Response: Array of ratings with comments
```

### 12.3 Mobile Implementation
- **Post-Delivery Rating**: Rate after delivery
- **Rating Components**: Category-based ratings
- **Feedback Form**: Collect detailed feedback
- **Rating History**: View all ratings received

---

## 13. Disputes APIs

### 13.1 Create Dispute
```
POST /api/disputes
Headers: Authorization: Bearer {token}
Body: { freightId, reason, description, attachments?: [] }
Response: Dispute object
```

### 13.2 Update Dispute
```
PATCH /api/disputes/:id
Body: { status: "open" | "resolved" | "escalated", resolution?: "" }
```

### 13.3 Mobile Implementation
- **Report Issue**: File dispute about shipment
- **Attachment Support**: Add photos/documents
- **Status Tracking**: Monitor dispute resolution

---

## 14. Admin APIs

### 14.1 Admin Dashboard
```
GET /api/admin/dashboard
Response: { revenue, escrowBalance, driverCount, shipmentCount, ... }
```

### 14.2 List All Users
```
GET /api/admin/users?role=driver&status=pending
```

### 14.3 Approve/Reject Driver
```
PATCH /api/admin/drivers/:id
Body: { status: "approved" | "rejected" }
```

### 14.4 Mobile Implementation
- **Admin Dashboard**: Overview of key metrics
- **User Management**: Approve drivers/shippers
- **Fraud Monitoring**: Flag suspicious activity
- **Payment Reports**: Revenue and transaction reports

---

## 15. WebSocket Events (Real-time)

### Real-time Communication
```
Events:
- "new_freight" - New shipment created
- "driver_matched" - Driver matched for freight
- "driver_location_update" - Driver location changed
- "message_received" - New message
- "freight_status_changed" - Freight status updated
- "payment_confirmed" - Payment processed
- "delivery_completed" - Delivery finished
```

### Mobile Implementation
- **Socket.IO Integration**: Connect on app startup
- **Event Listeners**: Subscribe to relevant events
- **Background Connection**: Maintain connection when backgrounded
- **Reconnection**: Auto-reconnect on network change

---

## 16. Security Implementation

### 16.1 Authentication
- JWT tokens with expiry
- Refresh token rotation
- Biometric fallback

### 16.2 Data Security
- HTTPS only
- Sensitive data encryption (flutter_secure_storage)
- API key masking

### 16.3 Role-Based Access
```
Admin: All endpoints
Support: Dispute management, user moderation
Driver: Driver-specific endpoints + freight browsing
Shipper: Shipper-specific endpoints + driver browsing
Fleet Owner: Fleet management + driver oversight
```

---

## 17. Error Handling

### Standard Error Responses
```json
{
  "error": "Human readable error message",
  "code": "ERROR_CODE",
  "statusCode": 400,
  "details": {}
}
```

### Mobile Handling
- Retry failed requests automatically
- Show user-friendly error messages
- Log errors for debugging
- Fallback to cached data when possible

---

## 18. Performance Optimization

### 18.1 Caching Strategy
- Cache user data for 1 hour
- Cache freight list for 5 minutes
- Cache driver profiles for 30 minutes
- Invalidate on mutations

### 18.2 Pagination
- Implement cursor-based pagination
- Load more on scroll (infinite scroll)
- Batch API requests

### 18.3 Image Optimization
- Resize images on upload
- Use WebP format
- Progressive loading
- Caching with network fallback

---

## 19. Network Optimization

### 19.1 Bandwidth Reduction
- Compress API responses
- Minimize JSON payloads
- Use pagination limits

### 19.2 Connection Management
- Implement request queuing
- Exponential backoff for retries
- Circuit breaker pattern
- Timeout management (30s default)

---

## 20. Development Checklist

### Phase 1: Foundation
- [x] Analyze existing APIs
- [ ] Set up Flutter project structure
- [ ] Create network layer with Dio
- [ ] Implement authentication
- [ ] Build user management

### Phase 2: Core Features
- [ ] Freight creation workflow
- [ ] Driver matching and applications
- [ ] Contract generation
- [ ] Payment integration
- [ ] Real-time tracking

### Phase 3: Advanced Features
- [ ] AI price prediction
- [ ] AI driver recommendations
- [ ] Chat with AI assistant
- [ ] Multi-language support
- [ ] Push notifications

### Phase 4: Polish
- [ ] UI/UX refinement
- [ ] Performance optimization
- [ ] Security hardening
- [ ] Testing (unit/integration/E2E)
- [ ] Documentation

---

## Conclusion

This integration plan ensures the Flutter mobile app seamlessly consumes all backend APIs while maintaining security, performance, and user experience standards comparable to industry leaders (Uber Freight, Loadsmart, Convoy).

The architecture supports:
- ✅ All user roles (Shipper, Driver, Fleet Owner, Admin, Support)
- ✅ Real-time updates (WebSocket events)
- ✅ Offline-first design (local caching)
- ✅ AI-powered features (price prediction, driver matching)
- ✅ Escrow payments (Chapa, CBE Birr)
- ✅ Multi-language support (English, Amharic, Oromo, Tigrinya)
- ✅ Production-grade security

---

**Status**: ✅ Integration plan complete - Ready for implementation

**Next Step**: Execute Phase 1 - Set up Flutter project structure and implement network layer.
