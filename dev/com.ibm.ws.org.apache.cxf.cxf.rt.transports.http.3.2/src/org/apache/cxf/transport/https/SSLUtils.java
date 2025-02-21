/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.transport.https;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.logging.Logger;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.configuration.jsse.TLSParameterBase;
import org.apache.cxf.configuration.jsse.TLSServerParameters;
import org.apache.cxf.transport.https.httpclient.DefaultHostnameVerifier;
import org.apache.cxf.transport.https.httpclient.PublicSuffixMatcherLoader;

public final class SSLUtils {

    private static final Logger LOG = LogUtils.getL7dLogger(SSLUtils.class);
    // Liberty Change Start 
    private static boolean performHostNameVerification  = true; // Liberty Change

    static {
        Boolean b = (Boolean) AccessController.doPrivileged(new PrivilegedAction() {
        public Object run() {
           String propValue = System.getProperty("com.ibm.ssl.performURLHostNameVerification");
           return (propValue != null ? Boolean.valueOf(propValue) : true);
        }
        });
        performHostNameVerification = b.booleanValue();
        LOG.fine("Property com.ibm.ssl.performURLHostNameVerification is set to: " + performHostNameVerification);
    }
    // Liberty Change End

    private SSLUtils() {
        //Helper class
    }

    public static HostnameVerifier getHostnameVerifier(TLSClientParameters tlsClientParameters) {
        HostnameVerifier verifier;

        if (tlsClientParameters.getHostnameVerifier() != null) {
	    LOG.fine("Getting HostnameVerifier from tlsClientParameters."); // Liberty Change
            verifier = tlsClientParameters.getHostnameVerifier();
        } else if (tlsClientParameters.isUseHttpsURLConnectionDefaultHostnameVerifier()) {
	    LOG.fine("Getting DefaultHostnameVerifier from HttpsURLConnection."); // Liberty Change
            verifier = HttpsURLConnection.getDefaultHostnameVerifier();
        } else if (tlsClientParameters.isDisableCNCheck()) {
	    LOG.fine("isDisableCNCheck is true, setting verifier to AllowAllHostnameVerifier."); // Liberty Change
            verifier = new AllowAllHostnameVerifier();
	} else if (!performHostNameVerification) {  // Liberty Change Start
	    LOG.fine("performHostNameVerification is false, setting verifier to AllowAllHostnameVerifier.");
            verifier = new AllowAllHostnameVerifier();  // Liberty Change End
        } else {
	    LOG.fine("Getting new DefaultHostnameVerifier from PublicSuffixMatcherLoader."); // Liberty Change
            verifier = new DefaultHostnameVerifier(PublicSuffixMatcherLoader.getDefault());
        }
	LOG.fine("getHostnameVerifier() returning: " + verifier.getClass().getCanonicalName()); // Liberty Change
        return verifier;
    }

    public static SSLContext getSSLContext(TLSParameterBase parameters) throws GeneralSecurityException {
        // TODO do we need to cache the context
        String provider = parameters.getJsseProvider();

        String protocol = parameters.getSecureSocketProtocol() != null ? parameters
            .getSecureSocketProtocol() : "TLS";

	LOG.fine("getSSLContext: provider = " + provider + " and protocol = " + protocol); // Liberty Change

        SSLContext ctx = provider == null ? SSLContext.getInstance(protocol) : SSLContext
            .getInstance(protocol, provider);

        KeyManager[] keyManagers = parameters.getKeyManagers();
        if (keyManagers == null && parameters instanceof TLSClientParameters) {
	    LOG.fine("Calling getDefaultKeyStoreManagers...");
            keyManagers = org.apache.cxf.configuration.jsse.SSLUtils.getDefaultKeyStoreManagers(LOG);
        }
        KeyManager[] configuredKeyManagers = configureKeyManagersWithCertAlias(parameters, keyManagers);

        TrustManager[] trustManagers = parameters.getTrustManagers();
        if (trustManagers == null && parameters instanceof TLSClientParameters) {
	    LOG.fine("Calling getDefaultTrustStoreManagers...");
            trustManagers = org.apache.cxf.configuration.jsse.SSLUtils.getDefaultTrustStoreManagers(LOG);
        }

	LOG.fine("keyManagers: " + keyManagers); // Liberty Change
	LOG.fine("trustManagers: " + trustManagers); // Liberty Change
	LOG.fine("configuredkeyManagers: " +  configuredKeyManagers); // Liberty Change

        ctx.init(configuredKeyManagers, trustManagers, parameters.getSecureRandom());

        if (parameters instanceof TLSClientParameters && ctx.getClientSessionContext() != null) {
            ctx.getClientSessionContext().setSessionTimeout(((TLSClientParameters)parameters).getSslCacheTimeout());
        }

	LOG.fine("getSSLContext returning: " + ctx.getClass().getCanonicalName()); // Liberty Change
        return ctx;
    }

    public static KeyManager[] configureKeyManagersWithCertAlias(TLSParameterBase tlsParameters,
                                                      KeyManager[] keyManagers)
        throws GeneralSecurityException {
        if (tlsParameters.getCertAlias() == null || keyManagers == null) {
	    LOG.fine("Alias or keyManagers is NULL."); // Liberty Change
            return keyManagers;
        }

        KeyManager[] copiedKeyManagers = Arrays.copyOf(keyManagers, keyManagers.length);
        for (int idx = 0; idx < copiedKeyManagers.length; idx++) {
            if (copiedKeyManagers[idx] instanceof X509KeyManager
                && !(copiedKeyManagers[idx] instanceof AliasedX509ExtendedKeyManager)) {
                try {
                    copiedKeyManagers[idx] = new AliasedX509ExtendedKeyManager(tlsParameters.getCertAlias(),
                                                                         (X509KeyManager)copiedKeyManagers[idx]);
                } catch (Exception e) {
                    throw new GeneralSecurityException(e);
                }
            }
        }

        return copiedKeyManagers;
    }

    public static SSLEngine createServerSSLEngine(TLSServerParameters parameters) throws Exception {
        SSLContext sslContext = getSSLContext(parameters);
        SSLEngine serverEngine = sslContext.createSSLEngine();
        serverEngine.setUseClientMode(false);
        serverEngine.setNeedClientAuth(parameters.getClientAuthentication().isRequired());
        return serverEngine;
    }

    public static SSLEngine createClientSSLEngine(TLSClientParameters parameters) throws Exception {
        SSLContext sslContext = getSSLContext(parameters);
        SSLEngine clientEngine = sslContext.createSSLEngine();
        clientEngine.setUseClientMode(true);
        return clientEngine;
    }


}
