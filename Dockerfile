# 构建阶段：Git拉代码→Gradle打包Jar（流水线必须用多阶段！）
FROM eclipse-temurin:17-jre-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src
RUN chmod +x ./gradlew && ./gradlew bootJar

# 运行阶段：只保留Jar包，轻量镜像
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 正确多阶段复制：从builder阶段复制打包好的Jar（你之前注释掉了，是错误！）
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]