package com.ethioloadai.notification;

import com.ethioloadai.notification.controller.NotificationController;
import com.ethioloadai.notification.dto.NotificationItemDto;
import com.ethioloadai.notification.dto.NotificationListResponse;
import com.ethioloadai.notification.service.NotificationStoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationControllerTest {

    @Test
    @DisplayName("GET /api/notifications returns 200 with notifications list and unread count")
    void testGetNotifications() {
        NotificationStoreService service = mock(NotificationStoreService.class);
        NotificationController controller = new NotificationController(service);

        NotificationListResponse mockResp = NotificationListResponse.builder()
                .data(List.of(NotificationItemDto.builder().id("n1").type("Info").build()))
                .unreadCount(1)
                .build();

        when(service.getNotifications(anyLong())).thenReturn(mockResp);

        ResponseEntity<NotificationListResponse> response = controller.getNotifications(null);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getUnreadCount());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    @DisplayName("PATCH /api/notifications/{id}/read returns 200 on success and 404 when missing")
    void testMarkRead() {
        NotificationStoreService service = mock(NotificationStoreService.class);
        NotificationController controller = new NotificationController(service);

        when(service.markAsRead(anyLong(), eq("valid-id"))).thenReturn(true);
        when(service.markAsRead(anyLong(), eq("missing-id"))).thenReturn(false);

        ResponseEntity<Map<String, Object>> resOk = controller.markRead("valid-id", null);
        assertEquals(200, resOk.getStatusCode().value());
        assertTrue((Boolean) resOk.getBody().get("success"));

        ResponseEntity<Map<String, Object>> res404 = controller.markRead("missing-id", null);
        assertEquals(404, res404.getStatusCode().value());
        assertFalse((Boolean) res404.getBody().get("success"));
    }

    @Test
    @DisplayName("PATCH /api/notifications/read-all returns 200 and calls service")
    void testMarkAllRead() {
        NotificationStoreService service = mock(NotificationStoreService.class);
        NotificationController controller = new NotificationController(service);

        ResponseEntity<Map<String, Object>> response = controller.markAllRead(null);
        assertEquals(200, response.getStatusCode().value());
        assertTrue((Boolean) response.getBody().get("success"));

        verify(service, times(1)).markAllAsRead(anyLong());
    }
}
