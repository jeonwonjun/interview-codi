package io.interviewcodi.backend.practice.service;

import io.interviewcodi.backend.common.error.BusinessException;
import io.interviewcodi.backend.common.error.ErrorCode;
import io.interviewcodi.backend.practice.dto.AiRequestStatusResponse;
import io.interviewcodi.backend.practice.repository.AiRequestRepository;
import io.interviewcodi.backend.practice.repository.AiRequestRepository.RequestDetailRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiRequestService {

    private final AiRequestRepository aiRequestRepository;

    AiRequestService(AiRequestRepository aiRequestRepository) {
        this.aiRequestRepository = aiRequestRepository;
    }

    @Transactional(readOnly = true)
    public AiRequestStatusResponse get(long userId, long requestId) {
        RequestDetailRow row = aiRequestRepository.findDetailForOwner(requestId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_REQUEST_NOT_FOUND));
        return new AiRequestStatusResponse(row.id(), row.kind().name(), row.status().name(),
                row.discardReason() == null ? null : row.discardReason().name(), row.answerRevisionId(),
                row.recordingId(), row.feedbackId(), row.questionId());
    }
}
