package io.interviewcodi.backend.practice.storage;

import org.springframework.core.io.Resource;

/**
 * 녹음 파일 저장소. 지금은 로컬 디스크, 배포할 때 S3 호환 저장소로 바꾼다(설계 문서 5-3).
 * 키는 서버가 만들고 외부에 공개하지 않는다. 파일 접근 권한 확인은 호출하는 쪽(API)이 한다.
 */
public interface RecordingStorage {

    void save(String key, byte[] content);

    Resource load(String key);

    /**
     * 파일을 실제로 지운다. DB에 기록되지 못한 파일(저장 뒤 트랜잭션 실패)을 치울 때만 쓴다.
     * 사용자가 지운 녹음은 행에 삭제 시각만 찍고 파일은 남긴다.
     */
    void deleteOrphan(String key);
}
