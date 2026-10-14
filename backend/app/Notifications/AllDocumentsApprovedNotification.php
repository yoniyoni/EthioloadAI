<?php

namespace App\Notifications;

use Illuminate\Bus\Queueable;
use Illuminate\Notifications\Notification;

class AllDocumentsApprovedNotification extends Notification
{
    use Queueable;

    public function via($notifiable): array
    {
        return ['database'];
    }

    public function toDatabase($notifiable): array
    {
        return [
            'message' => 'All your documents have been approved. Your account is now verified — you can place bids and accept cargo.',
            'type'    => 'account_verified',
        ];
    }
}
