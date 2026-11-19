# 빌드 단계: JDK 17에서 bootJar를 만든다(테스트는 돌리지 않는다)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
# 래퍼와 빌드 설정을 먼저 복사해 Gradle 배포판 레이어를 캐시한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# gradlew의 CR(윈도 줄바꿈)을 지우고 실행 권한을 준다
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon --version
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# 실행 단계: JRE 17에서 jar를 실행한다
FROM eclipse-temurin:17-jre
WORKDIR /app
# 비루트 사용자로 돌리고, 저장 루트를 만들어 쓰기 가능하게 한다(VOLUME 앞에서 chown)
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app \
    && mkdir -p /app/data/storage \
    && chown -R app:app /app/data/storage
COPY --from=build /workspace/build/libs/backend-0.0.1-SNAPSHOT.jar /app/app.jar
# 저장 루트 기본값. 볼륨 지점이다
ENV FILEMANAGER_STORAGE_ROOT=/app/data/storage
VOLUME ["/app/data/storage"]
# DB는 MySQL만 쓴다. 접속 정보는 application.yml 기본값을 따르며 실행 시 -e FILEMANAGER_DB_URL 등으로 준다
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
