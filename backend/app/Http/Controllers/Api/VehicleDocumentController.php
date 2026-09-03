<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\VehicleDocumentUploadRequest;
use App\Http\Resources\VehicleDocumentResource;
use App\Models\Vehicle;
use App\Models\VehicleDocument;
use App\Notifications\AllVehicleDocumentsApprovedNotification;
use App\Notifications\VehicleDocumentReviewedNotification;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;

class VehicleDocumentController extends Controller
{
    /**
     * GET /vehicles/{vehicle}/documents
     * Vehicle owner (driver or fleet owner) lists the vehicle's documents.
     */
    public function index($vehicleId)
    {
        $vehicle = Vehicle::findOrFail($vehicleId);
        $user = auth()->user();

        if ($vehicle->user_id !== $user->id && $vehicle->fleet_owner_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $docs = VehicleDocument::where('vehicle_id', $vehicleId)->get();
        return VehicleDocumentResource::collection($docs);
    }

    /**
     * POST /vehicles/{vehicle}/documents
     * Vehicle owner (driver or fleet owner) uploads or replaces a document (multipart/form-data).
     */
    public function upload($vehicleId, VehicleDocumentUploadRequest $request)
    {
        $vehicle = Vehicle::findOrFail($vehicleId);
        $user = auth()->user();

        if ($vehicle->user_id !== $user->id && $vehicle->fleet_owner_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        $type = $request->document_type;

        // Delete old file if replacing
        $existing = VehicleDocument::where('vehicle_id', $vehicleId)
            ->where('document_type', $type)
            ->first();

        if ($existing) {
            Storage::disk('private')->delete($existing->file_path);
        }

        $file     = $request->file('file');
        $path     = $file->store("vehicle-documents/{$vehicleId}", 'private');
        $origName = $file->getClientOriginalName();

        $doc = VehicleDocument::updateOrCreate(
            ['vehicle_id' => $vehicleId, 'document_type' => $type],
            [
                'file_path'        => $path,
                'original_name'    => $origName,
                'status'           => 'pending',
                'rejection_reason' => null,
                'reviewed_by'      => null,
                'reviewed_at'      => null,
            ]
        );

        return (new VehicleDocumentResource($doc))->response()->setStatusCode(201);
    }

    /**
     * GET /vehicle-documents/{document}/file
     * Serves the actual file — vehicle owner sees own docs, admin sees all.
     */
    public function download(VehicleDocument $document)
    {
        $user = auth()->user();
        $vehicle = $document->vehicle;

        if (!$user->is_admin && $vehicle->user_id !== $user->id && $vehicle->fleet_owner_id !== $user->id) {
            return response()->json(['message' => 'Forbidden'], 403);
        }

        if (!Storage::disk('private')->exists($document->file_path)) {
            return response()->json(['message' => 'File not found'], 404);
        }

        return Storage::disk('private')->download(
            $document->file_path,
            $document->original_name
        );
    }

    /**
     * GET /admin/vehicle-documents
     * Admin views all documents, optionally filtered by status.
     */
    public function adminIndex(Request $request)
    {
        $query = VehicleDocument::with('vehicle.user')
            ->latest();

        if ($request->query('status')) {
            $query->where('status', $request->query('status'));
        }

        return VehicleDocumentResource::collection($query->get());
    }

    /**
     * PATCH /admin/vehicle-documents/{document}/review
     * Admin approves or rejects a document.
     * Body: { action: 'approve'|'reject', rejection_reason?: string }
     */
    public function review(Request $request, VehicleDocument $document)
    {
        $request->validate([
            'action'           => 'required|in:approve,reject',
            'rejection_reason' => 'required_if:action,reject|nullable|string|max:500',
        ]);

        $status = $request->action === 'approve' ? 'approved' : 'rejected';

        $document->update([
            'status'           => $status,
            'rejection_reason' => $request->action === 'reject'
                ? $request->rejection_reason
                : null,
            'reviewed_by'  => auth()->id(),
            'reviewed_at'  => now(),
        ]);

        $vehicle = Vehicle::find($document->vehicle_id);
        $recipients = $this->notifiableOwners($vehicle);

        // Notify vehicle owner(s) of this document's review result
        foreach ($recipients as $recipient) {
            $recipient?->notify(new VehicleDocumentReviewedNotification($document->fresh()));
        }

        // If all required docs are approved → verify the vehicle and notify
        if ($status === 'approved') {
            $wasVerified = $this->maybeVerifyVehicle($document->vehicle_id);
            if ($wasVerified) {
                foreach ($recipients as $recipient) {
                    $recipient?->notify(new AllVehicleDocumentsApprovedNotification());
                }
            }
        }

        return response()->json([
            'success' => true,
            'message' => "Document {$status}.",
            'data'    => new VehicleDocumentResource($document->fresh()),
        ]);
    }

    /**
     * Resolve the vehicle's user and fleet owner (if set and different), for notification.
     */
    private function notifiableOwners(?Vehicle $vehicle): array
    {
        if (!$vehicle) {
            return [];
        }

        $recipients = [$vehicle->user];

        if ($vehicle->fleet_owner_id && $vehicle->fleet_owner_id !== $vehicle->user_id) {
            $recipients[] = $vehicle->fleetOwner;
        }

        return $recipients;
    }

    /**
     * Mark vehicle as verified once all 4 required document types are approved.
     * Returns true if the vehicle was just newly verified by this call.
     */
    private function maybeVerifyVehicle(int $vehicleId): bool
    {
        $required = ['registration_certificate', 'insurance_certificate', 'roadworthiness_certificate', 'ownership_document'];

        $approvedCount = VehicleDocument::where('vehicle_id', $vehicleId)
            ->where('status', 'approved')
            ->whereIn('document_type', $required)
            ->count();

        if ($approvedCount >= count($required)) {
            $vehicle = Vehicle::find($vehicleId);
            if ($vehicle && !$vehicle->verification_status) {
                $vehicle->update([
                    'verification_status' => true,
                ]);
                return true;
            }
        }

        return false;
    }
}
