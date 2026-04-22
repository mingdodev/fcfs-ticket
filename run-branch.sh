#!/bin/bash
set -e

BRANCH=$1

if [ -z "$BRANCH" ]; then
  echo "사용법: ./run-branch.sh <branch>"
  exit 1
fi

echo "[브랜치 변경] $BRANCH"
git checkout $BRANCH

echo "[환경 리셋]"
docker compose down -v --remove-orphans

echo "[실행]"
docker compose up --build -d