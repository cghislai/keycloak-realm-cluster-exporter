package com.charlyghislain.keycloak.export;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class KeycloakRealmExportConfigFactory {
    private final static Logger LOG = Logger.getLogger(KeycloakRealmExportConfigFactory.class.getName());

    public static final String SECRETS_PATH = "/var/run/secrets";
    public static final String DEFAULT_SECRET_NAME_PATTERN = "realm-{0}-json-export-{1}-secret";
    public static final String DEFAULT_SECRET_KEY_PATTERN = "{0}.json";

    public static Map<String, String> resolvePropertiesMap(String[] args) {
        Map<String, String> properties = new HashMap<>();
        loadPropertiesFromSecrets(properties);
        loadPropertiesFromEnv(properties);
        loadPropertiesFromArguments(properties, args);
        return properties;
    }

    public static KeycloakRealmExportConfig createConfig(Map<String, String> properties) {
        KeycloakRealmExportConfig exportConfig = new KeycloakRealmExportConfig();

        Set<String> realms = new HashSet<>();
        Optional.ofNullable(properties.get(ConfigurationProperty.REALM_NAME.getPropertyName()))
                .filter(s -> !s.isBlank())
                .ifPresent(realms::add);
        Optional.ofNullable(properties.get(ConfigurationProperty.REALM_NAMES.getPropertyName()))
                .map(s -> s.split(","))
                .stream()
                .flatMap(Arrays::stream)
                .filter(s -> !s.isBlank())
                .forEach(realms::add);
        if (realms.isEmpty()) {
            throw new RuntimeException("No realm configured");
        }
        exportConfig.setRealmNames(realms);

        String keycloakApiUri = Optional.ofNullable(properties.get(ConfigurationProperty.KEYCLOAK_API_URI.getPropertyName()))
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new RuntimeException("No keycloak api uri configured"));
        URI apiUri;
        try {
            apiUri = new URI(keycloakApiUri);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid keycloak api uri", e);
        }
        exportConfig.setKeycloakApiUri(apiUri);

        String host = Optional.ofNullable(properties.get(ConfigurationProperty.KEYCLOAK_HOST_HEADER.getPropertyName()))
                .orElseGet(apiUri::getHost);
        exportConfig.setKeycloakHostname(host);

        String user = Optional.ofNullable(properties.get(ConfigurationProperty.ADMIN_USERNAME.getPropertyName()))
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new RuntimeException("No keycloak admin username configured"));
        exportConfig.setAdminUser(user);

        String adminPassword = Optional.ofNullable(properties.get(ConfigurationProperty.ADMIN_PASSWORD.getPropertyName()))
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new RuntimeException("No admin password configured"));
        exportConfig.setAdminPassword(adminPassword);

        String secretnamespace = Optional.ofNullable(properties.get(ConfigurationProperty.SECRET_NAMESPACE.getPropertyName()))
                .orElseThrow(() -> new RuntimeException("No secret namespace configured"));
        exportConfig.setSecretNamespace(secretnamespace);

        String secretNamePattern = Optional.ofNullable(properties.get(ConfigurationProperty.SECRET_NAME_PATTERN.getPropertyName()))
                .orElse(DEFAULT_SECRET_NAME_PATTERN);
        exportConfig.setSecretNamePattern(secretNamePattern);

        String secretKeyPattern = Optional.ofNullable(properties.get(ConfigurationProperty.SECRET_KEY_PATTERN.getPropertyName()))
                .orElse(DEFAULT_SECRET_KEY_PATTERN);
        exportConfig.setSecretKeyPattern(secretKeyPattern);

        Map<String, String> labelsMap = Optional.ofNullable(properties.get(ConfigurationProperty.SECRET_LABELS.getPropertyName()))
                .map(s -> s.split(","))
                .stream()
                .flatMap(Arrays::stream)
                .map(p -> p.split(":"))
                .filter(p -> p.length == 2 && !p[0].isBlank())
                .collect(Collectors.toMap(
                        p -> p[0].strip(),
                        p -> p[1].strip()
                ));
        exportConfig.setSecretLabels(labelsMap);

        Map<String, String> annotationsMap = Optional.ofNullable(properties.get(ConfigurationProperty.SECRET_ANNOTATIONS.getPropertyName()))
                .map(s -> s.split(","))
                .stream()
                .flatMap(Arrays::stream)
                .map(p -> p.split(":"))
                .filter(p -> p.length == 2 && !p[0].isBlank())
                .collect(Collectors.toMap(
                        p -> p[0].strip(),
                        p -> p[1].strip()
                ));
        exportConfig.setSecretAnnotations(annotationsMap);

        boolean debug = Optional.ofNullable(properties.get(ConfigurationProperty.DEBUG.getPropertyName()))
                .filter(s -> !s.isBlank())
                .map(Boolean::parseBoolean)
                .orElse(properties.containsKey(ConfigurationProperty.DEBUG.getPropertyName()));
        exportConfig.setDebug(debug);

        boolean exportUsers = Optional.ofNullable(properties.get(ConfigurationProperty.EXPORT_USERS.getPropertyName()))
                .filter(s -> !s.isBlank())
                .map(Boolean::parseBoolean)
                .orElse(properties.containsKey(ConfigurationProperty.EXPORT_USERS.getPropertyName()));
        exportConfig.setExportUsers(exportUsers);

        return exportConfig;
    }

    private static void loadPropertiesFromArguments(Map<String, String> properties, String[] args) {
        Arrays.stream(args).forEach(a -> loadPropertyFromArg(properties, a));
    }

    private static void loadPropertyFromArg(Map<String, String> properties, String arg) {
        String[] argParts = arg.split("=");
        Set<String> allPropertyNames = ConfigurationProperty.getAllPropertyNames();
        if (argParts.length == 2) {
            String key = argParts[0];
            String value = argParts[1];
            if (allPropertyNames.contains(key)) {
                loadProperty(properties, key, value);
            }
        } else if (allPropertyNames.contains(arg)) {
            loadProperty(properties, arg, "");
        }
    }

    private static void loadPropertiesFromSecrets(Map<String, String> properties) {
        Set<String> allPropertyNames = ConfigurationProperty.getAllPropertyNames();
        allPropertyNames.forEach(k -> loadPropertySecret(properties, k));
    }

    private static void loadPropertySecret(Map<String, String> properties, String propName) {
        Path secretPath = getSecretPath(propName);
        if (Files.exists(secretPath) && Files.isReadable(secretPath)) {
            try {
                List<String> allLines = Files.readAllLines(secretPath);
                if (allLines.size() > 0) {
                    String secretLine = allLines.get(0);
                    loadProperty(properties, propName, secretLine);
                }
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Unable to loac secret at " + secretPath, e);
            }
        }
    }

    private static Path getSecretPath(String propName) {
        return Paths.get(SECRETS_PATH).resolve(propName);
    }

    private static void loadPropertiesFromEnv(Map<String, String> properties) {
        Set<String> allPropertyNames = ConfigurationProperty.getAllPropertyNames();
        allPropertyNames.forEach(p -> loadProperty(properties, p, System.getenv(p)));
    }

    private static void loadProperty(Map<String, String> properties, String propName, String propValueNullable) {
        Optional.ofNullable(propValueNullable)
                .ifPresent(v -> properties.put(propName, v));
    }
}
