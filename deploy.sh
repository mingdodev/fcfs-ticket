#!/bin/bash

# --- 1. 버전 주입 설정 ---
TARGET_TAG=${1:-v0}

# --- 2. 변수 설정 ---
DOCKER_ID="mingdodev"
IMAGE_NAME="fcfs-ticket"
PEM_KEY=""
SERVER_USER=""
SERVER_IP=""
SERVER_PATH=""

echo "========= [🚀 배포 시작: 버전 $TARGET_TAG] ========="

# [로컬] 빌드 및 푸시
docker build --platform linux/amd64 -t $DOCKER_ID/$IMAGE_NAME:$TARGET_TAG .
docker push $DOCKER_ID/$IMAGE_NAME:$TARGET_TAG

echo "========= 2. 파일 전송 ========="
ssh -i $PEM_KEY $SERVER_USER@$SERVER_IP "mkdir -p $SERVER_PATH/docker"

# init.sql 전송 (없을 때만)
ssh -i $PEM_KEY $SERVER_USER@$SERVER_IP "[ ! -f $SERVER_PATH/docker/init.sql ]"
if [ $? -eq 0 ]; then
    scp -i $PEM_KEY ./docker/init.sql $SERVER_USER@$SERVER_IP:$SERVER_PATH/docker/
fi

# docker-compose.yml 전송 (항상 최신으로 덮어씀)
scp -i $PEM_KEY docker-compose.yml $SERVER_USER@$SERVER_IP:$SERVER_PATH/

echo "========= 3. 원격 서버 실행 (TAG 주입) ========="
ssh -i $PEM_KEY $SERVER_USER@$SERVER_IP << EOF
  cd $SERVER_PATH

  # 1. 프로젝트 이름(-p)으로 묶인 모든 컨테이너/네트워크를 한 번에 내리기
  docker compose -p fcfs down --remove-orphans || true

  # 2. 새 이미지 가져오기
  TAG=$TARGET_TAG docker compose -p fcfs pull

  # 3. 새 버전 실행
  TAG=$TARGET_TAG docker compose -p fcfs up -d

  # 안 쓰는 리소스 정리
  docker system prune -f
EOF

echo "========= [✅ 배포 완료] ========="