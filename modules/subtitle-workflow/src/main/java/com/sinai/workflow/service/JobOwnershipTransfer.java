package com.sinai.workflow.service;

import com.sinai.identity.api.UserDeletionEvent;
import com.sinai.workflow.repository.SubtitleJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobOwnershipTransfer {

    private final SubtitleJobRepository jobRepository;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUserDeletion(UserDeletionEvent event) {
        var transferred = jobRepository.transferOwnership(event.deletedUserId().toString(),
                event.successorId().toString());
        log.info("Transferred {} jobs of deleted user {} to {}", transferred, event.deletedUserId(),
                event.successorId());
    }
}
