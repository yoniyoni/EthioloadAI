<?php

namespace App\Notifications;

use App\Models\DriverDocument;
use Illuminate\Bus\Queueable;
use Illuminate\Notifications\Notification;

class DocumentReviewedNotification extends Notification
{
    use Queueable;

    public function __construct(protected DriverDocument $document) {}

    public function via($notifiable): array
    {
        return ['database'];
    }

    public function toDatabase($notifiable): array
    {
        $isApproved = $this->document->status === 'approved';
        $label = str_replace('_', ' ', ucfirst($this->document->document_type));

        return [
            'document_id'   => $this->document->id,
            'document_type' => $this->document->document_type,
            'status'        => $this->document->status,
            'message'       => $isApproved
                ? "Your {$label} has been approved."
                : "Your {$label} was rejected. Reason: {$this->document->rejection_reason}",
            'type'          => 'document_reviewed',
        ];
    }
}
