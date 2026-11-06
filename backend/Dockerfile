# 售后工单系统 · 应用镜像（Phase 9 用）
# 构建：在 backend 目录执行 mvn package 之后
#   docker build -t ticket-system:1.0 .
FROM eclipse-temurin:21-jre

WORKDIR /app

ENV TZ=Asia/Shanghai \
    LOG_PATH=/app/logs \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

COPY target/ticket-system.jar /app/app.jar

EXPOSE 8080

# 容器内用 docker profile：主机名是 mysql / redis / rabbitmq 而不是 127.0.0.1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=docker"]
