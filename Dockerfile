FROM openjdk:15.0.1-slim

ADD target/keycloak-realm-cluster-exporter-jar-with-dependencies.jar /exporter.jar

ENTRYPOINT [ "/usr/local/openjdk-15/bin/java" ,  "-jar" ,  "/exporter.jar" ]
