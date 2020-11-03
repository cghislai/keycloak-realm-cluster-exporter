package be.valuya.keycloak.export;

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

public class KeycloakRealmExportConfigFactory {
    private final static Logger LOG = Logger.getLogger(KeycloakRealmExportConfigFactory.class.getName());

    public static final String SECRETS_PATH = "/var/run/secrets";
    public static final String DEFAULT_NAMESPACE = "default";
    public static final String DEFAULT_NAME_PATTERN = "realm-{0}-json-export-{1}-secret";

    public static final String PROP_REALM = "realm";
    public static final String PROP_REALMS = "realms";
    public static final String PROP_URI = "uri";
    public static final String PROP_ADMIN_USERNAME = "username";
    public static final String PROP_ADMIN_PASSWORD = "password";
    public static final String PROP_SECRET_NAMESPACE = "secretnamespace";
    public static final String PROP_SECRET_NAME_PATTERN = "secretpattern";

    private final static Set<String> allProperties = Set.of(
            PROP_REALM, PROP_REALMS, PROP_URI, PROP_ADMIN_USERNAME, PROP_ADMIN_PASSWORD,
            PROP_SECRET_NAMESPACE, PROP_SECRET_NAME_PATTERN
    );

    public static KeycloakRealmExportConfig createConfig(String[] args) {
        KeycloakRealmExportConfig exportConfig = new KeycloakRealmExportConfig();

        Map<String, String> properties = new HashMap<>();
        loadPropertiesFromEnv(properties);
        loadPropertiesFromSecrets(properties);
        loadPropertiesFromArguments(properties, args);

        Set<String> realms = new HashSet<>();
        Optional.ofNullable(properties.get(PROP_REALM))
                .filter(s -> !s.isBlank())
                .ifPresent(realms::add);
        Optional.ofNullable(properties.get(PROP_REALMS))
                .map(s -> s.split(","))
                .stream()
                .flatMap(Arrays::stream)
                .filter(s -> !s.isBlank())
                .forEach(realms::add);
        if (realms.isEmpty()) {
            throw new RuntimeException("No realm");
        }
        exportConfig.setRealmNames(realms);

        String uri = Optional.ofNullable(properties.get(PROP_URI))
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new RuntimeException("No api uri"));
        URI apiUri;
        try {
            apiUri = new URI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid uri", e);
        }
        exportConfig.setKeycloakApiUri(apiUri);

        String user = Optional.ofNullable(properties.get(PROP_ADMIN_USERNAME))
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new RuntimeException("No admin username"));
        exportConfig.setAdminUser(user);

        String adminPassword = Optional.ofNullable(properties.get(PROP_ADMIN_PASSWORD))
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new RuntimeException("No admin password"));
        exportConfig.setAdminPassword(adminPassword);

        String secretnamespace = Optional.ofNullable(properties.get(PROP_SECRET_NAMESPACE))
                .orElse(DEFAULT_NAMESPACE);
        exportConfig.setSecretNamespace(secretnamespace);

        String secretNamePattern = Optional.ofNullable(properties.get(PROP_SECRET_NAME_PATTERN))
                .orElse(DEFAULT_NAME_PATTERN);
        exportConfig.setSecretNamePattern(secretNamePattern);

        return exportConfig;
    }

    private static void loadPropertiesFromArguments(Map<String, String> properties, String[] args) {
        Arrays.asList(args).stream()
                .forEach(a -> loadPropertyFromArg(properties, a));
    }

    private static void loadPropertyFromArg(Map<String, String> properties, String arg) {
        String[] argParts = arg.split("=");
        if (argParts.length == 2) {
            String key = argParts[0];
            String value = argParts[1];
            if (allProperties.contains(key)) {
                loadProperty(properties, key, value);
            }
        }
    }

    private static void loadPropertiesFromSecrets(Map<String, String> properties) {
        allProperties.stream()
                .forEach(k -> loadPropertySecret(properties, k));
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
        allProperties.forEach(p -> loadProperty(properties, p, System.getenv(p)));
    }

    private static void loadProperty(Map<String, String> properties, String propName, String propValueNullable) {
        Optional.ofNullable(propValueNullable)
                .ifPresent(v -> properties.put(propName, v));
    }
}
