package io.interviewcodi.backend.practice;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class FeedbackRepository {

    private final JdbcClient jdbcClient;

    FeedbackRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    List<FeedbackRow> findByPractice(long practiceId) {
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

    record FeedbackRow(long id, long answerRevisionId, String observation, String suggestion,
                       String suggestionFocus, String previousReview) {
    }
}
