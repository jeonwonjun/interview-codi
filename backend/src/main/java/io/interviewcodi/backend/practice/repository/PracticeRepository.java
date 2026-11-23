package io.interviewcodi.backend.practice.repository;

import io.interviewcodi.backend.practice.domain.PracticeStatus;
import io.interviewcodi.backend.practice.domain.StartMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PracticeRepository {

    private final JdbcClient jdbcClient;

    PracticeRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insert(long userId, StartMode startMode) {
        return jdbcClient.sql("""
                        insert into practices (user_id, start_mode)
                        values (:userId, :startMode)
                        returning id
                        """)
                .param("userId", userId)
                .param("startMode", startMode.name())
                .query(Long.class)
                .single();
    }

    /**
     * 본인의 삭제되지 않은 연습 행을 잠근다. 진행을 바꾸는 쓰기는 모두 이 잠금을 먼저 잡는다(잠금 순서의 시작).
     * 같은 연습의 답변 제출·마치기·삭제와 워커의 결과 반영이 이 잠금으로 한 줄로 선다.
     */
    public Optional<PracticeStatus> lockForOwner(long practiceId, long userId) {
        return jdbcClient.sql("""
                        select status
                        from practices
                        where id = :practiceId and user_id = :userId and deleted_at is null
                        for update
                        """)
                .param("practiceId", practiceId)
                .param("userId", userId)
                .query(PracticeStatus.class)
                .optional();
    }

    public Optional<PracticeRow> findForOwner(long practiceId, long userId) {
        return jdbcClient.sql("""
                        select id, start_mode, status, created_at, completed_at
                        from practices
                        where id = :practiceId and user_id = :userId and deleted_at is null
                        """)
                .param("practiceId", practiceId)
                .param("userId", userId)
                .query(PracticeRow.class)
                .optional();
    }

    public List<PracticeSummaryRow> findSummaries(long userId) {
        return jdbcClient.sql("""
                        select p.id, p.start_mode, p.status, p.created_at, p.completed_at,
                               (select count(*) from questions q
                                where q.practice_id = p.id and q.deleted_at is null) as question_count,
                               (select count(*) from answers a
                                join questions q on q.id = a.question_id
                                where q.practice_id = p.id and q.deleted_at is null and a.deleted_at is null)
                                       as answer_count,
                               (select max(a.created_at) from answers a
                                join questions q on q.id = a.question_id
                                where q.practice_id = p.id and q.deleted_at is null and a.deleted_at is null)
                                       as last_answered_at
                        from practices p
                        where p.user_id = :userId and p.deleted_at is null
                        order by p.created_at desc, p.id desc
                        """)
                .param("userId", userId)
                .query(PracticeSummaryRow.class)
                .list();
    }

    public void complete(long practiceId) {
        jdbcClient.sql("""
                        update practices
                        set status = 'COMPLETED', completed_at = now(), updated_at = now()
                        where id = :practiceId and status = 'IN_PROGRESS'
                        """)
                .param("practiceId", practiceId)
                .update();
    }

    public void softDelete(long practiceId) {
        jdbcClient.sql("""
                        update practices
                        set deleted_at = now(), updated_at = now()
                        where id = :practiceId and deleted_at is null
                        """)
                .param("practiceId", practiceId)
                .update();
    }

    /** 워커가 결과를 반영할 때 연습 행을 잠근다. 사용자 요청과 같은 잠금 순서(연습 → 요청)를 지킨다. */
    public Optional<LockedPractice> lockById(long practiceId) {
        return jdbcClient.sql("""
                        select status, deleted_at is not null as deleted
                        from practices
                        where id = :practiceId
                        for update
                        """)
                .param("practiceId", practiceId)
                .query(LockedPractice.class)
                .optional();
    }

    public record PracticeRow(long id, StartMode startMode, PracticeStatus status, OffsetDateTime createdAt,
                       OffsetDateTime completedAt) {
    }

    public record PracticeSummaryRow(long id, StartMode startMode, PracticeStatus status, OffsetDateTime createdAt,
                              OffsetDateTime completedAt, int questionCount, int answerCount,
                              OffsetDateTime lastAnsweredAt) {
    }

    public record LockedPractice(PracticeStatus status, boolean deleted) {
    }
}
