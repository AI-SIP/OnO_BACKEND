# open jdk 17 버전의 환경을 구성한다.
# Eclipse Temurin은 OpenJDK의 공식 후속 이미지입니다
# alpine 대신 일반 버전 사용 (ARM64/Apple Silicon 지원)
FROM eclipse-temurin:17-jre

# 작업 디렉토리 설정
WORKDIR /app

# build가 될 때 JAR_FILE이라는 변수 명에 build/libs/*.jar 선언
# build/libs - gradle로 빌드했을 때 jar 파일이 생성되는 경로임
ARG JAR_FILE=build/libs/*.jar

# JAR 파일 복사
COPY ${JAR_FILE} backend-0.0.1.SNAPSHOT.jar

# Firebase 키를 JAR 내부 경로처럼 배치 (classpath에서 접근 가능하도록)
# Spring Boot의 경우 JAR와 같은 디렉토리에 있으면 classpath로 인식됨
COPY FirebaseAdminKey.json FirebaseAdminKey.json

# 운영 및 개발에서 사용되는 환경 설정을 분리한다.
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "-Dspring.profiles.active=prod", "backend-0.0.1.SNAPSHOT.jar"]