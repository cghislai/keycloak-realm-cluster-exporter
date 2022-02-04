package com.charlyghislain.keycloak.export;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.ClientBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KubernetesClient {

    private final static Logger LOG = Logger.getLogger(KubernetesClient.class.getName());

    private KeycloakRealmExportConfig exportConfig;

    public KubernetesClient(KeycloakRealmExportConfig exportConfig) {
        this.exportConfig = exportConfig;
    }


    public void persistRealmData(String realm, InputStream data) throws IOException {
        byte[] dataBytes = data.readAllBytes();

        // loading the in-cluster config, including:
        //   1. service-account CA
        //   2. service-account bearer-token
        //   3. service-account namespace
        //   4. master endpoints(ip, port) from pre-set environment variables
        ApiClient client = ClientBuilder.cluster().build();
        if (exportConfig.isDebug()) {
            client.setDebugging(true);
        }

        // if you prefer not to refresh service account token, please use:
        // ApiClient client = ClientBuilder.oldCluster().build();

        // set the global default api-client to the in-cluster one from above
        Configuration.setDefaultApiClient(client);

        // the CoreV1Api loads default api-client from global configuration.
        CoreV1Api api = new CoreV1Api();

        String secretNamespace = exportConfig.getSecretNamespace();
        String secretName = createNameSpaceSecretName(realm);
        V1Secret existingSecret;
        try {
            existingSecret = api.readNamespacedSecret(secretName, secretNamespace, null);
        } catch (ApiException e) {
            LOG.log(Level.FINE, "HTTP error to get secret named " + secretName + ": " + e.getCode());
            existingSecret = null;
        }

        V1ObjectMeta metadata = new V1ObjectMeta();
        metadata.setName(secretName);
        metadata.setNamespace(secretNamespace);
        V1Secret newSecret = new V1Secret();
        newSecret.setApiVersion("v1");
        String secretKey = realm + ".json";
        newSecret.setData(Map.of(secretKey, dataBytes));
        newSecret.setKind("Secret");
        newSecret.setType("Opaque");
        newSecret.setMetadata(metadata);

        V1Secret updatedSecret;
        try {
            if (existingSecret == null) {
                updatedSecret = api.createNamespacedSecret(secretNamespace, newSecret, null, null, null);
            } else {
                updatedSecret = api.replaceNamespacedSecret(secretName, secretNamespace, newSecret, null, null, null);
            }
        } catch (ApiException e) {
            throw new RuntimeException("Unable to create/replace secret " + secretName, e);
        }
        LOG.log(Level.FINE, "Created/replaced secret " + secretName + " with " + dataBytes.length + " bytes in key" + secretKey);
        if (exportConfig.isDebug()) {
            String realmJson = new String(dataBytes, StandardCharsets.UTF_8);
            LOG.log(Level.FINER, "Updated secret: " + updatedSecret);
            LOG.log(Level.FINER, realmJson);
        }
    }

    private String createNameSpaceSecretName(String realm) {
        String secretNamePattern = exportConfig.getSecretNamePattern();
        String formattedDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String secretName = MessageFormat.format(secretNamePattern, realm, formattedDate);
        return secretName;
    }

}

