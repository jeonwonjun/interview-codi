package io.interviewcodi.backend.practice.repository;

import io.interviewcodi.backend.practice.domain.AiRequestKind;
import io.interviewcodi.backend.practice.domain.AiRequestStatus;
import io.interviewcodi.backend.practice.domain.DiscardReason;
import java.util.List;
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

    public record RequestRow(long id, long answerRevisionId, AiRequestStatus status) {
    }
}
