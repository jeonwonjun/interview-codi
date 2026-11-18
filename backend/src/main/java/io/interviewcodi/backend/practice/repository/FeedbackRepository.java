package io.interviewcodi.backend.practice.repository;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackRepository {

    private final JdbcClient jdbcClient;

    FeedbackRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<FeedbackRow> findByPractice(long practiceId) {
        return jdbcClient.sql("""
                        select f.id, f.answer_revision_id, f.observation, f.suggestion, f.suggestion_focus,
                               f.previous_review
                        from feedback f
                        join ai_requests r on r.id = f.ai_request_id
                        where r.practice_id = :practiceId and f.deleted_at is null
                        """)
                .param("practiceId", practiceId)
                .query(FeedbackRow.class)
                .list();
    }

    public long insert(long aiRequestId, long answerRevisionId, String observation, String suggestion,
                       String suggestionFocus, String previousReview, String generator) {
        return jdbcClient.sql("""
                        insert into feedback (ai_request_id, answer_revision_id, observation, suggestion,
                                              suggestion_focus, previous_review, generator)
                        values (:aiRequestId, :answerRevisionId, :observation, :suggestion,
                                :suggestionFocus, :previousReview, :generator)
                        returning id
                        """)
                .param("aiRequestId", aiRequestId)
                .param("answerRevisionId", answerRevisionId)
                .param("observation", observation)
                .param("suggestion", suggestion)
                .param("suggestionFocus", suggestionFocus)
                .param("previousReview", previousReview)
                .param("generator", generator)
                .query(Long.class)
                .single();
    }

    public record FeedbackRow(long id, long answerRevisionId, String observation, String suggestion,
                       String suggestionFocus, String previousReview) {
    }
}
