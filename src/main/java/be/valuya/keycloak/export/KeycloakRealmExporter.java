package be.valuya.keycloak.export;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class KeycloakRealmExporter {
    private final static Logger LOG = Logger.getLogger(KeycloakRealmExporter.class.getName());

    public static void main(String[] args) {
        tryReadLoggingConfig();
        KeycloakRealmExportConfig config = KeycloakRealmExportConfigFactory.createConfig(args);

        KeycloakExportClient exportClient = new KeycloakExportClient(config);
        KubernetesClient kubernetesClient = new KubernetesClient(config);

        Set<String> realmNames = config.getRealmNames();
        boolean hasFailure = false;
        for (String realm : realmNames) {
            boolean success = exportRealm(exportClient, kubernetesClient, realm);
            if (!success) {
                hasFailure = true;
            }
        }

        System.exit(hasFailure ? 1 : 0);
    }

    private static void tryReadLoggingConfig() {
        try {
            InputStream logPropsFile = KeycloakRealmExporter.class.getClassLoader().getResourceAsStream("logging.properties");
            LogManager.getLogManager().readConfiguration(logPropsFile);
        } catch (Exception var14) {
            LOG.log(Level.WARNING, "Unable to load logging config");
        }
    }

    private static boolean exportRealm(KeycloakExportClient exportClient, KubernetesClient kubernetesClient, String realm) {
        LOG.log(Level.INFO, "Exporting " + realm);

        InputStream realmData;
        try {
            realmData = exportClient.exportRealm(realm);
        } catch (InterruptedException | IOException e) {
            LOG.log(Level.SEVERE, "Unable to load realm data", e);
            return false;
        }

        try {
            kubernetesClient.persistRealmData(realm, realmData);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Unable to save realm data", e);
            return false;
        }

        LOG.log(Level.INFO, realm + " successfully exported");
        return true;
    }

}
