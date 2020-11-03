package be.valuya.keycloak.export;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KeycloakExportClient {
    private final static Logger LOG = Logger.getLogger(KeycloakExportClient.class.getName());

    private KeycloakRealmExportConfig exportConfig;

    public KeycloakExportClient(KeycloakRealmExportConfig exportConfig) {
        this.exportConfig = exportConfig;
    }

    public InputStream exportRealm(String realmName) throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();

        Map<String, String> formData = new HashMap<>();
        formData.put("client_id", "admin-cli");
        formData.put("grant_type", "password");
        formData.put("username", exportConfig.getAdminUser());
        formData.put("password", exportConfig.getAdminPassword());
        String encodedFormData = encodeBodyFormData(formData);

        String accessToken;
        {
            HttpRequest authRequest = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(encodedFormData))
                    .uri(exportConfig.getKeycloakApiUri().resolve("realms/master/protocol/openid-connect/token"))
                    .headers("content-type", "application/x-www-form-urlencoded")
                    .header("accept", "application/json")
                    .build();
            if (exportConfig.isDebug()) {
                LOG.log(Level.FINER, "> " + authRequest.method() + " " + authRequest.uri());
                LOG.log(Level.FINER, encodedFormData);
            }
            HttpResponse<String> authResponse = httpClient.send(authRequest, HttpResponse.BodyHandlers.ofString());
            int status = authResponse.statusCode();
            String responseBody = authResponse.body();
            if (exportConfig.isDebug()) {
                LOG.log(Level.FINER, "< " + status + " " + responseBody);
            }
            if (status != 200) {
                throw new RuntimeException("Unable to authenticate to keycloak :" + responseBody);
            }
            JsonReader reader = Json.createReader(new StringReader(responseBody));
            JsonObject tokenJsonObject = reader.readObject();
            accessToken = tokenJsonObject.getString("access_token");
        }

        Thread.sleep(2000);

        InputStream realmData;
        {
            HttpRequest exportRequest = HttpRequest.newBuilder()
                    .GET()
                    .uri(exportConfig.getKeycloakApiUri().resolve("realms/" + realmName + "/importexport/realm"))
                    .header("accept", "application/json")
                    .header("authorization", "bearer " + accessToken)
                    .build();
            if (exportConfig.isDebug()) {
                LOG.log(Level.FINER, "> " + exportRequest.method() + " " + exportRequest.uri());
                LOG.log(Level.FINER, "authorization: bearer " + accessToken);
            }

            HttpResponse<InputStream> exportResponse = httpClient.send(exportRequest, HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = exportResponse.statusCode();
            if (exportConfig.isDebug()) {
                LOG.log(Level.FINER, "< " + statusCode);
            }

            if (statusCode != 200) {
                InputStream exportBodyData = exportResponse.body();
                byte[] exportBodyBytes = exportBodyData.readAllBytes();
                String exportBodyString = new String(exportBodyBytes);
                throw new RuntimeException("Unable to export realm " + realmName + ": http " + statusCode + " : " + exportBodyString);
            }
            realmData = exportResponse.body();
        }
        return realmData;
    }


    private String encodeBodyFormData(Map<String, String> valueMap) {
        return valueMap.entrySet()
                .stream()
                .map(this::encodeEntry)
                .reduce("", (a, b) -> {
                    if (a.isEmpty()) {
                        return b;
                    } else {
                        return a + "&" + b;
                    }
                });
    }


    private String encodeEntry(Map.Entry<?, ?> entry) {
        Object value = entry.getValue();
        Object key = entry.getKey();
        String encodedKey = URLEncoder.encode(key.toString(), StandardCharsets.UTF_8);
        String encodedValue = URLEncoder.encode(value.toString(), StandardCharsets.UTF_8);
        return encodedKey + "=" + encodedValue;
    }
}