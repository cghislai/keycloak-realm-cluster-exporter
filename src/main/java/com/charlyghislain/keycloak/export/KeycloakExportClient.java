package com.charlyghislain.keycloak.export;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KeycloakExportClient {
    private final static Logger LOG = Logger.getLogger(KeycloakExportClient.class.getName());

    private final KeycloakRealmExportConfig exportConfig;
    private final HttpClient httpClient;
    private String accessToken;

    public KeycloakExportClient(KeycloakRealmExportConfig exportConfig) {
        this.exportConfig = exportConfig;
        // Need to override host header so that keycoak matches its issuer
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
        httpClient = createClient();
    }

    public InputStream exportRealm(String realmName) throws IOException, InterruptedException {

        String accessToken = getAccessToken();

        InputStream realmData;
        {
            String query = exportConfig.isExportUsers() ? "?users=true" : "";
            HttpRequest exportRequest = HttpRequest.newBuilder()
                    .GET()
                    .uri(exportConfig.getKeycloakApiUri().resolve("realms/" + realmName + "/importexport/realm" + query))
                    .setHeader("accept", "application/json")
                    .setHeader("authorization", "bearer " + accessToken)
                    .setHeader("host", exportConfig.getKeycloakHostname())
                    .build();
            if (exportConfig.isDebug()) {
                LOG.log(Level.FINER, "> " + exportRequest.method() + " " + exportRequest.uri());
            }

            HttpResponse<InputStream> exportResponse = httpClient.send(exportRequest, HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = exportResponse.statusCode();
            this.debugResponse(exportResponse);

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

    private String getAccessToken() throws IOException, InterruptedException {
        if (this.accessToken != null) {
            return this.accessToken;
        }

        Map<String, String> formData = new HashMap<>();
        formData.put("client_id", "admin-cli");
        formData.put("grant_type", "password");
        formData.put("username", exportConfig.getAdminUser());
        formData.put("password", exportConfig.getAdminPassword());
        String encodedFormData = encodeBodyFormData(formData);

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
        this.debugResponse(authResponse);
        if (status != 200) {
            throw new RuntimeException("Unable to authenticate to keycloak :" + responseBody);
        }
        JsonReader reader = Json.createReader(new StringReader(responseBody));
        JsonObject tokenJsonObject = reader.readObject();
        this.accessToken = tokenJsonObject.getString("access_token");
        return this.accessToken;
    }

    private HttpClient createClient() {
        Path keycloakTrustStorePath = exportConfig.getKeycloakTrustStorePath();
        if (keycloakTrustStorePath == null) {
            return HttpClient.newHttpClient();
        }

        String trustStorePassword = Optional.ofNullable(exportConfig.getKeycloakTrustStorePassword())
                .orElseThrow(() -> new RuntimeException("No truststore password provided"));

        String keyStoreType = keycloakTrustStorePath.getFileName().toString().endsWith("p12") ? "pkcs12" : "jks";
        try (InputStream trustStoreStream = Files.newInputStream(keycloakTrustStorePath)) {
            KeyStore trustStore = KeyStore.getInstance(keyStoreType);
            trustStore.load(trustStoreStream, trustStorePassword.toCharArray());
            String trustManagerFactoryAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(trustManagerFactoryAlgorithm);
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(
                    null,
                    trustManagerFactory.getTrustManagers(),
                    null
            );

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException | KeyManagementException e) {
            throw new RuntimeException("Unable to read truststore at " + keycloakTrustStorePath + ": " + e.getMessage(), e);
        }
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


    private <T> HttpResponse<T> debugResponse(HttpResponse<T> httpResponse) {
        Optional<HttpRequest.BodyPublisher> bodyPublisher = httpResponse.request().bodyPublisher();
        return debugResponse(httpResponse, bodyPublisher);
    }


    private <T> HttpResponse<T> debugResponse(HttpResponse<T> httpResponse, Optional<HttpRequest.BodyPublisher> originalRequestBody) {
        if (!exportConfig.isDebug()) {
            return httpResponse;
        }
        HttpRequest request = httpResponse.request();

        httpResponse.previousResponse()
                .ifPresent(previousResponse -> debugResponse(previousResponse, originalRequestBody));


        try {
            ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
            PrintStream debugOutputStream = new PrintStream(responseStream);
            debugResponseToStream(httpResponse, originalRequestBody, request, debugOutputStream);
            responseStream.close();
            byte[] responseBytes = responseStream.toByteArray();
            String responseDEbugString = new String(responseBytes);
            LOG.fine(responseDEbugString);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Unable to debug response: " + e.getMessage());
        }

        return httpResponse;
    }

    private <T> void debugResponseToStream(HttpResponse<T> httpResponse, Optional<HttpRequest.BodyPublisher> originalRequestBody,
                                           HttpRequest request, PrintStream outStream) {
        String method = request.method();
        String uri = request.uri().toString();
        outStream.println("> " + method + " " + uri);
        String requestHeaders = request.headers().map()
                .entrySet()
                .stream()
                .map(requestHeaderMapEntry -> requestHeaderMapEntry.getKey() + ": " + requestHeaderMapEntry.getValue() + "\n")
                .reduce("", String::concat);
        outStream.println(requestHeaders);

        boolean originalRequest = httpResponse.previousResponse()
                .isEmpty();
        if (originalRequest) {
            originalRequestBody.ifPresent(bodyPublisher -> logRequestBody(bodyPublisher, outStream));
        }

        outStream.println("< " + httpResponse.statusCode());
        HttpHeaders httpHeaders = httpResponse.headers();
        String headers = httpHeaders.map()
                .entrySet()
                .stream()
                .map(responseHeaderMapEntry -> responseHeaderMapEntry.getKey() + ": " + responseHeaderMapEntry.getValue() + "\n")
                .reduce("", String::concat);
        outStream.println(headers);

        outStream.println("---start response body---");
        outStream.println(httpResponse.body());
    }

    private void logRequestBody(HttpRequest.BodyPublisher bodyPublisher, PrintStream outStream) {
        HttpResponse.BodySubscriber<String> stringBodySubscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        DebugBodySubscriber stringSubscriber = new DebugBodySubscriber(stringBodySubscriber);
        bodyPublisher.subscribe(stringSubscriber);
        String bodyString = stringBodySubscriber.getBody()
                .toCompletableFuture()
                .join();
        int index = 0;
        int lineWidth = 128;
        outStream.println("POST data:");
        while (index + lineWidth <= bodyString.length()) {
            String line = bodyString.substring(index, index + lineWidth);
            outStream.println(line);
            index += lineWidth;
        }
        String lastLine = bodyString.substring(index);
        outStream.println(lastLine);
        outStream.println();
    }

}
