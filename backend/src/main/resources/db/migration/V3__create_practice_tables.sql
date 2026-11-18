-- 자료 없는 텍스트 연습. 설계: 결정 기록 0003(답변 시도와 수정 버전), 0007(AI 요청 큐와 결과 일관성).
-- 현재 질문·현재 버전은 포인터 컬럼 없이 가장 큰 order_no·revision_no로 판단한다.
-- 연습을 삭제해도 하위 행에는 deleted_at을 찍지 않는다. 하위 조회는 항상 연습의 삭제 상태를 함께 확인한다.

-- 한 번의 연습. 진행을 바꾸는 모든 쓰기는 이 행을 먼저 잠근다(잠금 순서: practices → 하위 행 → ai_requests).
create table practices (
    id           bigint generated always as identity primary key,
    user_id      bigint      not null,
    start_mode   varchar(20) not null,
    status       varchar(20) not null default 'IN_PROGRESS',
    completed_at timestamptz,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    deleted_at   timestamptz,
    constraint fk_practices_user foreign key (user_id) references users (id),
    constraint ck_practices_start_mode check (start_mode in ('EXPERIENCE', 'RESUME')),
    constraint ck_practices_status check (status in ('IN_PROGRESS', 'COMPLETED')),
    constraint ck_practices_completed_at check ((status = 'COMPLETED') = (completed_at is not null))
);

create index ix_practices_user_created on practices (user_id, created_at desc) where deleted_at is null;

-- 연습 안의 질문. 고정 질문(EXPERIENCE_PRESET) 또는 실제 답변 버전에 근거한 꼬리질문(FOLLOW_UP).
create table questions (
    id                        bigint generated always as identity primary key,
    practice_id               bigint      not null,
    order_no                  int         not null,
    content                   text        not null,
    source_kind               varchar(20) not null,
    preset_key                varchar(50),
    source_answer_revision_id bigint,
    ai_request_id             bigint,
    skipped_at                timestamptz,
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now(),
    deleted_at                timestamptz,
    constraint fk_questions_practice foreign key (practice_id) references practices (id),
    constraint uk_questions_practice_order unique (practice_id, order_no),
    constraint uk_questions_ai_request unique (ai_request_id),
    constraint ck_questions_order_no check (order_no >= 1),
    constraint ck_questions_source_kind check (source_kind in ('EXPERIENCE_PRESET', 'FOLLOW_UP')),
    constraint ck_questions_preset check ((source_kind = 'EXPERIENCE_PRESET') = (preset_key is not null)),
    constraint ck_questions_follow_up check (
        (source_kind = 'FOLLOW_UP') = (source_answer_revision_id is not null and ai_request_id is not null))
);

-- 한 번의 답변 시도. 재답변은 새 시도, 문구 교정은 같은 시도의 새 버전이다.
create table answers (
    id                bigint generated always as identity primary key,
    question_id       bigint      not null,
    attempt_no        int         not null,
    -- 브라우저가 제출마다 만든 UUID. 같은 제출을 다시 보내도 시도를 한 번만 만든다.
    client_request_id uuid        not null,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    deleted_at        timestamptz,
    constraint fk_answers_question foreign key (question_id) references questions (id),
    constraint uk_answers_question_attempt unique (question_id, attempt_no),
    constraint uk_answers_question_client_request unique (question_id, client_request_id),
    constraint ck_answers_attempt_no check (attempt_no >= 1)
);

-- 답변 시도의 본문 버전. 최초 본문이 1이고, 교정은 덮어쓰지 않고 새 버전을 쌓는다.
-- 동시 교정은 (answer_id, revision_no) 유일 제약으로 하나만 성공한다.
create table answer_revisions (
    id          bigint generated always as identity primary key,
    answer_id   bigint      not null,
    revision_no int         not null,
    content     text        not null,
    created_at  timestamptz not null default now(),
    deleted_at  timestamptz,
    constraint fk_answer_revisions_answer foreign key (answer_id) references answers (id),
    constraint uk_answer_revisions_answer_revision unique (answer_id, revision_no),
    constraint ck_answer_revisions_revision_no check (revision_no >= 1),
    constraint ck_answer_revisions_content check (char_length(btrim(content)) between 1 and 3000)
);

