# 构建阶段
FROM registry.cn-hangzhou.aliyuncs.com/library/openjdk:17-jre-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src
RUN chmod +x gradlew && ./gradlew bootJar

# 运行阶段
FROM registry.cn-hangzhou.aliyuncs.com/library/openjdk:17-jre-alpine
WORKDIR /app
# 复制jar包到容器（注意jar包名称要和你的一致）
COPY /build/libs/*.jar app.jar
#COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]