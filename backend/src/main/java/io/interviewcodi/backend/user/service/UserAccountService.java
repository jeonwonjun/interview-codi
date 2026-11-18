package io.interviewcodi.backend.user.service;

import io.interviewcodi.backend.user.domain.DeletedUserException;
import io.interviewcodi.backend.user.repository.UserRepository;
import io.interviewcodi.backend.user.repository.UserRepository.UserStatus;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 카카오 회원번호로 서비스 내부 사용자를 찾거나 처음이면 생성한다.
 */
@Service
public class UserAccountService {

    private final UserRepository userRepository;

    UserAccountService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 같은 카카오 회원번호의 첫 로그인이 동시에 들어와도 내부 사용자는 하나만 생성된다.
     * <p>
     * 먼저 {@code on conflict do nothing}으로 삽입을 시도하고, 이미 있으면 조회한다. 애플리케이션에서 "조회 후 없으면
     * 삽입"하는 방식과 달리 두 요청 사이의 틈에 중복 행이 생기지 않는다.
     *
     * @return 내부 사용자 ID
     * @throws DeletedUserException 논리적으로 삭제된 사용자인 경우
     */
    public long findOrCreateByKakaoUserId(long kakaoUserId) {
        Optional<Long> createdId = userRepository.insertIfAbsent(kakaoUserId);
        if (createdId.isPresent()) {
            return createdId.get();
        }

        UserStatus existing = userRepository.findStatusByKakaoUserId(kakaoUserId)
                .orElseThrow(() -> new IllegalStateException("User conflicted but not found: " + kakaoUserId));
        // 첫 버전에는 탈퇴 기능이 없지만, 삭제 상태의 계정으로 로그인시키지 않도록 막아 둔다.
        if (existing.deleted()) {
            throw new DeletedUserException(existing.id());
        }
        return existing.id();
    }
}
