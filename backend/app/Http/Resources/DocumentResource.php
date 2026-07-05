<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;
use App\Models\DriverDocument;

class DocumentResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            'id'               => $this->id,
            'user_id'          => $this->user_id,
            'document_type'    => $this->document_type,
            'document_label'   => DriverDocument::labelFor($this->document_type),
            'original_name'    => $this->original_name,
            'status'           => $this->status,
            'rejection_reason' => $this->rejection_reason,
            'reviewed_by'      => $this->reviewer?->full_name,
            'reviewed_at'      => $this->reviewed_at,
            // Secure file URL — client hits GET /driver/documents/{id}/file
            'file_url'         => url("/api/driver/documents/{$this->id}/file"),
            'created_at'       => $this->created_at,
            'updated_at'       => $this->updated_at,
            // Include driver info for admin review view
            'driver_name'      => $this->whenLoaded('user', fn() => $this->user->full_name),
            'driver_phone'     => $this->whenLoaded('user', fn() => $this->user->phone),
        ];
    }
}