# 多阶段构建：Maven 编译 → JRE 运行
# 构建：docker build -t ceramic-platform .
# 运行：见 docker-compose.yml 的 backend 服务（需 MySQL 位于宿主机，Redis/RabbitMQ 走 compose 网络）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
# 上传文件目录：挂载 volume 防止容器重建丢图
RUN mkdir -p /app/uploads
VOLUME ["/app/uploads"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
