package com.charlyghislain.keycloak.export;

import lombok.Getter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum ConfigurationProperty {

    HELP("help", "Display help output and exits."),
    DEBUG("debug", "Debug output"),
    REALM_NAME("realmName", "The name of a single realm"),
    REALM_NAMES("realmNames", "A comma-separated list of realm names"),
    KEYCLOAK_API_URI("keycloakApiUri", "The keycloak uri, eg keycloak.namespace.cluster.local:8080/auth"),
    KEYCLOAK_TRUSTSTORE_PATH("keycloakTruststorePath", "The path to a truststore when reaching keycloak over tls"),
    KEYCLOAK_TRUSTSTORE_PASSWORD("keycloakTruststorePassword", "The password to the keycloak truststore"),
    KEYCLOAK_HOST_HEADER("keycloakHostHeader", "The keycloak host header. This must match the token issuer, and is required if the api uri is not a public uri"),

    EXPORT_USERS("exportUsers", "Whether to export users as well. This might not fit within a kubernetes secret then."),
    ADMIN_USERNAME("adminUsername", "The keycloak admin username"),
    ADMIN_PASSWORD("adminPassword", "The keycloak admin password"),
    SECRET_NAMESPACE("secretNamespace", "The namespace into which to create/update the secret containing the exported data. The service account running this will need access to read,create,update secrets in that namespace."),
    SECRET_NAME_PATTERN("secretNamePattern", "A pattern used to build the secret name. {0} will be replaced with the realm name. {1} will be replaced with the iso local date. Defaults to '" + KeycloakRealmExportConfigFactory.DEFAULT_SECRET_NAME_PATTERN + ""),
    SECRET_KEY_PATTERN("secretKeyPattern", "A pattern used to build the secret key. {0} will be replaced with the realm nameDefaults to '" + KeycloakRealmExportConfigFactory.DEFAULT_SECRET_KEY_PATTERN + "'"),
    SECRET_LABELS("secretLabels", "A comma-separated list of key:value labels to apply on created secrets"),
    SECRET_ANNOTATIONS("secretAnnotations", "A comma-separated list of key:value annotations to apply on created secrets"),
    ;

    @Getter
    private final String propertyName;
    @Getter
    private final String description;

    ConfigurationProperty(String propertyName, String description) {
        this.propertyName = propertyName;
        this.description = description;
    }

    public static Set<String> getAllPropertyNames() {
        return Arrays.stream(ConfigurationProperty.values())
                .map(ConfigurationProperty::getPropertyName)
                .collect(Collectors.toSet());
    }
}
