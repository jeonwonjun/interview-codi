package io.interviewcodi.backend.practice;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class QuestionRepository {

    private final JdbcClient jdbcClient;

    QuestionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    long insertPreset(long practiceId, int orderNo, ExperienceQuestion preset) {
        return jdbcClient.sql("""
                        insert into questions (practice_id, order_no, content, source_kind, preset_key)
                        values (:practiceId, :orderNo, :content, 'EXPERIENCE_PRESET', :presetKey)
                        returning id
                        """)
                .param("practiceId", practiceId)
                .param("orderNo", orderNo)
                .param("content", preset.content())
                .param("presetKey", preset.key())
                .query(Long.class)
                .single();
    }

    /** 본인의 삭제되지 않은 연습에 속한 질문이면 그 연습 ID. */
    Optional<Long> findPracticeIdForOwner(long questionId, long userId) {
        return jdbcClient.sql("""
                        select q.practice_id
                        from questions q
                        join practices p on p.id = q.practice_id
                        where q.id = :questionId and q.deleted_at is null
                          and p.user_id = :userId and p.deleted_at is null
                        """)
                .param("questionId", questionId)
                .param("userId", userId)
                .query(Long.class)
                .optional();
    }

    /** 현재 질문 = 삭제되지 않은 가장 큰 order_no. */
    Optional<CurrentQuestion> findCurrent(long practiceId) {
        return jdbcClient.sql("""
                        select id, skipped_at is not null as skipped
                        from questions
                        where practice_id = :practiceId and deleted_at is null
                        order by order_no desc
                        limit 1
                        """)
                .param("practiceId", practiceId)
                .query(CurrentQuestion.class)
                .optional();
    }

    List<QuestionRow> findByPractice(long practiceId) {
        return jdbcClient.sql("""
                        select id, order_no, content, source_kind, skipped_at is not null as skipped
                        from questions
                        where practice_id = :practiceId and deleted_at is null
                        order by order_no
                        """)
                .param("practiceId", practiceId)
                .query(QuestionRow.class)
                .list();
    }

    record CurrentQuestion(long id, boolean skipped) {
    }

    record QuestionRow(long id, int orderNo, String content, QuestionSourceKind sourceKind, boolean skipped) {
    }
}
