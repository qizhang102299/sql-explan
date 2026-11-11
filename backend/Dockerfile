# 构建阶段：请在有网络的环境预先执行 mvn dependency:go-offline，或将 .m2 缓存挂载进构建
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline -DskipTests
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY --from=build /build/target/sql-explan-*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
