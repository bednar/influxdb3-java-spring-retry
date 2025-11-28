FROM maven:3.9-eclipse-temurin-21
LABEL authors="karl"

WORKDIR /home/ubuntu

COPY pom.xml ./
COPY src ./src

ENV INFLUXDB_URL=https://us-east-1-1.aws.cloud2.influxdata.com/
ENV INFLUXDB_TOKEN=<TO_DO_INJECT_TOKEN>
ENV INFLUXDB_DATABASE=EAR_6466

ENTRYPOINT ["mvn", "spring-boot:run", "-Dspring-boot.run.jvmArguments=\"--add-opens=java.base/java.nio=ALL-UNNAMED\""]
