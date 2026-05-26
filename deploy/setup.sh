#!/usr/bin/env bash
# EC2 (Ubuntu 22.04) 최초 1회 셋업 스크립트.
# 실행: sudo bash setup.sh
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "sudo 로 실행하세요." >&2
  exit 1
fi

echo "==> 시스템 업데이트"
apt-get update -y
apt-get upgrade -y

echo "==> swap 2GB 추가 (RAM 1GB 인스턴스 대응)"
if [[ ! -f /swapfile ]]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "==> OpenJDK 21 설치"
apt-get install -y openjdk-21-jre-headless

echo "==> MySQL 8 설치"
DEBIAN_FRONTEND=noninteractive apt-get install -y mysql-server
systemctl enable --now mysql

echo "==> Caddy 설치"
apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
  | tee /etc/apt/sources.list.d/caddy-stable.list
apt-get update -y
apt-get install -y caddy

echo "==> 디렉토리 구조 생성"
mkdir -p /opt/gudocs
mkdir -p /etc/gudocs
mkdir -p /var/log/caddy
chown -R ubuntu:ubuntu /opt/gudocs

echo ""
echo "================================================================"
echo "기본 설치 완료. 이제 다음 작업을 수동으로 진행하세요:"
echo ""
echo "1. MySQL 초기화"
echo "   sudo mysql_secure_installation"
echo "   sudo mysql < /home/ubuntu/deploy/mysql-init.sql"
echo ""
echo "2. 환경변수 파일 생성"
echo "   sudo cp /home/ubuntu/deploy/env.example /etc/gudocs/env"
echo "   sudo chmod 600 /etc/gudocs/env"
echo "   sudo vi /etc/gudocs/env"
echo ""
echo "3. systemd 서비스 등록"
echo "   sudo cp /home/ubuntu/deploy/systemd/gudocs.service /etc/systemd/system/"
echo "   sudo systemctl daemon-reload"
echo "   sudo systemctl enable gudocs"
echo ""
echo "4. Caddyfile 적용"
echo "   sudo cp /home/ubuntu/deploy/Caddyfile /etc/caddy/Caddyfile"
echo "   sudo vi /etc/caddy/Caddyfile   # 도메인 교체"
echo "   sudo systemctl reload caddy"
echo ""
echo "5. 최초 JAR 업로드 후 서비스 시작 (GitHub Actions 가 알아서 함)"
echo "   sudo systemctl start gudocs"
echo "================================================================"
