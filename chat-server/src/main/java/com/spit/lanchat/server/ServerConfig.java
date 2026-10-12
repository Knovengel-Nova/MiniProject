package com.spit.lanchat.server;

/**
 * Server configuration loaded from environment variables or sensible defaults.
 * Credentials and secrets are kept out of source code.
 */
public class ServerConfig {

    private int port = 8443;
    private boolean tlsEnabled = true;
    private String keystorePath = "certs/server-keystore.p12";
    private String keystorePassword = "changeit";

    private String dbUrl = "jdbc:postgresql://127.0.0.1:5432/lan_chat_server";
    private String dbUser = "postgres";
    private String dbPassword = "postgres";

    private String pdsEndpoint = "http://127.0.0.1:5005/predict";
    private int pdsTimeoutMs = 1500;

    public static ServerConfig load() {
        ServerConfig config = new ServerConfig();

        String portStr = System.getenv("SERVER_PORT");
        if (portStr != null) {
            try { config.setPort(Integer.parseInt(portStr)); } catch (NumberFormatException ignored) {}
        }

        String tlsStr = System.getenv("TLS_ENABLED");
        if (tlsStr != null) {
            config.setTlsEnabled(Boolean.parseBoolean(tlsStr));
        }

        String ksPath = System.getenv("KEYSTORE_PATH");
        if (ksPath != null) {
            config.setKeystorePath(ksPath);
        }

        String ksPass = System.getenv("KEYSTORE_PASSWORD");
        if (ksPass != null) {
            config.setKeystorePassword(ksPass);
        }

        String dbUrlEnv = System.getenv("DB_URL");
        if (dbUrlEnv != null) {
            config.setDbUrl(dbUrlEnv);
        }

        String dbUserEnv = System.getenv("DB_USER");
        if (dbUserEnv != null) {
            config.setDbUser(dbUserEnv);
        }

        String dbPassEnv = System.getenv("DB_PASSWORD");
        if (dbPassEnv != null) {
            config.setDbPassword(dbPassEnv);
        }

        String pdsEnv = System.getenv("PDS_ENDPOINT");
        if (pdsEnv != null) {
            config.setPdsEndpoint(pdsEnv);
        }

        String pdsTimeoutEnv = System.getenv("PDS_TIMEOUT_MS");
        if (pdsTimeoutEnv != null) {
            try { config.setPdsTimeoutMs(Integer.parseInt(pdsTimeoutEnv)); } catch (NumberFormatException ignored) {}
        }

        return config;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public void setTlsEnabled(boolean tlsEnabled) {
        this.tlsEnabled = tlsEnabled;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public void setKeystorePath(String keystorePath) {
        this.keystorePath = keystorePath;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public void setDbUrl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }

    public void setDbUser(String dbUser) {
        this.dbUser = dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }

    public String getPdsEndpoint() {
        return pdsEndpoint;
    }

    public void setPdsEndpoint(String pdsEndpoint) {
        this.pdsEndpoint = pdsEndpoint;
    }

    public int getPdsTimeoutMs() {
        return pdsTimeoutMs;
    }

    public void setPdsTimeoutMs(int pdsTimeoutMs) {
        this.pdsTimeoutMs = pdsTimeoutMs;
    }
}
