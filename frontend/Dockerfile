# 빌드 단계: glibc 계열 node 이미지를 쓴다(TypeScript 7 네이티브 바이너리가 glibc용이라 alpine은 쓰지 않는다)
FROM node:22-bookworm-slim AS build
WORKDIR /app
# 의존성 파일을 먼저 복사해 npm ci 레이어를 캐시한다
COPY package.json package-lock.json ./
RUN npm ci
# 소스를 복사해 빌드한다(VITE_API_BASE를 주지 않아 기본 /api 상대 주소를 쓴다)
COPY . .
RUN npm run build

# 실행 단계: 공식 nginx 이미지(debian 계열)로 dist를 정적 서빙한다
FROM nginx:1.27
# 템플릿은 시작할 때 envsubst가 /etc/nginx/conf.d/default.conf로 바꾼다
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
# /api 요청을 넘길 백엔드 주소(http://호스트:포트 형식)
ENV BACKEND_URL=http://backend:8080
EXPOSE 80
