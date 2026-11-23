package io.interviewcodi.backend.practice.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 로컬 디스크 저장소. 임시 파일에 다 쓴 뒤 이름을 바꿔, 쓰다 만 파일이 키로 보이지 않게 한다.
 */
@Component
class LocalDiskRecordingStorage implements RecordingStorage {

    private final Path root;

    LocalDiskRecordingStorage(@Value("${practice.recording.local-dir}") Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void save(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temp, content);
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to save recording", ex);
        }
    }

    @Override
    public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            throw new UncheckedIOException(new IOException("Recording file is missing"));
        }
        return new FileSystemResource(path);
    }

    @Override
    public void deleteOrphan(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to delete orphan recording", ex);
        }
    }

    /** 키가 저장소 폴더 밖을 가리키지 못하게 한다. 키는 서버가 만들지만 마지막 방어선으로 확인한다. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Invalid recording key");
        }
        return path;
    }
}
