package io.interviewcodi.backend.practice.dto;

import java.util.List;

/** 최신순. 첫 버전은 페이지를 나누지 않는다. */
public record PracticeListResponse(List<PracticeSummaryResponse> items) {
}
