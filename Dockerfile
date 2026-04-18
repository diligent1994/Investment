# 【构建打包阶段】必须用JDK完整镜像！！支持编译源码打包Jar
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# 复制项目全部源码
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src

# 修复1：把Windows换行符转Linux换行符（解决gradlew脚本语法错误）
RUN sed -i 's/\r$//' gradlew
# 修复2：赋予执行权限 + 执行Gradle打包
RUN chmod +x ./gradlew && ./gradlew bootJar

# 【最终运行阶段】轻量JRE镜像，只运行Jar，不需要编译工具
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 多阶段构建：从打包阶段复制打好的Jar包（最终镜像极小）
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]