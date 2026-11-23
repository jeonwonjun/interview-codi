-- 음성 답변. 설계: docs/voice-practice-design.md 2절·5-1·5-3.
-- 음성 시도는 녹음을 먼저 저장하고, 인식(TRANSCRIBE)이 끝나면 인식 문구를 버전 1로 만든다.

-- 시도를 어떻게 냈는지. 기존 시도는 모두 텍스트다.
alter table answers
    add column input_mode varchar(10) not null default 'TEXT',
    add constraint ck_answers_input_mode check (input_mode in ('TEXT', 'VOICE'));

-- 음성 시도의 녹음. 시도 하나에 녹음 하나. 파일은 저장소(RecordingStorage)에 두고 위치 키만 남긴다.
-- 키는 추측할 수 없게 만들고 외부에 공개하지 않는다. 파일은 항상 API가 소유자·삭제 상태를 확인한 뒤 흘려보낸다.
-- 삭제는 deleted_at만 찍고 파일은 남긴다. 실제 파일 정리는 보관 기간을 정할 때 함께 정한다.
create table recordings (
    id           bigint generated always as identity primary key,
    answer_id    bigint       not null,
    storage_key  varchar(200) not null,
    -- 브라우저가 보낸 형식에서 매개변수(codecs 등)를 뗀 값. Chrome은 audio/webm, Safari는 audio/mp4.
    content_type varchar(50)  not null,
    -- 브라우저가 잰 길이. 3분에서 녹음을 멈추므로 여유를 두고 5분까지 받는다.
    duration_ms  int          not null,
    size_bytes   bigint       not null,
    created_at   timestamptz  not null default now(),
    deleted_at   timestamptz,
    constraint fk_recordings_answer foreign key (answer_id) references answers (id),
    constraint uk_recordings_answer unique (answer_id),
    constraint uk_recordings_storage_key unique (storage_key),
    constraint ck_recordings_content_type check (content_type like 'audio/%'),
    constraint ck_recordings_duration_ms check (duration_ms between 1 and 300000),
    -- 음성인식 제공자의 파일 크기 한도(OpenAI 25MB)를 넘지 않는다.
    constraint ck_recordings_size_bytes check (size_bytes between 1 and 26214400)
);

-- 인식 요청은 답변 버전이 아니라 녹음을 가리킨다. 버전은 인식이 끝나야 생긴다.
alter table ai_requests
    alter column answer_revision_id drop not null,
    add column recording_id bigint,
    add constraint fk_ai_requests_recording foreign key (recording_id) references recordings (id),
    drop constraint ck_ai_requests_kind,
    add constraint ck_ai_requests_kind check (kind in ('FEEDBACK', 'FOLLOW_UP_QUESTION', 'TRANSCRIBE')),
    -- TRANSCRIBE는 녹음만, 나머지는 답변 버전만 입력으로 가진다.
    add constraint ck_ai_requests_input check (
        (kind = 'TRANSCRIBE' and recording_id is not null and answer_revision_id is null)
        or (kind <> 'TRANSCRIBE' and answer_revision_id is not null and recording_id is null));

-- 같은 녹음의 처리 중 인식 요청은 하나, 성공도 하나. 버전 기준 유일 인덱스(V3)와 같은 역할이다.
-- 버전 기준 인덱스는 answer_revision_id가 null인 인식 요청끼리 부딪히지 않는다(PostgreSQL은 null을 서로 다르게 본다).
create unique index uk_ai_requests_recording_active on ai_requests (recording_id)
    where kind = 'TRANSCRIBE' and status in ('PENDING', 'RUNNING');
create unique index uk_ai_requests_recording_succeeded on ai_requests (recording_id)
    where kind = 'TRANSCRIBE' and status = 'SUCCEEDED';
