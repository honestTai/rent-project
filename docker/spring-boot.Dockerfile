# syntax=docker/dockerfile:1

FROM maven:3.8.8-eclipse-temurin-8 AS build

ARG MODULE_NAME
WORKDIR /workspace

COPY . .
RUN mvn -pl ${MODULE_NAME} -am -DskipTests package

FROM eclipse-temurin:8-jre

ARG MODULE_NAME
WORKDIR /app

ENV TZ=Asia/Shanghai
ENV SPRING_PROFILES_ACTIVE=local,docker
ENV JAVA_OPTS=""

COPY --from=build /workspace/${MODULE_NAME}/target/${MODULE_NAME}-0.0.1-SNAPSHOT.jar /app/app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
