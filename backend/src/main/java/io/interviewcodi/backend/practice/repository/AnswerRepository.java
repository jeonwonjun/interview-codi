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

    /** 삭제되지 않았고, 같은 시도에 더 큰 버전이 없으면 현재 버전이다. */
    public boolean isCurrentRevision(long revisionId) {
        return jdbcClient.sql("""
                        select count(*)
                        from answer_revisions r
                        join answers a on a.id = r.answer_id and a.deleted_at is null
                        where r.id = :revisionId and r.deleted_at is null
                          and not exists (
                              select 1 from answer_revisions newer
                              where newer.answer_id = r.answer_id and newer.revision_no > r.revision_no
                                and newer.deleted_at is null)
                        """)
                .param("revisionId", revisionId)
                .query(Integer.class)
                .single() == 1;
    }

    /** 본인의 삭제되지 않은 연습에 속한 버전이면 그 연습 ID. */
    public Optional<Long> findPracticeIdForOwner(long revisionId, long userId) {
        return jdbcClient.sql("""
                        select p.id
                        from answer_revisions r
                        join answers a on a.id = r.answer_id and a.deleted_at is null
                        join questions q on q.id = a.question_id and q.deleted_at is null
                        join practices p on p.id = q.practice_id
                        where r.id = :revisionId and r.deleted_at is null
                          and p.user_id = :userId and p.deleted_at is null
                        """)
                .param("revisionId", revisionId)
                .param("userId", userId)
                .query(Long.class)
                .optional();
    }

    /**
     * 피드백 생성 입력: 질문 문구, 이 버전의 본문, 그리고 재답변이면 직전 시도의 현재 버전 피드백이 제안한 개선점.
     */
    public FeedbackSource findFeedbackSource(long revisionId) {
        return jdbcClient.sql("""
                        select q.content as question, r.content as answer,
                               (select f.suggestion
                                from answers prev
                                join lateral (
                                    select pr.id from answer_revisions pr
                                    where pr.answer_id = prev.id and pr.deleted_at is null
                                    order by pr.revision_no desc limit 1) prev_rev on true
                                join feedback f on f.answer_revision_id = prev_rev.id and f.deleted_at is null
                                where prev.question_id = a.question_id and prev.attempt_no = a.attempt_no - 1
                                  and prev.deleted_at is null) as previous_suggestion
                        from answer_revisions r
                        join answers a on a.id = r.answer_id
                        join questions q on q.id = a.question_id
                        where r.id = :revisionId
                        """)
                .param("revisionId", revisionId)
                .query(FeedbackSource.class)
                .single();
    }

    /** 본인의 삭제되지 않은 연습에 속한 답변 시도면 그 연습 ID. */
    public Optional<Long> findPracticeIdForOwnerByAnswer(long answerId, long userId) {
        return jdbcClient.sql("""
                        select p.id
                        from answers a
                        join questions q on q.id = a.question_id and q.deleted_at is null
                        join practices p on p.id = q.practice_id
                        where a.id = :answerId and a.deleted_at is null
                          and p.user_id = :userId and p.deleted_at is null
                        """)
                .param("answerId", answerId)
                .param("userId", userId)
                .query(Long.class)
                .optional();
    }

    public record AttemptRow(long id, long questionId, int attemptNo, long revisionId, int revisionNo, String content) {
    }

    public record FeedbackSource(String question, String answer, String previousSuggestion) {
    }
}
