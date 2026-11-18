package io.interviewcodi.backend.practice.repository;

import io.interviewcodi.backend.practice.domain.ExperienceQuestion;
import io.interviewcodi.backend.practice.domain.QuestionSourceKind;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionRepository {

    private final JdbcClient jdbcClient;

    QuestionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insertPreset(long practiceId, int orderNo, ExperienceQuestion preset) {
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
    public Optional<Long> findPracticeIdForOwner(long questionId, long userId) {
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
    public Optional<CurrentQuestion> findCurrent(long practiceId) {
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

    public List<QuestionRow> findByPractice(long practiceId) {
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

    public int nextOrderNo(long practiceId) {
        return jdbcClient.sql("select coalesce(max(order_no), 0) + 1 from questions where practice_id = :practiceId")
                .param("practiceId", practiceId)
                .query(Integer.class)
                .single();
    }

    public long insertFollowUp(long practiceId, int orderNo, String content, long sourceAnswerRevisionId,
                               long aiRequestId) {
        return jdbcClient.sql("""
                        insert into questions (practice_id, order_no, content, source_kind,
                                               source_answer_revision_id, ai_request_id)
                        values (:practiceId, :orderNo, :content, 'FOLLOW_UP', :sourceAnswerRevisionId, :aiRequestId)
                        returning id
                        """)
                .param("practiceId", practiceId)
                .param("orderNo", orderNo)
                .param("content", content)
                .param("sourceAnswerRevisionId", sourceAnswerRevisionId)
                .param("aiRequestId", aiRequestId)
                .query(Long.class)
                .single();
    }

    public List<String> findUsedPresetKeys(long practiceId) {
        return jdbcClient.sql("select preset_key from questions where practice_id = :practiceId and preset_key is not null")
                .param("practiceId", practiceId)
                .query(String.class)
                .list();
    }

    public List<String> findContents(long practiceId) {
        return jdbcClient.sql("""
                        select content from questions
                        where practice_id = :practiceId and deleted_at is null
                        order by order_no
                        """)
                .param("practiceId", practiceId)
                .query(String.class)
                .list();
    }

    public boolean hasAnswers(long questionId) {
        return jdbcClient.sql("select exists (select 1 from answers where question_id = :questionId and deleted_at is null)")
                .param("questionId", questionId)
                .query(Boolean.class)
                .single();
    }

    public void markSkipped(long questionId) {
        jdbcClient.sql("update questions set skipped_at = now(), updated_at = now() where id = :questionId")
                .param("questionId", questionId)
                .update();
    }

    public record CurrentQuestion(long id, boolean skipped) {
    }

    public record QuestionRow(long id, int orderNo, String content, QuestionSourceKind sourceKind, boolean skipped) {
    }
}
