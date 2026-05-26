# Deploy

EC2 + Caddy + MySQL 시연용 배포 가이드.

## 사전 준비

1. AWS 콘솔에서 EC2 인스턴스 생성
   - AMI: Ubuntu Server 22.04 LTS
   - 타입: `t3.micro` (프리티어)
   - 스토리지: gp3 8GB
   - 키페어 생성 후 `.pem` 안전하게 보관
   - 보안그룹 인바운드 규칙:
     - SSH (22) — 내 IP만
     - HTTP (80) — Anywhere (Let's Encrypt 인증용)
     - HTTPS (443) — Anywhere
2. 도메인은 별도 구매하지 않음 → `<dash로 구분된 EC2 IP>.sslip.io` 사용
   - 예: EC2 IP `13.125.1.2` → `13-125-1-2.sslip.io`
   - 인스턴스 생성 후 `deploy/Caddyfile` 의 호스트를 실제 IP 기반으로 교체

## 최초 셋업

```bash
# 로컬에서 deploy 디렉토리 통째로 업로드
scp -i ~/your-key.pem -r deploy ubuntu@<EC2_IP>:/home/ubuntu/

# EC2 접속
ssh -i ~/your-key.pem ubuntu@<EC2_IP>

# 셋업 스크립트 실행
cd ~/deploy
sudo bash setup.sh

# 스크립트가 안내하는 5단계 수동 작업 진행 (mysql init, env, systemd, Caddy)
```

## GitHub Actions 시크릿 등록

리포지토리 Settings → Secrets and variables → Actions 에서 등록:

| 이름            | 값                                                 |
| --------------- | -------------------------------------------------- |
| `EC2_HOST`      | EC2 퍼블릭 IP 또는 도메인                          |
| `EC2_USER`      | `ubuntu`                                           |
| `EC2_SSH_KEY`   | `.pem` 파일 내용 전체 (`-----BEGIN ...` 포함)      |

이후 `main` 브랜치에 push 되면 자동으로 빌드 → SCP 업로드 → 서비스 재시작.

## 로그 확인

```bash
# 백엔드 로그
sudo journalctl -u gudocs -f

# Caddy 로그
sudo journalctl -u caddy -f
sudo tail -f /var/log/caddy/gudocs.log
```

## 시연 종료 후 정리

```bash
# EC2 인스턴스 종료 (Terminate)
# Elastic IP 사용했다면 해제 (보관 시 과금됨)
```
