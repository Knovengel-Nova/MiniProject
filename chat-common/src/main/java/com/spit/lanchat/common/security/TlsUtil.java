package com.spit.lanchat.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Utility for configuring and initializing TLS/SSL contexts for Server and Client TCP communication.
 */
public final class TlsUtil {

    private static final Logger logger = LoggerFactory.getLogger(TlsUtil.class);

    private TlsUtil() {}

    /**
     * Creates an SSLContext for the Server from a PKCS12 KeyStore.
     */
    public static SSLContext createServerSSLContext(String keystorePath, String password) throws Exception {
        File file = new File(keystorePath);
        if (!file.exists()) {
            File parentFile = new File("..", keystorePath);
            if (parentFile.exists()) {
                file = parentFile;
            } else {
                throw new IllegalArgumentException("Keystore file not found: " + keystorePath);
            }
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(file)) {
            keyStore.load(in, password.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());
        return sslContext;
    }

    /**
     * Creates an SSLContext for the Client from a PKCS12 TrustStore.
     */
    public static SSLContext createClientSSLContext(String truststorePath, String password) throws Exception {
        File file = new File(truststorePath);
        if (!file.exists()) {
            File parentFile = new File("..", truststorePath);
            if (parentFile.exists()) {
                file = parentFile;
            } else {
                throw new IllegalArgumentException("Truststore file not found: " + truststorePath);
            }
        }

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(file)) {
            trustStore.load(in, password.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    /**
     * Creates a self-contained testing SSLContext that trusts all certificates
     * for automated local integration tests where certificate files may not be in relative path.
     */
    public static SSLContext createTestingSSLContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            return sc;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create testing SSLContext", e);
        }
    }
}
