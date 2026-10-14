package com.ethioloadai.notification.service;

import com.ethioloadai.notification.dto.NotificationItemDto;
import com.ethioloadai.notification.dto.NotificationListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationStoreServiceImpl implements NotificationStoreService {
    private static final Logger log = LoggerFactory.getLogger(NotificationStoreServiceImpl.class);
    private static final int MAX_NOTIFICATIONS_PER_USER = 50;

    private final Map<Long, List<NotificationItemDto>> userNotifications = new ConcurrentHashMap<>();

    @Override
    public NotificationListResponse getNotifications(Long userId) {
        List<NotificationItemDto> items = userNotifications.computeIfAbsent(userId, id -> createDefaultSeed(id));

        long unread = items.stream().filter(n -> n.getReadAt() == null).count();

        return NotificationListResponse.builder()
                .data(new ArrayList<>(items))
                .unreadCount((int) unread)
                .build();
    }

    @Override
    public boolean markAsRead(Long userId, String notificationId) {
        List<NotificationItemDto> items = userNotifications.get(userId);
        if (items == null) {
            return false;
        }

        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        for (NotificationItemDto item : items) {
            if (item.getId() != null && item.getId().equals(notificationId)) {
                item.setReadAt(now);
                log.debug("Marked notification {} as read for user {}", notificationId, userId);
                return true;
            }
        }
        return false;
    }

    @Override
    public void markAllAsRead(Long userId) {
        List<NotificationItemDto> items = userNotifications.get(userId);
        if (items != null) {
            String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            for (NotificationItemDto item : items) {
                if (item.getReadAt() == null) {
                    item.setReadAt(now);
                }
            }
            log.debug("Marked all notifications as read for user {}", userId);
        }
    }

    @Override
    public void recordNotification(Long userId, String type, Map<String, Object> data) {
        List<NotificationItemDto> list = userNotifications.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());

        String id = UUID.randomUUID().toString();
        NotificationItemDto item = NotificationItemDto.of(id, type, data != null ? data : Map.of(), LocalDateTime.now());

        // Insert at beginning (latest first)
        list.add(0, item);

        // Cap at 50
        while (list.size() > MAX_NOTIFICATIONS_PER_USER) {
            list.remove(list.size() - 1);
        }

        log.info("Recorded new notification {} for user {}: [{}]", id, userId, type);
    }

    private List<NotificationItemDto> createDefaultSeed(Long userId) {
        List<NotificationItemDto> list = new CopyOnWriteArrayList<>();
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        list.add(NotificationItemDto.builder()
                .id(UUID.randomUUID().toString())
                .type("SystemWelcomeNotification")
                .data(Map.of(
                        "message", "Welcome to EthioLoad logistics network. System operations active.",
                        "type", "system"
                ))
                .readAt(now)
                .createdAt(now)
                .build());

        return list;
    }
}
