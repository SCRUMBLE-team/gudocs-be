-- 카카오 등 이메일 선택 동의 provider는 미동의 시 email이 null로 들어온다.
-- 엔티티는 이미 nullable(User.email, SocialAccount.email)인데 스키마가 NOT NULL로 남아 있어
-- 이메일 미동의 카카오 계정의 최초 로그인이 INSERT 단계에서 500으로 실패했다.
-- (ddl-auto=validate 는 nullability 를 검사하지 않아 기동 시점에 드러나지 않음)

ALTER TABLE `users`
  MODIFY COLUMN `email` varchar(255) NULL;

ALTER TABLE `social_accounts`
  MODIFY COLUMN `email` varchar(255) NULL;
