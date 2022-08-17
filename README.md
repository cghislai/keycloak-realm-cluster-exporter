# Keycloak realm cluster exporter

Exports a keycloak realm as json into a kubernetes secret.

This requires an export REST resource to be exposed on keycloak. See https://github.com/cghislai/keycloak-importexport.

## Configuration

Configuration values are obtained from different sources, in order:

- files loaded from /var/run/secrets/
- env variables
- arguments as 'key=value'

Available configuration properties:

| Name  | Description                                                                                                                                                                            |
|:-----|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| help| Display help output and exits.                                                                                                                                                         |
| debug| Debug output                                                                                                                                                                           |
| realmName| The name of a single realm                                                                                                                                                             |
| realmNames| A comma-separated list of realm names                                                                                                                                                  |
| keycloakApiUri| The keycloak uri, eg keycloak.namespace.cluster.local:8080/auth                                                                                                                        |
| keycloakHostHeader| The keycloak host header. This must match the token issuer, and is required if the api uri is not a public uri                                                                         |
| keycloakTruststorePath| The path to a truststore when reaching keycloak over tls                                                                                                                               |
| keycloakTruststorePassword| The password to the keycloak truststore                                                                                                                                                |
| exportUsers| Whether to export users as well. This might not fit within a kubernetes secret then.                                                                                                   |
| adminUsername| The keycloak admin username                                                                                                                                                            |
| adminPassword| The keycloak admin password                                                                                                                                                            |
| secretNamespace| The namespace into which to create/update the secret containing the exported data. The service account running this will need access to  read,create,update secrets in that namespace. |
| secretNamePattern| A pattern used to build the secret name. {0} will be replaced with the realm name. {1} will be replaced with the iso local date. Defaults  to 'realm-{0}-json-export-{1}-secret        |
| secretKeyPattern| A pattern used to build the secret key. {0} will be replaced with the realm nameDefaults to '{0}.json'                                                                                 |
| secretLabels| A comma-separated list of key:value labels to apply on created secrets                                                                                                                 |
| secretAnnotations| A comma-separated list of key:value annotations to apply on created secrets                                                                                                            |
