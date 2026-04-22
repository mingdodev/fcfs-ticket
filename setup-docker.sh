#!/bin/bash
set -e

echo "[Docker 설치]"
curl -fsSL https://get.docker.com | sh

echo "[Docker Compose plugin 확인]"
docker compose version

echo "[현재 유저 docker 권한 추가]"
sudo usermod -aG docker $USER

echo "[완료] 로그아웃 후 다시 접속하세요"