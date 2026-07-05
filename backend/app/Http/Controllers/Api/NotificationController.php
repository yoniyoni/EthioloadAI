<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class NotificationController extends Controller
{
    /** GET /notifications — returns latest 50 notifications for the auth user */
    public function index(Request $request)
    {
        $notifications = $request->user()
            ->notifications()
            ->latest()
            ->limit(50)
            ->get()
            ->map(fn ($n) => [
                'id'         => $n->id,
                'type'       => class_basename($n->type),
                'data'       => $n->data,
                'read_at'    => $n->read_at?->toIso8601String(),
                'created_at' => $n->created_at->toIso8601String(),
            ]);

        $unreadCount = $request->user()->unreadNotifications()->count();

        return response()->json([
            'data'         => $notifications,
            'unread_count' => $unreadCount,
        ]);
    }

    /** PATCH /notifications/{id}/read — mark a single notification as read */
    public function markRead(Request $request, string $id)
    {
        $notification = $request->user()->notifications()->find($id);

        if (!$notification) {
            return response()->json(['message' => 'Not found'], 404);
        }

        $notification->markAsRead();

        return response()->json(['success' => true]);
    }

    /** PATCH /notifications/read-all — mark all unread as read */
    public function markAllRead(Request $request)
    {
        $request->user()->unreadNotifications->markAsRead();

        return response()->json(['success' => true]);
    }
}
