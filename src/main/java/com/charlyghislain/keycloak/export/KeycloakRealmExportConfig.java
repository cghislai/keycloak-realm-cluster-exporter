package com.charlyghislain.keycloak.export;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.net.URI;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@ToString
public class KeycloakRealmExportConfig {

    private Set<String> realmNames;
    private URI keycloakApiUri;
    private String keycloakHostname;
    private String adminUser;
    private String adminPassword;
    private boolean exportUsers;

    private String secretNamespace;
    private String secretNamePattern;
    private String secretKeyPattern;
    private Map<String, String> secretLabels;
    private Map<String, String> secretAnnotations;

    private boolean debug;

}
