FROM openjdk:17
WORKDIR /app
COPY . .
RUN ./gradlew build

CMD ["java", "-jar", "build/libs/synthea.jar"]
