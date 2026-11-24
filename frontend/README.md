# 문서 관리 프론트엔드

React 19 + TypeScript + Vite + plain CSS. 데이터는 백엔드(`../backend`)가 보관하고 프런트는 `/api`로 부른다. 브라우저 저장소(localStorage, IndexedDB)는 쓰지 않는다.

```bash
npm install
npm run dev      # http://localhost:5173 (먼저 백엔드를 띄운다)
npm run build    # tsc -b && vite build -> dist
npm run lint     # oxlint
npm run preview  # 빌드 결과 미리보기
```

저장 계층은 `src/api/storage.ts`이고 백엔드 REST API를 fetch로 부른다. 서버 오류는 응답의 `message`를 `Error`로 던져 화면에 그대로 보인다.

- 개발 순서: 백엔드(`cd ../backend && ./gradlew bootRun`, 포트 8080)를 먼저 띄우고 `npm run dev`를 실행한다. `npm run dev`와 `npm run preview`는 `/api`를 `http://localhost:8080`으로 프록시한다(`vite.config.ts`).
- 기본 주소는 `/api`(같은 출처)다. 환경변수 `VITE_API_BASE`에 절대 주소를 주면 그 주소를 부르며, 이때는 백엔드 CORS 허용 origin이 필요하다.
- 백엔드가 꺼져 있으면 첫 목록 읽기가 실패해 화면이 비어 있다.

## 도커 이미지

`frontend/Dockerfile`은 `node:22-bookworm-slim`에서 `npm ci && npm run build`로 `dist`를 만들고, 공식 nginx 이미지가 그것을 80번 포트로 내보내는 멀티스테이지다. TypeScript 7 네이티브 바이너리가 glibc용이라 alpine 이미지는 쓰지 않는다. 빌드하는 동안 npm 레지스트리에 접근해야 한다.

```bash
cd frontend
docker build -t filemanager-frontend .
```

| 환경변수 | 이미지 기본값 | 설명 |
|---|---|---|
| `BACKEND_URL` | `http://backend:8080` | `/api` 요청을 넘길 백엔드 주소. `http://호스트:포트` 형식이며 경로나 끝의 `/`는 붙이지 않는다. 시작할 때 envsubst가 nginx 템플릿(`nginx/default.conf.template`)에 이 값을 넣는다 |

백엔드가 호스트의 8080에 떠 있을 때의 실행 예(접속 주소 `http://localhost:3000`):

```bash
docker run -d -p 3000:80 -e BACKEND_URL=http://host.docker.internal:8080 filemanager-frontend
```

- Linux 호스트에서 `host.docker.internal`을 쓰려면 `--add-host=host.docker.internal:host-gateway`를 함께 준다.
- 같은 도커 네트워크에 `backend`라는 이름의 컨테이너가 있으면 `BACKEND_URL`을 생략할 수 있다. 백엔드 이름을 해석하지 못하면 nginx가 시작하지 않는다.
- `VITE_API_BASE` 없이 빌드하므로 화면은 같은 출처의 `/api`를 부르고 nginx가 그 요청을 백엔드로 넘긴다. 그래서 백엔드 CORS 설정을 바꿀 필요가 없다.
- 업로드 한도는 백엔드(요청 210MB)가 정하고 nginx는 220m까지 받는다.
- SPA 경로는 `index.html`로 돌아간다.
