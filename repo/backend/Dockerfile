# Stage 1: Build the application using Maven
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .

# Retry dependency resolution to handle transient DNS/network issues in Docker build environments.
RUN set -e; \
	SUCCESS=0; \
	for i in 1 2 3 4 5; do \
		if mvn -B -DskipTests -Dmaven.wagon.http.retryHandler.count=5 dependency:go-offline; then SUCCESS=1; break; fi; \
		sleep 10; \
	done; \
	if [ "$SUCCESS" -ne 1 ]; then echo "Maven dependency resolution failed after retries"; exit 1; fi

COPY src ./src
RUN set -e; \
	SUCCESS=0; \
	for i in 1 2 3; do \
		if mvn -B -DskipTests -Dmaven.wagon.http.retryHandler.count=5 clean package; then SUCCESS=1; break; fi; \
		sleep 10; \
	done; \
	if [ "$SUCCESS" -ne 1 ]; then echo "Maven package build failed after retries"; exit 1; fi

# Normalize the built artifact path for the runtime stage.
RUN set -e; \
	JAR_PATH=$(ls /app/target/*.jar | grep -v '\\.original$' | head -n 1); \
	if [ -z "$JAR_PATH" ]; then echo "No runnable jar produced in /app/target"; exit 1; fi; \
	cp "$JAR_PATH" /app/app.jar

# Stage 2: Create the minimal runtime image
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
