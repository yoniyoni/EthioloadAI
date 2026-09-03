<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;
use App\Models\VehicleDocument;

class VehicleDocumentResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            'id'               => $this->id,
            'vehicle_id'       => $this->vehicle_id,
            'document_type'    => $this->document_type,
            'document_label'   => VehicleDocument::labelFor($this->document_type),
            'original_name'    => $this->original_name,
            'status'           => $this->status,
            'rejection_reason' => $this->rejection_reason,
            'reviewed_by'      => $this->reviewer?->full_name,
            'reviewed_at'      => $this->reviewed_at,
            // Secure file URL — client hits GET /vehicle-documents/{id}/file
            'file_url'         => url("/api/vehicle-documents/{$this->id}/file"),
            'created_at'       => $this->created_at,
            'updated_at'       => $this->updated_at,
            // Include vehicle info for admin review view
            'plate_number'     => $this->whenLoaded('vehicle', fn() => $this->vehicle->plate_number),
            'truck_type'       => $this->whenLoaded('vehicle', fn() => $this->vehicle->truck_type),
        ];
    }
}
