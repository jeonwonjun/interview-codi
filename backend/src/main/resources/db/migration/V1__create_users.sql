-- 카카오 계정과 연결한 서비스 내부 사용자. 계정 식별값만 저장한다.
-- 닉네임·이메일·프로필 사진 등 카카오 프로필과 사용자 정보 응답 원문은 저장하지 않는다.
create table users (
    id            bigint generated always as identity primary key,
    -- 카카오 사용자 정보 조회 응답의 회원번호(id). 필수·유일하며, 동시에 들어온 첫 로그인의 중복 가입도 이 제약으로 막는다.
    -- 첫 버전에는 탈퇴 기능이 없어 단순 유일성 제약을 둔다. 탈퇴 후 재가입을 지원하면 삭제되지 않은 행끼리의 부분 유일 인덱스로 바꾼다.
    kakao_user_id bigint      not null,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    deleted_at    timestamptz,
    constraint uk_users_kakao_user_id unique (kakao_user_id)
);
