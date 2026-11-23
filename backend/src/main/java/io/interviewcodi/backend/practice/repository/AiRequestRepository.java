package io.interviewcodi.backend.practice.repository;

import io.interviewcodi.backend.practice.domain.AiRequestKind;
import io.interviewcodi.backend.practice.domain.AiRequestStatus;
import io.interviewcodi.backend.practice.domain.DiscardReason;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AiRequestRepository {

    private final JdbcClient jdbcClient;

    AiRequestRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 워커가 꺼내 갈 요청을 쌓는다. 답변·버전 저장과 같은 트랜잭션에서 호출한다. */
    public long insertPending(long practiceId, AiRequestKind kind, long answerRevisionId) {
        return jdbcClient.sql("""
                        insert into ai_requests (practice_id, kind, answer_revision_id)
                        values (:practiceId, :kind, :answerRevisionId)
                        returning id
                        """)
                .param("practiceId", practiceId)
                .param("kind", kind.name())
                .param("answerRevisionId", answerRevisionId)
                .query(Long.class)
                .single();
    }

    /** 녹음을 글로 옮기는 요청을 쌓는다. 시도·녹음 저장과 같은 트랜잭션에서 호출한다. */
    public long insertTranscribePending(long practiceId, long recordingId) {
        return jdbcClient.sql("""
                        insert into ai_requests (practice_id, kind, recording_id)
                        values (:practiceId, 'TRANSCRIBE', :recordingId)
                        returning id
                        """)
                .param("practiceId", practiceId)
                .param("recordingId", recordingId)
                .query(Long.class)
                .single();
    }

    /** 녹음별 가장 최근 인식 요청. */
    public List<TranscribeRow> findLatestTranscribeRequests(long practiceId) {
        return jdbcClient.sql("""
                        select distinct on (recording_id) id, recording_id, status
                        from ai_requests
                        where practice_id = :practiceId and kind = 'TRANSCRIBE'
                        order by recording_id, id desc
                        """)
                .param("practiceId", practiceId)
                .query(TranscribeRow.class)
                .list();
    }

    /** 이 녹음의 가장 최근 인식 요청. */
    public Optional<TranscribeRow> findLatestTranscribeRequest(long recordingId) {
        return jdbcClient.sql("""
                        select id, recording_id, status
                        from ai_requests
                        where recording_id = :recordingId and kind = 'TRANSCRIBE'
                        order by id desc
                        limit 1
                        """)
                .param("recordingId", recordingId)
                .query(TranscribeRow.class)
                .optional();
    }

    /** 이 질문의 시도 중 아직 글로 옮기는 중(PENDING·RUNNING)인 음성 시도가 있는지. */
    public boolean hasActiveTranscription(long questionId) {
        return jdbcClient.sql("""
                        select exists (
                            select 1
                            from ai_requests r
                            join recordings rec on rec.id = r.recording_id
                            join answers a on a.id = rec.answer_id
                            where a.question_id = :questionId and r.kind = 'TRANSCRIBE'
                              and r.status in ('PENDING', 'RUNNING'))
                        """)
                .param("questionId", questionId)
                .query(Boolean.class)
                .single();
    }

    /** 아직 꺼내지 않은 요청을 버린다. 처리 중(RUNNING)인 요청은 워커가 결과를 반영할 때 버린다. */
    public int discardPending(long practiceId, AiRequestKind kind, DiscardReason reason) {
        return jdbcClient.sql("""
                        update ai_requests
                        set status = 'DISCARDED', discard_reason = :reason, finished_at = now(), updated_at = now()
                        where practice_id = :practiceId and kind = :kind and status = 'PENDING'
                        """)
                .param("practiceId", practiceId)
                .param("kind", kind.name())
                .param("reason", reason.name())
                .update();
    }

    public int discardAllPending(long practiceId, DiscardReason reason) {
        return jdbcClient.sql("""
                        update ai_requests
                        set status = 'DISCARDED', discard_reason = :reason, finished_at = now(), updated_at = now()
                        where practice_id = :practiceId and status = 'PENDING'
                        """)
                .param("practiceId", practiceId)
                .param("reason", reason.name())
                .update();
    }

    /**
     * 교정으로 현재 버전이 바뀐 답변 시도에서, 이전 버전을 근거로 아직 꺼내지 않은 요청을 버린다.
     * 처리 중(RUNNING)인 요청은 워커가 결과를 반영할 때 현재 버전이 아님을 확인하고 버린다.
     */
    public int discardPendingForOlderRevisions(long answerId, long currentRevisionId) {
        return jdbcClient.sql("""
                        update ai_requests
                        set status = 'DISCARDED', discard_reason = 'SUPERSEDED', finished_at = now(), updated_at = now()
                        where status = 'PENDING'
                          and answer_revision_id in (
                              select id from answer_revisions where answer_id = :answerId and id <> :currentRevisionId)
                        """)
                .param("answerId", answerId)
                .param("currentRevisionId", currentRevisionId)
                .update();
    }

    /** 답변 버전별 가장 최근 피드백 요청. */
    public List<RequestRow> findLatestFeedbackRequests(long practiceId) {
        return jdbcClient.sql("""
                        select distinct on (answer_revision_id) id, answer_revision_id, status
                        from ai_requests
                        where practice_id = :practiceId and kind = 'FEEDBACK'
                        order by answer_revision_id, id desc
                        """)
                .param("practiceId", practiceId)
                .query(RequestRow.class)
                .list();
    }

    /**
     * 처리할 요청 하나를 꺼내 임대를 건다. {@code SKIP LOCKED}로 다른 워커가 잠근 행은 기다리지 않고 건너뛴다.
     * 임대가 끝난 RUNNING(처리하던 워커가 죽은 경우)도 다시 꺼낸다.
     */
    public Optional<ClaimedRequest> claimNext(UUID leaseToken, Duration lease) {
        return claimNext(leaseToken, lease, EnumSet.allOf(AiRequestKind.class));
    }

    /** {@code kinds}에 든 종류만 꺼낸다. 인식 전용 슬롯처럼 종류별로 처리 몫을 떼어 둘 때 쓴다. */
    public Optional<ClaimedRequest> claimNext(UUID leaseToken, Duration lease, Set<AiRequestKind> kinds) {
        return jdbcClient.sql("""
                        update ai_requests
                        set status = 'RUNNING', attempt_count = attempt_count + 1, lease_token = :leaseToken,
                            lease_until = now() + make_interval(secs => :leaseSeconds),
                            first_claimed_at = coalesce(first_claimed_at, now()), updated_at = now()
                        where id = (
                            select id
                            from ai_requests
                            where kind in (:kinds)
                              and ((status = 'PENDING' and available_at <= now())
                                or (status = 'RUNNING' and lease_until < now()))
                            order by id
                            limit 1
                            for update skip locked)
                        returning id, practice_id, kind, answer_revision_id, recording_id, attempt_count, lease_token
                        """)
                .param("leaseToken", leaseToken)
                .param("leaseSeconds", lease.toSeconds())
                .param("kinds", kinds.stream().map(Enum::name).toList())
                .query(ClaimedRequest.class)
                .optional();
    }

    /**
     * 결과를 반영하기 전에 요청 행을 잠그고, 내가 꺼낸 그대로인지 확인한다.
     * 임대가 끝나 다른 워커가 다시 꺼냈다면 false. 그 결과는 반영하지 않는다.
     */
    public boolean lockIfStillLeased(long requestId, UUID leaseToken) {
        return jdbcClient.sql("""
                        select id
                        from ai_requests
                        where id = :id and status = 'RUNNING' and lease_token = :leaseToken
                        for update
                        """)
                .param("id", requestId)
                .param("leaseToken", leaseToken)
                .query(Long.class)
                .optional()
                .isPresent();
    }

    public void markSucceeded(long requestId) {
        finish(requestId, "SUCCEEDED", null, null);
    }

    public void markDiscarded(long requestId, DiscardReason reason) {
        finish(requestId, "DISCARDED", reason.name(), null);
    }

    public void markFailed(long requestId, String error) {
        finish(requestId, "FAILED", null, error);
    }

    /** 일시적 실패. 임대를 풀고 {@code delay} 뒤에 다시 꺼내도록 PENDING으로 되돌린다. */
    public void retryLater(long requestId, Duration delay, String error) {
        jdbcClient.sql("""
                        update ai_requests
                        set status = 'PENDING', lease_token = null, lease_until = null, last_error = :error,
                            available_at = now() + make_interval(secs => :delaySeconds), updated_at = now()
                        where id = :id
                        """)
                .param("id", requestId)
                .param("error", error)
                .param("delaySeconds", delay.toSeconds())
                .update();
    }

    private void finish(long requestId, String status, String discardReason, String error) {
        jdbcClient.sql("""
                        update ai_requests
                        set status = :status, discard_reason = :discardReason, last_error = coalesce(:error, last_error),
                            lease_token = null, lease_until = null, finished_at = now(), updated_at = now()
                        where id = :id
                        """)
                .param("id", requestId)
                .param("status", status)
                .param("discardReason", discardReason)
                .param("error", error)
                .update();
    }

    /** 이 버전·종류의 처리 중(PENDING·RUNNING) 요청. 부분 유일 인덱스 때문에 최대 하나다. */
    public Optional<RequestRow> findActive(long answerRevisionId, AiRequestKind kind) {
        return jdbcClient.sql("""
                        select id, answer_revision_id, status
                        from ai_requests
                        where answer_revision_id = :answerRevisionId and kind = :kind
                          and status in ('PENDING', 'RUNNING')
                        """)
                .param("answerRevisionId", answerRevisionId)
                .param("kind", kind.name())
                .query(RequestRow.class)
                .optional();
    }

    /** 이 연습에서 처리 중인 가장 최근 꼬리질문 요청. 재접속한 화면이 폴링을 이어가는 데 쓴다. */
    public Optional<RequestRow> findActiveFollowUp(long practiceId) {
        return jdbcClient.sql("""
                        select id, answer_revision_id, status
                        from ai_requests
                        where practice_id = :practiceId and kind = 'FOLLOW_UP_QUESTION'
                          and status in ('PENDING', 'RUNNING')
                        order by id desc
                        limit 1
                        """)
                .param("practiceId", practiceId)
                .query(RequestRow.class)
                .optional();
    }

    /** 이 버전의 가장 최근 피드백 요청. */
    public Optional<RequestRow> findLatestFeedbackRequest(long answerRevisionId) {
        return jdbcClient.sql("""
                        select id, answer_revision_id, status
                        from ai_requests
                        where answer_revision_id = :answerRevisionId and kind = 'FEEDBACK'
                        order by id desc
                        limit 1
                        """)
                .param("answerRevisionId", answerRevisionId)
                .query(RequestRow.class)
                .optional();
    }

    /** 본인의 삭제되지 않은 연습에 속한 요청과 그 결과 식별값. */
    public Optional<RequestDetailRow> findDetailForOwner(long requestId, long userId) {
        return jdbcClient.sql("""
                        select r.id, r.kind, r.status, r.discard_reason, r.answer_revision_id, r.recording_id,
                               f.id as feedback_id, q.id as question_id
                        from ai_requests r
                        join practices p on p.id = r.practice_id
                        left join feedback f on f.ai_request_id = r.id and f.deleted_at is null
                        left join questions q on q.ai_request_id = r.id and q.deleted_at is null
                        where r.id = :id and p.user_id = :userId and p.deleted_at is null
                        """)
                .param("id", requestId)
                .param("userId", userId)
                .query(RequestDetailRow.class)
                .optional();
    }

    public record RequestRow(long id, long answerRevisionId, AiRequestStatus status) {
    }

    /** TRANSCRIBE는 {@code recordingId}만, 나머지 종류는 {@code answerRevisionId}만 있다(DB 검사 제약). */
    public record ClaimedRequest(long id, long practiceId, AiRequestKind kind, Long answerRevisionId, Long recordingId,
                                 int attemptCount, UUID leaseToken) {
    }

    /** 인식 요청(TRANSCRIBE)은 {@code answerRevisionId}가 null이고 {@code recordingId}가 있다. */
    public record RequestDetailRow(long id, AiRequestKind kind, AiRequestStatus status, DiscardReason discardReason,
                                   Long answerRevisionId, Long recordingId, Long feedbackId, Long questionId) {
    }

    public record TranscribeRow(long id, long recordingId, AiRequestStatus status) {
    }
}
