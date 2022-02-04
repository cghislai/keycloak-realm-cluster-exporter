FROM openjdk:17-alpine

ADD target/keycloak-realm-cluster-exporter-jar-with-dependencies.jar /exporter.jar

ENTRYPOINT [ "/opt/openjdk-17/bin/java" ]
CMD [  "-jar" ,  "/exporter.jar" ]
