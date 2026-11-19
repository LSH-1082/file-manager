# 공식 MySQL 8 이미지를 기반으로 한다.
FROM mysql:8

# DB와 사용자 기본값이다. compose가 환경변수로 덮어쓸 수 있다.
# MYSQL_PASSWORD와 MYSQL_ROOT_PASSWORD는 이미지에 넣지 않고 실행할 때(compose) 준다.
ENV MYSQL_DATABASE=fileManager \
    MYSQL_USER=fileManager

# 스키마 SQL은 /docker-entrypoint-initdb.d에 넣지 않는다. 스키마는 백엔드가 시작할 때 Flyway로 만든다.
# 문자셋은 utf8mb4, 콜레이션 utf8mb4_bin은 docs/db/schema.sql의 테이블 COLLATE와 같다.
# 시간대는 이름 'UTC' 대신 '+00:00'을 쓴다(첫 초기화 때 시간대 테이블이 없어도 기동되도록).
# ENTRYPOINT는 공식 이미지 것을 그대로 쓰고, EXPOSE 3306과 VOLUME /var/lib/mysql도 공식 이미지가 이미 선언한다.
CMD ["mysqld", "--character-set-server=utf8mb4", "--collation-server=utf8mb4_bin", "--default-time-zone=+00:00"]
