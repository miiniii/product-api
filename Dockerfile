# 1. Java 17 기반 경량 이미지 사용
FROM openjdk:17-jdk-slim

# 2. 임시 디렉토리 설정 (Spring Boot 최적화)
VOLUME /tmp

# 3. .jar 파일을 앱으로 복사
ARG JAR_FILE=build/libs/test-repo-java-0.0.1-SNAPSHOT.jar
#ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# 4. 실행 명령
ENTRYPOINT ["java", "-jar", "/app.jar"]
