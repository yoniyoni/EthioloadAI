<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\DocumentUploadRequest;
use App\Http\Resources\DocumentResource;
use App\Models\DriverDocument;
use App\Models\User;
use App\Notifications\AllDocumentsApprovedNotification;
use App\Notifications\DocumentReviewedNotification;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Storage;

class DocumentController extends Controller
{
    /**
     * GET /driver/documents
     * Driver lists their own documents.
     */
    public function index()
    {
        $docs = DriverDocument::where('user_id', auth()->id())->get();
        return DocumentResource::collection($docs);
    }

    /**
     * POST /driver/documents
     * Driver uploads or replaces a document (multipart/form-data).
     */
    public function upload(DocumentUploadRequest $request)
    {
        $user = auth()->user();
        $type = $request->document_type;

        // Delete old file if replacing
        $existing = DriverDocument::where('user_id', $user->id)
            ->where('document_type', $type)
            ->first();

        if ($existing) {
            Storage::disk('private')->delete($existing->file_path);
        }

        $file     = $request->file('file');
        $path     = $file->store("driver-documents/{$user->id}", 'private');
        $origName = $file->getClientOriginalName();

        $doc = DriverDocument::updateOrCreate(
            ['user_id' => $user->id, 'document_type' => $type],
            [
                'file_path'        => $path,
                'original_name'    => $origName,
                'status'           => 'pending',
                'rejection_reason' => null,
                'reviewed_by'      => null,
                'reviewed_at'      => null,
            ]
        );

        return (new DocumentResource($doc))->response()->setStatusCode(201);
    }

    /**
     * GET /driver/documents/{document}/file
     * Serves the actual file — driver sees own docs, admin sees all.
     */
    public function download(DriverDocument $document)
    {
        $user = auth()->user();

        if (!$user->is_admin && $document->user_id !== $user->id) {
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
     * GET /admin/driver-documents
     * Admin views all documents, optionally filtered by status.
     */
    public function adminIndex(Request $request)
    {
        $query = DriverDocument::with('user')
            ->latest();

        if ($request->query('status')) {
            $query->where('status', $request->query('status'));
        }

        return DocumentResource::collection($query->get());
    }

    /**
     * PATCH /admin/driver-documents/{document}/review
     * Admin approves or rejects a document.
     * Body: { action: 'approve'|'reject', rejection_reason?: string }
     */
    public function review(Request $request, DriverDocument $document)
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

        $driver = User::find($document->user_id);

        // Notify driver of this document's review result
        $driver?->notify(new DocumentReviewedNotification($document->fresh()));

        // If all required docs are approved → verify the driver and notify
        if ($status === 'approved') {
            $wasVerified = $this->maybeVerifyDriver($document->user_id);
            if ($wasVerified) {
                $driver?->notify(new AllDocumentsApprovedNotification());
            }
        }

        return response()->json([
            'success' => true,
            'message' => "Document {$status}.",
            'data'    => new DocumentResource($document->fresh()),
        ]);
    }

    /**
     * Mark driver as verified once all 5 required document types are approved.
     * Returns true if the driver was just newly verified by this call.
     */
    private function maybeVerifyDriver(int $userId): bool
    {
        $required = ['license', 'national_id', 'vehicle_registration', 'insurance', 'tin'];

        $approvedCount = DriverDocument::where('user_id', $userId)
            ->where('status', 'approved')
            ->whereIn('document_type', $required)
            ->count();

        if ($approvedCount >= count($required)) {
            $user = User::find($userId);
            if ($user && !$user->verification_status) {
                $user->update([
                    'verification_status' => true,
                    'is_active'           => true,
                ]);
                return true;
            }
        }

        return false;
    }
}