alter table questions
    add constraint fk_questions_source_answer_revision
        foreign key (source_answer_revision_id) references answer_revisions (id);

-- AI 작업 큐. 답변·버전과 같은 트랜잭션에 PENDING으로 넣고, 워커가 FOR UPDATE SKIP LOCKED로 꺼낸다.
create table ai_requests (
    id                 bigint generated always as identity primary key,
    practice_id        bigint      not null,
    kind               varchar(30) not null,
    -- 입력으로 고정한 답변 버전. 결과 반영 때 여전히 현재 버전인지 확인한다.
    answer_revision_id bigint      not null,
    status             varchar(20) not null default 'PENDING',
    discard_reason     varchar(30),
    attempt_count      int         not null default 0,
    available_at       timestamptz not null default now(),
    lease_token        uuid,
    lease_until        timestamptz,
    -- 오류 종류만 남긴다. 답변 본문·프롬프트는 남기지 않는다.
    last_error         varchar(300),
    finished_at        timestamptz,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    constraint fk_ai_requests_practice foreign key (practice_id) references practices (id),
    constraint fk_ai_requests_answer_revision foreign key (answer_revision_id) references answer_revisions (id),
    constraint ck_ai_requests_kind check (kind in ('FEEDBACK', 'FOLLOW_UP_QUESTION')),
    constraint ck_ai_requests_status check (status in ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'DISCARDED')),
    constraint ck_ai_requests_discard_reason check (
        (status = 'DISCARDED') = (discard_reason is not null)
        and (discard_reason is null
            or discard_reason in ('SUPERSEDED', 'PRACTICE_DELETED', 'PRACTICE_COMPLETED', 'QUESTION_CHANGED'))),
    constraint ck_ai_requests_finished_at check (
        (status in ('SUCCEEDED', 'FAILED', 'DISCARDED')) = (finished_at is not null)),
    constraint ck_ai_requests_lease check ((status = 'RUNNING') = (lease_token is not null and lease_until is not null))
);

-- 같은 버전·종류의 처리 중 요청은 하나, 성공도 하나. 동시 재요청의 마지막 방어선이다.
create unique index uk_ai_requests_active on ai_requests (answer_revision_id, kind)
    where status in ('PENDING', 'RUNNING');
create unique index uk_ai_requests_succeeded on ai_requests (answer_revision_id, kind)
    where status = 'SUCCEEDED';
-- 워커의 큐 조회.
create index ix_ai_requests_pending on ai_requests (available_at) where status = 'PENDING';
create index ix_ai_requests_running on ai_requests (lease_until) where status = 'RUNNING';

alter table questions
    add constraint fk_questions_ai_request foreign key (ai_request_id) references ai_requests (id);

-- 답변 버전 하나에 대한 피드백. 버전당 최대 하나.
create table feedback (
    id                 bigint generated always as identity primary key,
    ai_request_id      bigint       not null,
    answer_revision_id bigint       not null,
    observation        text         not null,
    suggestion         text,
    suggestion_focus   varchar(20),
    previous_review    text,
    generator          varchar(100) not null,
    created_at         timestamptz  not null default now(),
    deleted_at         timestamptz,
    constraint fk_feedback_ai_request foreign key (ai_request_id) references ai_requests (id),
    constraint fk_feedback_answer_revision foreign key (answer_revision_id) references answer_revisions (id),
    constraint uk_feedback_ai_request unique (ai_request_id),
    constraint uk_feedback_answer_revision unique (answer_revision_id),
    constraint ck_feedback_suggestion_focus check (
        suggestion_focus is null or suggestion_focus in ('ACCURACY', 'ANSWER', 'EVIDENCE', 'ORDER')),
    constraint ck_feedback_suggestion check ((suggestion is null) = (suggestion_focus is null))
);
