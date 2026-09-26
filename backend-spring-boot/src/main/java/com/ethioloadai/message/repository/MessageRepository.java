package com.ethioloadai.message.repository;

import com.ethioloadai.message.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Retrieve messages for a given freight, newest first.
     * Matches Freight-Link UI expectation which reverses the list for bottom-up display.
     */
    List<Message> findByFreightIdOrderByCreatedAtDesc(Long freightId);

    /**
     * Retrieve unread messages for a specific receiver within a freight.
     */
    List<Message> findByFreightIdAndReceiverIdAndIsReadFalse(Long freightId, Long receiverId);

    /**
     * Mark all unread messages for a recipient in a freight conversation as read.
     */
    @Modifying
    @Query("UPDATE Message m SET m.isRead = true, m.updatedAt = CURRENT_TIMESTAMP WHERE m.freight.id = :freightId AND m.receiver.id = :receiverId AND m.isRead = false")
    int markAsReadByFreightIdAndReceiverId(@Param("freightId") Long freightId, @Param("receiverId") Long receiverId);
}
