-- 큐 대기 시간(접수 → 워커가 처음 꺼냄)을 재기 위한 시각. 재시도로 다시 꺼내도 처음 값을 유지한다.
-- 큐 대기 = first_claimed_at - created_at, 전체 지연 = finished_at - created_at.
alter table ai_requests add column first_claimed_at timestamptz;
