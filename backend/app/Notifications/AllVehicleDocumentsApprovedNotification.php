<?php

namespace App\Notifications;

use Illuminate\Bus\Queueable;
use Illuminate\Notifications\Notification;

class AllVehicleDocumentsApprovedNotification extends Notification
{
    use Queueable;

    public function via($notifiable): array
    {
        return ['database'];
    }

    public function toDatabase($notifiable): array
    {
        return [
            'message' => 'All required documents for your vehicle have been approved. It is now verified and can be assigned to trips.',
            'type'    => 'vehicle_verified',
        ];
    }
}
