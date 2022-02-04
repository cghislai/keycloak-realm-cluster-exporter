FROM openjdk:17-alpine

ADD target/keycloak-realm-cluster-exporter-jar-with-dependencies.jar /exporter.jar

ENTRYPOINT [ "/usr/local/openjdk-15/bin/java" ]
CMD [  "-jar" ,  "/exporter.jar"]
