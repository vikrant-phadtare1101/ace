/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.agent.impl;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.ace.agent.ConnectionHandler;
import org.apache.commons.codec.binary.Base64;

/**
 * Default connection handler with support for BASIC authentication and HTTPS client certificates.
 * 
 */
public class ConnectionHandlerImpl extends ComponentBase implements ConnectionHandler {

    public static final String COMPONENT_IDENTIFIER = "connection";
    public static final String CONFIG_KEY_BASE = ConfigurationHandlerImpl.CONFIG_KEY_NAMESPACE + "." + COMPONENT_IDENTIFIER;

    public static final String PROP_AUTHTYPE = "agent.authType";
    public static final String PROP_AUTHUSER = "agent.authUser";
    public static final String PROP_AUTHPASS = "agent.authPass";
    public static final String PROP_AUTHKEYFILE = "agent.authKeyFile";
    public static final String PROP_AUTHKEYPASS = "agent.authKeyPass";
    public static final String PROP_AUTHTRUSTFILE = "agent.authTrustFile";
    public static final String PROP_AUTHTRUSTPASS = "agent.authTrustPass";

    private static final String HTTP_HEADER_AUTHORIZATION = "Authorization";

    private enum AuthType {

        NONE,
        BASIC,
        CLIENT_CERT;

        static AuthType getAuthType(String name) {
            if (name.equals(NONE.name()))
                return NONE;
            if (name.equals(BASIC.name()))
                return BASIC;
            if (name.equals(CLIENT_CERT.name()))
                return CLIENT_CERT;
            return null;
        }
    }

    private static class UrlCredentials {

        final static UrlCredentials EMPTY_CREDENTIALS = new UrlCredentials(AuthType.NONE, new Object[0]);

        private final AuthType m_type;
        private final Object[] m_credentials;

        public UrlCredentials(AuthType type, Object... credentials) {
            m_type = type;
            m_credentials = (credentials == null) ? new Object[0] : credentials.clone();
        }

        public Object[] getCredentials() {
            return m_credentials.clone();
        }

        public AuthType getType() {
            return m_type;
        }
    }

    public ConnectionHandlerImpl() {
        super(COMPONENT_IDENTIFIER);
    }

    @Override
    public URLConnection getConnection(URL url) throws IOException {
        URLConnection connection = (HttpURLConnection) url.openConnection();
        UrlCredentials credentials = getCredentials();
        if (credentials != null) {
            if (credentials != null && credentials.getType() == AuthType.BASIC)
                applyBasicAuthentication(connection, credentials.getCredentials());

            else if (credentials != null && credentials.getType() == AuthType.CLIENT_CERT) {
                applyClientCertificate(connection, credentials.getCredentials());
            }
        }
        return connection;
    }

    private final String getBasicAuthCredentials(Object[] values) {
        if ((values == null) || values.length < 2) {
            throw new IllegalArgumentException("Insufficient credentials passed: expected 2 values!");
        }

        StringBuilder sb = new StringBuilder();
        if (values[0] instanceof String) {
            sb.append((String) values[0]);
        }
        else if (values[0] instanceof byte[]) {
            sb.append(new String((byte[]) values[0]));
        }
        sb.append(':');
        if (values[1] instanceof String) {
            sb.append((String) values[1]);
        }
        else if (values[1] instanceof byte[]) {
            sb.append(new String((byte[]) values[1]));
        }

        return "Basic " + new String(Base64.encodeBase64(sb.toString().getBytes()));
    }

    private void applyBasicAuthentication(URLConnection conn, Object[] values) {
        if (conn instanceof HttpURLConnection) {
            conn.setRequestProperty(HTTP_HEADER_AUTHORIZATION, getBasicAuthCredentials(values));
        }
    }

    private void applyClientCertificate(URLConnection conn, Object[] values) {
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(((SSLContext) values[0]).getSocketFactory());
        }
    }

    public UrlCredentials getCredentials() {

        String configValue = getConfigStringValue(PROP_AUTHTYPE);
        AuthType authType = AuthType.getAuthType(configValue == null ? "" : configValue.trim().toUpperCase());
        if (authType == null || authType == AuthType.NONE) {
            return UrlCredentials.EMPTY_CREDENTIALS;
        }

        if (authType == AuthType.BASIC) {
            String username = getConfigStringValue(PROP_AUTHUSER);
            String password = getConfigStringValue(PROP_AUTHPASS);
            return new UrlCredentials(AuthType.BASIC,
                new Object[] { username == null ? "" : username, password == null ? "" : password });
        }

        if (authType == AuthType.CLIENT_CERT) {
            String keystoreFile = getConfigStringValue(PROP_AUTHKEYFILE);
            String keystorePass = getConfigStringValue(PROP_AUTHKEYPASS);
            String truststoreFile = getConfigStringValue(PROP_AUTHTRUSTFILE);
            String truststorePass = getConfigStringValue(PROP_AUTHTRUSTPASS);

            // TODO This is expensive. Can we cache?
            try {
                KeyManager[] keyManagers = getKeyManagerFactory(keystoreFile, keystorePass);
                TrustManager[] trustManagers = getTrustManagerFactory(truststoreFile, truststorePass);
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(keyManagers, trustManagers, new SecureRandom());
                return new UrlCredentials(AuthType.CLIENT_CERT,
                    new Object[] { context });
            }
            catch (Exception e) {
                // TODO log
            }
        }
        return null;
    }

    private String getConfigStringValue(String key) {
        return getAgentContext().getConfigurationHandler().get(key, null);
    }

    private static KeyManager[] getKeyManagerFactory(String keystoreFile, String storePass) throws IOException, GeneralSecurityException {
        InputStream keyInput = null;
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyInput = new FileInputStream(keystoreFile);
            keyStore.load(keyInput, storePass.toCharArray());
            keyManagerFactory.init(keyStore, storePass.toCharArray());
            return keyManagerFactory.getKeyManagers();
        }
        finally {
            try {
                if (keyInput != null)
                    keyInput.close();
            }
            catch (IOException e) {
                // TODO log
            }
        }
    }

    private static TrustManager[] getTrustManagerFactory(String truststoreFile, String storePass) throws IOException, GeneralSecurityException {
        InputStream trustInput = null;
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustInput = new FileInputStream(truststoreFile);
            trustStore.load(trustInput, storePass.toCharArray());
            trustManagerFactory.init(trustStore);
            return trustManagerFactory.getTrustManagers();
        }
        finally {
            try {
                if (trustInput != null)
                    trustInput.close();
            }
            catch (IOException e) {
                // TODO log
            }
        }
    }
}
