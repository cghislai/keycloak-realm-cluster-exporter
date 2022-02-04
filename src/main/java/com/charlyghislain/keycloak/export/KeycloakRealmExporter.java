package com.charlyghislain.keycloak.export;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class KeycloakRealmExporter {

    private final static Logger LOG = Logger.getLogger(KeycloakRealmExporter.class.getName());

    public static void main(String[] args) {

        Map<String, String> configurationProperties = KeycloakRealmExportConfigFactory.resolvePropertiesMap(args);
        if (configurationProperties.containsKey(ConfigurationProperty.HELP.getPropertyName())) {
            printHelp();
            System.exit(0);
        }

        KeycloakRealmExportConfig config = KeycloakRealmExportConfigFactory.createConfig(configurationProperties);
        tryReadLoggingConfig(config);
        if (config.isDebug()) {
            LOG.log(Level.FINER, "Config: " + config.toString());
        }

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

    private static void printHelp() {
        System.out.println("");
        System.out.println("Keycloak realm cluster exporter");
        System.out.println("Properties loaded from: ");
        System.out.println(" - files located in path: " + KeycloakRealmExportConfigFactory.SECRETS_PATH);
        System.out.println(" - env variables");
        System.out.println(" - key=value run arguments");
        System.out.println("");
        System.out.println("Available configuration properties:");
        System.out.println("");
        Arrays.stream(ConfigurationProperty.values())
                .forEach(p -> {
                    System.out.println(p.getPropertyName() + ": \t" + p.getDescription());
                });
    }

    private static void tryReadLoggingConfig(KeycloakRealmExportConfig config) {
        try {
            boolean debug = config.isDebug();
            String loggingPropertiesFileName = debug ? "logging.debug.properties" : "logging.properties";
            InputStream logPropsFile = KeycloakRealmExporter.class.getClassLoader().getResourceAsStream(loggingPropertiesFileName);
            LogManager.getLogManager().readConfiguration(logPropsFile);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Unable to load logging config");
        }
    }

    private static boolean exportRealm(KeycloakExportClient exportClient, KubernetesClient kubernetesClient, String realm) {
        LOG.log(Level.INFO, "Exporting " + realm);

        InputStream realmData;
        try {
            realmData = exportClient.exportRealm(realm);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unable to load realm data", e);
            return false;
        }

        try {
            kubernetesClient.persistRealmData(realm, realmData);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unable to save realm data", e);
            return false;
        }

        LOG.log(Level.INFO, realm + " successfully exported");
        return true;
    }

}
