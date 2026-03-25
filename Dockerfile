# 构建阶段
# FROM gradle:8.5-jdk17 AS builder
# WORKDIR /app
# COPY build.gradle settings.gradle ./
# COPY src ./src
# RUN gradle bootJar --no-daemon

# 运行阶段
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 复制jar包到容器（注意jar包名称要和你的一致）
COPY /build/libs/*.jar app.jar
#COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]