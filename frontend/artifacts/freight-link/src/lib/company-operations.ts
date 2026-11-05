import { ApiError } from "@/lib/api";

export type CompanyPage<T> = {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type CompanyBooking = {
  id: number;
  carrierCompanyId: number;
  freightId: number;
  freightDescription: string | null;
  shipperId: number | null;
  shipperName: string | null;
  driverId: number | null;
  driverName: string | null;
  vehicleId: number | null;
  vehiclePlateNumber: string | null;
  vehicleType: string | null;
  tripId: number | null;
  tripStatus: string | null;
  bookingStatus: string | null;
  estimatedPrice: number | null;
  commissionFee: number | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type CompanyTripStop = {
  id: number;
  tripId: number;
  locationName: string | null;
  pickupLatitude: number | null;
  pickupLongitude: number | null;
  stopOrder: number | null;
  stopStatus: string | null;
  agreedPrice: number | null;
  cargoInfo: {
    cargoRequestId: number | null;
    materialType: string | null;
    weightTons: number | null;
    shipperName: string | null;
  } | null;
  arrivalTime: string | null;
  loadingTime: string | null;
  completionTime: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type CompanyTrip = {
  id: number;
  carrierCompanyId: number;
  startLocation: string | null;
  destination: string | null;
  tripStatus: string | null;
  tripType: string | null;
  totalStops: number | null;
  completedStops: number | null;
  startTime: string | null;
  endTime: string | null;
  shipperDeliveryConfirmedBy: number | null;
  shipperDeliveryConfirmedAt: string | null;
  booking: {
    id: number | null;
    status: string | null;
    estimatedPrice: number | null;
    commissionFee: number | null;
  } | null;
  driver: { id: number | null; fullName: string | null } | null;
  shipper: { id: number | null; fullName: string | null } | null;
  vehicle: {
    id: number | null;
    plateNumber: string | null;
    truckType: string | null;
    loadCapacity: number | null;
  } | null;
  cargo: {
    id: number | null;
    materialType: string | null;
    serviceType: string | null;
    weightTons: number | null;
    volumeM3: number | null;
    urgencyLevel: string | null;
  } | null;
  routeInfo: {
    totalDistanceKm: number | null;
    completionPercentage: number | null;
    gpsPointCount: number | null;
    lastGpsUpdate: string | null;
    averageSpeed: number | null;
  } | null;
  stops: CompanyTripStop[] | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type CompanyDispatch = {
  id: number;
  freightId: number;
  carrierOfferId: number;
  carrierCompanyId: number;
  driverUserId: number;
  vehicleId: number;
  assignedBy: number;
  status: string;
  agreedAmount?: number | null;
  notes: string | null;
  rejectionReason: string | null;
  assignedAt: string;
  respondedAt: string | null;
  cancelledAt: string | null;
  bookingId: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type AwardedFreight = {
  freightId: number;
  carrierCompanyId: number;
  pickupLocation: string;
  destination: string;
  materialType: string;
  weightTons: number;
  agreedAmount?: number | null;
  carrierOfferId: number;
  activeDispatch: {
    dispatchId: number;
    status: string;
    driverUserId: number;
    vehicleId: number;
    assignedAt: string;
    bookingId: number | null;
  } | null;
  awardedAt: string;
};

export const bookingStatuses = [
  "PENDING",
  "ACCEPTED",
  "DELIVERED",
  "COMPLETED",
  "CONFIRMED",
  "CANCELLED",
] as const;

export const tripStatuses = [
  "ONGOING",
  "ARRIVED",
  "DELIVERY_PENDING_VERIFICATION",
  "COMPLETED",
] as const;

export const tripTypes = ["SINGLE", "MULTI_STOP"] as const;
export const tripSortFields = ["id", "startTime", "tripStatus"] as const;

export function formatCompanyDate(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

export function formatCompanyMoney(value: number | null | undefined): string | null {
  return value == null || !Number.isFinite(value)
    ? null
    : `ETB ${value.toLocaleString()}`;
}

export function companyRequestError(error: unknown): string {
  const status = error instanceof ApiError ? error.status : undefined;
  if (status === 400) return "The selected filters are invalid. Review them and try again.";
  if (status === 401) return "Your session has expired. Sign in again to continue.";
  if (status === 403) return "Your active company role does not allow access to this information.";
  if (status === 404) return "This company record is unavailable or outside the visible history period.";
  if (status === 0) return "Unable to connect to the server. Check your connection and retry.";
  return error instanceof Error ? error.message : "Unable to load company records.";
}
