package be.valuya.keycloak.export;

import java.net.URI;
import java.util.Set;

public class KeycloakRealmExportConfig {

    private Set<String> realmNames;
    private URI keycloakApiUri;
    private String adminUser;
    private String adminPassword;

    private String secretNamespace;
    private String secretNamePattern;

    private boolean debug;

    public Set<String> getRealmNames() {
        return realmNames;
    }

    public void setRealmNames(Set<String> realmNames) {
        this.realmNames = realmNames;
    }

    public URI getKeycloakApiUri() {
        return keycloakApiUri;
    }

    public void setKeycloakApiUri(URI keycloakApiUri) {
        this.keycloakApiUri = keycloakApiUri;
    }

    public String getAdminUser() {
        return adminUser;
    }

    public void setAdminUser(String adminUser) {
        this.adminUser = adminUser;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getSecretNamespace() {
        return secretNamespace;
    }

    public void setSecretNamespace(String secretNamespace) {
        this.secretNamespace = secretNamespace;
    }

    public String getSecretNamePattern() {
        return secretNamePattern;
    }

    public void setSecretNamePattern(String secretNamePattern) {
        this.secretNamePattern = secretNamePattern;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public String toString() {
        return "KeycloakRealmExportConfig{" +
                "realmNames=" + realmNames +
                ", keycloakApiUri=" + keycloakApiUri +
                ", adminUser='" + adminUser + '\'' +
                ", adminPassword='" + adminPassword + '\'' +
                ", secretNamespace='" + secretNamespace + '\'' +
                ", secretNamePattern='" + secretNamePattern + '\'' +
                ", debug=" + debug +
                '}';
    }
}
