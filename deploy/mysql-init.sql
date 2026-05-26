-- 최초 1회만 실행. 비밀번호는 실제 값으로 교체할 것.
CREATE DATABASE IF NOT EXISTS gudocs
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'gudocs'@'localhost' IDENTIFIED BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON gudocs.* TO 'gudocs'@'localhost';
FLUSH PRIVILEGES;
