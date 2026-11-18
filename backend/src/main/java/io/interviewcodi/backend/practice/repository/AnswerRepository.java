package io.interviewcodi.backend.practice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AnswerRepository {

    private final JdbcClient jdbcClient;

    AnswerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<Long> findIdByClientRequestId(long questionId, UUID clientRequestId) {
        return jdbcClient.sql("""
                        select id
                        from answers
                        where question_id = :questionId and client_request_id = :clientRequestId
                        """)
                .param("questionId", questionId)
                .param("clientRequestId", clientRequestId)
                .query(Long.class)
                .optional();
    }

    /** 다음 시도 번호. 호출하는 쪽이 연습 행을 잠그고 있어 같은 번호를 두 번 내지 않는다. */
    public int nextAttemptNo(long questionId) {
        return jdbcClient.sql("""
                        select coalesce(max(attempt_no), 0) + 1
                        from answers
                        where question_id = :questionId
                        """)
                .param("questionId", questionId)
                .query(Integer.class)
                .single();
    }

    public long insertAnswer(long questionId, int attemptNo, UUID clientRequestId) {
        return jdbcClient.sql("""
                        insert into answers (question_id, attempt_no, client_request_id)
                        values (:questionId, :attemptNo, :clientRequestId)
                        returning id
                        """)
                .param("questionId", questionId)
                .param("attemptNo", attemptNo)
                .param("clientRequestId", clientRequestId)
                .query(Long.class)
                .single();
    }

    public long insertRevision(long answerId, int revisionNo, String content) {
        return jdbcClient.sql("""
                        insert into answer_revisions (answer_id, revision_no, content)
                        values (:answerId, :revisionNo, :content)
                        returning id
                        """)
                .param("answerId", answerId)
                .param("revisionNo", revisionNo)
                .param("content", content)
                .query(Long.class)
                .single();
    }

    /** 답변 시도별 현재 버전(삭제되지 않은 가장 큰 revision_no). 연습의 삭제 확인은 호출하는 쪽이 한다. */
    public List<AttemptRow> findAttemptsWithCurrentRevision(long practiceId) {
        return jdbcClient.sql("""
                        select distinct on (a.id)
                               a.id, a.question_id, a.attempt_no,
                               r.id as revision_id, r.revision_no, r.content
                        from answers a
                        join questions q on q.id = a.question_id
                        join answer_revisions r on r.answer_id = a.id and r.deleted_at is null
                        where q.practice_id = :practiceId and q.deleted_at is null and a.deleted_at is null
                        order by a.id, r.revision_no desc
                        """)
                .param("practiceId", practiceId)
                .query(AttemptRow.class)
                .list();
    }

    public Optional<AttemptRow> findAttemptWithCurrentRevision(long answerId) {
        return jdbcClient.sql("""
                        select a.id, a.question_id, a.attempt_no,
                               r.id as revision_id, r.revision_no, r.content
                        from answers a
                        join answer_revisions r on r.answer_id = a.id and r.deleted_at is null
                        where a.id = :answerId and a.deleted_at is null
                        order by r.revision_no desc
                        limit 1
                        """)
                .param("answerId", answerId)
                .query(AttemptRow.class)
                .optional();
    }

    public record AttemptRow(long id, long questionId, int attemptNo, long revisionId, int revisionNo, String content) {
    }
}
