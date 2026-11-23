package io.interviewcodi.backend.practice.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RecordingRepository {

    private final JdbcClient jdbcClient;

    RecordingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long insert(long answerId, String storageKey, String contentType, int durationMs, long sizeBytes) {
        return jdbcClient.sql("""
                        insert into recordings (answer_id, storage_key, content_type, duration_ms, size_bytes)
                        values (:answerId, :storageKey, :contentType, :durationMs, :sizeBytes)
                        returning id
                        """)
                .param("answerId", answerId)
                .param("storageKey", storageKey)
                .param("contentType", contentType)
                .param("durationMs", durationMs)
                .param("sizeBytes", sizeBytes)
                .query(Long.class)
                .single();
    }

    /** 연습의 삭제되지 않은 녹음. 연습의 삭제 확인은 호출하는 쪽이 한다. */
    public List<RecordingRow> findByPractice(long practiceId) {
        return jdbcClient.sql("""
                        select rec.id, rec.answer_id, rec.duration_ms
                        from recordings rec
                        join answers a on a.id = rec.answer_id and a.deleted_at is null
                        join questions q on q.id = a.question_id and q.deleted_at is null
                        where q.practice_id = :practiceId and rec.deleted_at is null
                        """)
                .param("practiceId", practiceId)
                .query(RecordingRow.class)
                .list();
    }

    /**
     * 본인의 삭제되지 않은 연습에 속하고, 시도·녹음도 삭제되지 않았을 때만 파일 위치를 돌려준다.
     * 다른 사용자의 녹음과 삭제된 녹음은 구분하지 않고 비어 있는 결과로 돌려준다.
     */
    public Optional<RecordingFileRow> findFileForOwner(long answerId, long userId) {
        return jdbcClient.sql("""
                        select rec.storage_key, rec.content_type, rec.size_bytes
                        from recordings rec
                        join answers a on a.id = rec.answer_id and a.deleted_at is null
                        join questions q on q.id = a.question_id and q.deleted_at is null
                        join practices p on p.id = q.practice_id
                        where rec.answer_id = :answerId and rec.deleted_at is null
                          and p.user_id = :userId and p.deleted_at is null
                        """)
                .param("answerId", answerId)
                .param("userId", userId)
                .query(RecordingFileRow.class)
                .optional();
    }

    /** 인식에 넘길 파일 위치와 결과를 붙일 시도. */
    public TranscribeSource findTranscribeSource(long recordingId) {
        return jdbcClient.sql("""
                        select answer_id, storage_key, content_type
                        from recordings
                        where id = :recordingId
                        """)
                .param("recordingId", recordingId)
                .query(TranscribeSource.class)
                .single();
    }

    /** 본인의 삭제되지 않은 연습에 속한 삭제되지 않은 녹음이면 연습 ID와 시도 ID. */
    public Optional<RecordingOwner> findOwner(long recordingId, long userId) {
        return jdbcClient.sql("""
                        select p.id as practice_id, a.id as answer_id
                        from recordings rec
                        join answers a on a.id = rec.answer_id and a.deleted_at is null
                        join questions q on q.id = a.question_id and q.deleted_at is null
                        join practices p on p.id = q.practice_id
                        where rec.id = :recordingId and rec.deleted_at is null
                          and p.user_id = :userId and p.deleted_at is null
                        """)
                .param("recordingId", recordingId)
                .param("userId", userId)
                .query(RecordingOwner.class)
                .optional();
    }

    public record TranscribeSource(long answerId, String storageKey, String contentType) {
    }

    public record RecordingOwner(long practiceId, long answerId) {
    }

    public record RecordingRow(long id, long answerId, int durationMs) {
    }

    public record RecordingFileRow(String storageKey, String contentType, long sizeBytes) {
    }
}
