package com.contextcraft.portal.repository;

import com.contextcraft.portal.entity.ConversationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationStateRepository extends JpaRepository<ConversationState, String> {
}
