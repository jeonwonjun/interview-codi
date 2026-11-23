package io.interviewcodi.backend.practice.controller;

import io.interviewcodi.backend.auth.domain.LoginUser;
import io.interviewcodi.backend.common.error.BusinessException;
import io.interviewcodi.backend.common.error.ErrorCode;
import io.interviewcodi.backend.practice.dto.AnswerAttemptResponse;
import io.interviewcodi.backend.practice.service.AnswerService.SubmitResult;
import io.interviewcodi.backend.practice.service.VoiceAnswerService;
import io.interviewcodi.backend.practice.service.VoiceAnswerService.RecordingFile;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class VoiceAnswerController {

    private final VoiceAnswerService voiceAnswerService;

    VoiceAnswerController(VoiceAnswerService voiceAnswerService) {
        this.voiceAnswerService = voiceAnswerService;
    }

    /**
     * 음성 답변 올리기(multipart). 새로 저장하면 201, 같은 {@code clientRequestId}를 다시 보낸 경우 처음 결과와 200.
     * 응답의 {@code transcriptionRequest}를 폴링하다가 끝나면 연습 상세를 다시 읽는다.
     */
    @PostMapping(path = "/api/questions/{questionId}/voice-answers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<AnswerAttemptResponse> submit(LoginUser loginUser, @PathVariable long questionId,
                                                 @RequestPart("audio") MultipartFile audio,
                                                 @RequestParam("clientRequestId") UUID clientRequestId,
                                                 @RequestParam("durationMs") Integer durationMs) {
        SubmitResult result = voiceAnswerService.submit(loginUser.userId(), questionId, clientRequestId, durationMs,
                audio.getContentType(), readBytes(audio));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.attempt());
    }

    /**
     * 녹음 재생. 파일 주소를 공개하지 않고 매번 소유자·삭제 상태를 확인한 뒤 흘려보낸다.
     * {@code Range} 요청은 Spring이 206으로 잘라 보낸다(재생 위치 이동).
     */
    @GetMapping("/api/answers/{answerId}/recording")
    ResponseEntity<Resource> recording(LoginUser loginUser, @PathVariable long answerId) {
        RecordingFile file = voiceAnswerService.loadRecording(loginUser.userId(), answerId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(file.resource());
    }

    private static byte[] readBytes(MultipartFile audio) {
        try {
            return audio.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_RECORDING);
        }
    }
}
