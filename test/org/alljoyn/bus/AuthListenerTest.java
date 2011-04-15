/**
 * Copyright 2009-2011, Qualcomm Innovation Center, Inc.
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0
 * 
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.alljoyn.bus;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusException;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.SignalEmitter;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.ifaces.DBusProxyObj;
import org.alljoyn.bus.ifaces.AllJoynProxyObj;

import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.security.auth.x500.X500Principal;

import static junit.framework.Assert.*;
import junit.framework.TestCase;

public class AuthListenerTest extends TestCase {
    static {
        System.loadLibrary("alljoyn_java");
    }

    private BusAttachment bus;
    private BusAttachment serviceBus;
    private SecureService service;
    private SecureInterface proxy;
    private InsecureInterface insecureProxy;
    private BusAuthListener authListener;
    private BusAuthListener serviceAuthListener;
    private String logonEntry;
    private int abortCount;
    private int clientPasswordCount;
    private int servicePasswordCount;
    private TestKeyStoreListener serviceKeyStoreListener;
    private TestKeyStoreListener keyStoreListener;

    public class TestKeyStoreListener implements KeyStoreListener {
        private byte[] keys;
        public boolean saveKeys;
        public byte[] getKeys() { return keys; }
        public char[] getPassword() { return "password".toCharArray(); }
        public void putKeys(byte[] keys) {
            if (saveKeys) {
                this.keys = keys;
            }
        }
    }

    public class SecureService implements SecureInterface, InsecureInterface, BusObject {
        public String Ping(String str) { 
            assertTrue(bus.getMessageContext().authMechanism.length() > 0);
            return str; 
        }
        public String InsecurePing(String str) { 
            return str; 
        }
    }

    public class BusAuthListener implements AuthListener {
        protected String name;
        public String userName;
        public boolean setUserName;
        public char[] password;
        public String mechanism;
        public int count;
        public int completed;
        public Map<String, String> x509cert;
        public Map<String, String> privKey;
        public ArrayList<AuthRequest> authRequests;

        public BusAuthListener(String name) {
            this.name = name;
            userName = "userName";
            setUserName = true;
            password = "123456".toCharArray();

            x509cert = new HashMap<String, String>();
            privKey = new HashMap<String, String>();
            x509cert.put("client", 
                         "-----BEGIN CERTIFICATE-----\n" +
                         "MIIBszCCARwCCQDuCh+BWVBk2DANBgkqhkiG9w0BAQUFADAeMQ0wCwYDVQQKDARN\n" +
                         "QnVzMQ0wCwYDVQQDDARHcmVnMB4XDTEwMDUxNzE1MTg1N1oXDTExMDUxNzE1MTg1\n" +
                         "N1owHjENMAsGA1UECgwETUJ1czENMAsGA1UEAwwER3JlZzCBnzANBgkqhkiG9w0B\n" +
                         "AQEFAAOBjQAwgYkCgYEArSd4r62mdaIRG9xZPDAXfImt8e7GTIyXeM8z49Ie1mrQ\n" +
                         "h7roHbn931Znzn20QQwFD6pPC7WxStXJVH0iAoYgzzPsXV8kZdbkLGUMPl2GoZY3\n" +
                         "xDSD+DA3m6krcXcN7dpHv9OlN0D9Trc288GYuFEENpikZvQhMKPDUAEkucQ95Z8C\n" +
                         "AwEAATANBgkqhkiG9w0BAQUFAAOBgQBkYY6zzf92LRfMtjkKs2am9qvjbqXyDJLS\n" +
                         "viKmYe1tGmNBUzucDC5w6qpPCTSe23H2qup27///fhUUuJ/ssUnJ+Y77jM/u1O9q\n" +
                         "PIn+u89hRmqY5GKHnUSZZkbLB/yrcFEchHli3vLo4FOhVVHwpnwLtWSpfBF9fWcA\n" +
                         "7THIAV79Lg==\n" +
                         "-----END CERTIFICATE-----");
            privKey.put("client",
                        "-----BEGIN RSA PRIVATE KEY-----\n" +
                        "Proc-Type: 4,ENCRYPTED\n" +
                        "DEK-Info: AES-128-CBC,0AE4BAB94CEAA7829273DD861B067DBA\n" +
                        "\n" +
                        "LSJOp+hEzNDDpIrh2UJ+3CauxWRKvmAoGB3r2hZfGJDrCeawJFqH0iSYEX0n0QEX\n" +
                        "jfQlV4LHSCoGMiw6uItTof5kHKlbp5aXv4XgQb74nw+2LkftLaTchNs0bW0TiGfQ\n" +
                        "XIuDNsmnZ5+CiAVYIKzsPeXPT4ZZSAwHsjM7LFmosStnyg4Ep8vko+Qh9TpCdFX8\n" +
                        "w3tH7qRhfHtpo9yOmp4hV9Mlvx8bf99lXSsFJeD99C5GQV2lAMvpfmM8Vqiq9CQN\n" +
                        "9OY6VNevKbAgLG4Z43l0SnbXhS+mSzOYLxl8G728C6HYpnn+qICLe9xOIfn2zLjm\n" +
                        "YaPlQR4MSjHEouObXj1F4MQUS5irZCKgp4oM3G5Ovzt82pqzIW0ZHKvi1sqz/KjB\n" +
                        "wYAjnEGaJnD9B8lRsgM2iLXkqDmndYuQkQB8fhr+zzcFmqKZ1gLRnGQVXNcSPgjU\n" +
                        "Y0fmpokQPHH/52u+IgdiKiNYuSYkCfHX1Y3nftHGvWR3OWmw0k7c6+DfDU2fDthv\n" +
                        "3MUSm4f2quuiWpf+XJuMB11px1TDkTfY85m1aEb5j4clPGELeV+196OECcMm4qOw\n" +
                        "AYxO0J/1siXcA5o6yAqPwPFYcs/14O16FeXu+yG0RPeeZizrdlv49j6yQR3JLa2E\n" +
                        "pWiGR6hmnkixzOj43IPJOYXySuFSi7lTMYud4ZH2+KYeK23C2sfQSsKcLZAFATbq\n" +
                        "DY0TZHA5lbUiOSUF5kgd12maHAMidq9nIrUpJDzafgK9JrnvZr+dVYM6CiPhiuqJ\n" +
                        "bXvt08wtKt68Ymfcx+l64mwzNLS+OFznEeIjLoaHU4c=\n" +
                        "-----END RSA PRIVATE KEY-----");
            x509cert.put("service",
                         "-----BEGIN CERTIFICATE-----\n" +
                         "MIIB7TCCAZegAwIBAgIJAKSCIxJABMPWMA0GCSqGSIb3DQEBBQUAMFIxCzAJBgNV\n" +
                         "BAYTAlVTMRMwEQYDVQQIDApXYXNoaW5ndG9uMRAwDgYDVQQHDAdTZWF0dGxlMQ0w\n" +
                         "CwYDVQQKDARRdUlDMQ0wCwYDVQQDDARHcmVnMB4XDTEwMDgwMzIzNTYzOVoXDTEx\n" +
                         "MDgwMzIzNTYzOVowUjELMAkGA1UEBhMCVVMxEzARBgNVBAgMCldhc2hpbmd0b24x\n" +
                         "EDAOBgNVBAcMB1NlYXR0bGUxDTALBgNVBAoMBFF1SUMxDTALBgNVBAMMBEdyZWcw\n" +
                         "XDANBgkqhkiG9w0BAQEFAANLADBIAkEA3b+TpTkJD03LlgKKA9phSeA+5owwM/jj\n" +
                         "PrRFcrH0mrFrHRujyPCuWRwOZojXgxVFU/jaTOyQ5sA5df7nEMgf/wIDAQABo1Aw\n" +
                         "TjAdBgNVHQ4EFgQUr6/4jRv/8qYIAtu/x9wSHllToxgwHwYDVR0jBBgwFoAUr6/4\n" +
                         "jRv/8qYIAtu/x9wSHllToxgwDAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQUFAANB\n" +
                         "ABJSIipYXtLymiidV3J6cOlurPvEM/mXey9FMjvAjrNrrhuOBP1SFrcW+ubWsmWi\n" +
                         "EeP1srLyLDXtE5AogwPcaVc=\n" +
                         "-----END CERTIFICATE-----");
            privKey.put("service",
                        "-----BEGIN RSA PRIVATE KEY-----\n" +
                        "Proc-Type: 4,ENCRYPTED\n" +
                        "DEK-Info: AES-128-CBC,1B43B2A4AE39BF6CECCA363FC9D02237\n" +
                        "\n" +
                        "zEMSBXr4Up+C5ZeWVZw5LPZHColZ8+ZhgkNHdqSfgyjri7Ij6nb1ABcbWeJBeqtF\n" +
                        "9fsijcTqUACVOhrAFi3d+F9HYP6taqDDwCJj638cTnYGM9j+WAspNOm05FlFmgvs\n" +
                        "guwpqc98RAj29C72zYb3GWoW0xIOhPF84OWKppweMSV6UFpLqnpFmo0zGT4ItMhV\n" +
                        "/tOdXyrTzhyjwFWhOBM1GZSKl1AtmIgDW88fFfGyPxIQSS/30ur0/dgUinVODBLP\n" +
                        "kNP73tpiBCeSHWqLlHV/bTer7TE5dsbyvvbFKftns/wP4Eri3V4SsldkURUJTrG7\n" +
                        "oGvwY4hwV0iZjSUcX1aBrfXE6oc8LAaJrZzNDUvNLjM2jHzIvMTwWIa3R1z9yjWl\n" +
                        "Rk5RScL4+i2JPll9SzrkhIGvh0ElYRdzbfkrUIY2anGwxM5Ihcv8Z3kpYJyvhdJu\n" +
                        "-----END RSA PRIVATE KEY-----\n");

            authRequests = new ArrayList<AuthRequest>();
        }

        public boolean requested(String mechanism, String authPeer, int count, String userName, 
                                 AuthRequest[] requests) {
            /* Bail out if we get stuck in a loop. */
            assertTrue(count < 10);

            this.mechanism = mechanism;
            for (AuthRequest request : requests) {
                authRequests.add(request);
                if (request instanceof LogonEntryRequest) {
                    assertEquals(this.userName, userName);
                    this.count = count;
                    if (logonEntry != null) {
                        ((LogonEntryRequest) request).setLogonEntry(logonEntry.toCharArray());
                    }
                } else if (request instanceof PasswordRequest) {
                    this.count = count;
                    boolean setPassword = false;
                    if ("client".equals(name)) {
                        if (clientPasswordCount == 0) {
                            setPassword = true;
                        } else {
                            --clientPasswordCount;
                        }
                    } else if ("service".equals(name)) {
                        if ("ALLJOYN_SRP_LOGON".equals(mechanism)) {
                            assertEquals(this.userName, userName);
                        }
                        if (servicePasswordCount == 0) {
                            setPassword = true;
                        } else {
                            --servicePasswordCount;
                        }
                    } else if (logonEntry == null) {
                        setPassword = true;
                    }
                    if (setPassword) {
                        if (!setUserName) {
                            assertEquals(this.userName, userName);
                        }
                        ((PasswordRequest) request).setPassword(password);
                    }
                } else if (request instanceof CertificateRequest) {
                    assertEquals("", userName);
                    this.count = count;
                    ((CertificateRequest) request).setCertificateChain(x509cert.get(name));
                } else if (request instanceof PrivateKeyRequest) {
                    assertEquals("", userName);
                    this.count = count;
                    ((PrivateKeyRequest) request).setPrivateKey(privKey.get(name));
                } else if (request instanceof UserNameRequest) {
                    assertEquals("", userName);
                    this.count = count;
                    if (setUserName) {
                        ((UserNameRequest) request).setUserName(this.userName);
                    }
                } else if (request instanceof VerifyRequest) {
                    assertEquals("", userName);
                } else {
                    return false;
                }
            }
            return true;
        }

        public void completed(String mechanism, boolean authenticated) {
            ++completed;
        }
    }

    public class UserNameOnceAuthListener extends BusAuthListener {

        public UserNameOnceAuthListener(String name) {
            super(name);
        }

        public boolean requested(String mechanism, String authPeer, int count, String userName, 
                                 AuthRequest[] requests) {
            boolean result = super.requested(mechanism, authPeer, count, userName, requests);
            setUserName = false;
            return result;
        }        
    }

    public class RetryAuthListener extends BusAuthListener {

        public RetryAuthListener(String name) {
            super(name);
            password = "654321".toCharArray();
        }

        public boolean requested(String mechanism, String authPeer, int count, String userName, 
                                 AuthRequest[] requests) {
            boolean result = super.requested(mechanism, authPeer, count, userName, requests);
            password = "123456".toCharArray();
            return result;
        }        
    }

    public class UserNameAuthListener extends BusAuthListener {

        public UserNameAuthListener(String name) {
            super(name);
        }

        public boolean requested(String mechanism, String authPeer, int count, String userName, 
                                 AuthRequest[] requests) {
            /* Bail out if we get stuck in a loop. */
            assertTrue(count < 10);

            this.mechanism = mechanism;
            for (AuthRequest request : requests) {
                if (request instanceof PasswordRequest) {
                    this.count = count;
                    if (userName.equals(this.userName)) {
                        ((PasswordRequest) request).setPassword(password);
                    }
                    return true;
                }
            }
            return false;
        }        
    }

    public class RetryUserNameAuthListener extends BusAuthListener {

        public RetryUserNameAuthListener(String name) {
            super(name);
            userName = "notUser";
        }

        public boolean requested(String mechanism, String authPeer, int count, String userName, 
                                 AuthRequest[] requests) {
            boolean result = super.requested(mechanism, authPeer, count, userName, requests);
            this.userName = "userName";
            return result;
        }        
    }

    public class AbortAuthListener extends BusAuthListener {

        public AbortAuthListener(String name) {
            super(name);
            password = "654321".toCharArray();
        }

        public boolean requested(String mechanism, String authPeer, int count, String userName, 
                                 AuthRequest[] requests) {
            /* Bail out if we get stuck in a loop. */
            assertTrue(count < 10);

            if (count <= abortCount) {
                return super.requested(mechanism, authPeer, count, userName, requests);
            } else {
                for (AuthRequest request : requests) {
                    if (request instanceof VerifyRequest) {
                    } else {
                        this.count = count;
                    }
                }
            }
            return false;
        }        
    }

    public class AbortFirstMechanismAuthListener extends BusAuthListener {

        private String mechanism;

        public AbortFirstMechanismAuthListener(String name) {
            super(name);
        }

        public boolean requested(String mechanism, String authPeer, int count, String userName, 
                                 AuthRequest[] requests) {
            /* Bail out if we get stuck in a loop. */
            assertTrue(count < 10);

            if (this.mechanism == null) {
                this.mechanism = mechanism;
            }
            if (this.mechanism.equals(mechanism)) {
                password = "654321".toCharArray();
            } else {
                password = "123456".toCharArray();
            }

            if (count <= abortCount) {
                return super.requested(mechanism, authPeer, count, userName, requests);
            } else {
                for (AuthRequest request : requests) {
                    if (request instanceof VerifyRequest) {
                    } else {
                        this.count = count;
                    }
                }
            }
            return false;
        }        
    }

    public class RsaAuthListener implements AuthListener {
        private String certificate;
        private String privateKey;
        private char[] password;
        private CertificateFactory factory;
        private CertPathValidator validator;
        private Set<TrustAnchor> trustAnchors;

        public String verified;
        public String rejected;

        public RsaAuthListener(String certificate) throws Exception {
            this(certificate, null, null);
        }

        public RsaAuthListener(String certificate, String privateKey, char[] password) throws Exception {
            this.certificate = certificate;
            this.privateKey = privateKey;
            this.password = password;
            if ("The Android Project".equals(System.getProperty("java.vendor"))) {
                // If you don't say "BC" for CertificateFactory , Android will give you a 
                // "DRLCertificateFactory" and "BC" validator and validation will fail.
                factory = CertificateFactory.getInstance("X.509", "BC");
                validator = CertPathValidator.getInstance("PKIX", "BC");
            } else {
                factory = CertificateFactory.getInstance("X.509");
                validator = CertPathValidator.getInstance("PKIX");
            }
            trustAnchors = new HashSet<TrustAnchor>();
        }

        public void addTrustAnchor(String certificate) throws Exception {
            BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(certificate.getBytes()));
            trustAnchors.add(new TrustAnchor((X509Certificate) factory.generateCertificate(in), null));
        }

        public boolean requested(String mechanism, String authPeer, int count, String userName, 
                                 AuthRequest[] requests) {
            for (AuthRequest request : requests) {
                if (request instanceof CertificateRequest) {
                    ((CertificateRequest) request).setCertificateChain(certificate);
                } else if (request instanceof PrivateKeyRequest) {
                    if (privateKey != null) {
                        ((PrivateKeyRequest) request).setPrivateKey(privateKey);
                    }
                } else if (request instanceof PasswordRequest) {
                    if (password != null) {
                        ((PasswordRequest) request).setPassword(password);
                    }
                } else if (request instanceof VerifyRequest) {
                    String subject = null;
                    try {
                        String chain = ((VerifyRequest) request).getCertificateChain();
                        
                        BufferedInputStream in = 
                            new BufferedInputStream(new ByteArrayInputStream(chain.getBytes()));
                        List<X509Certificate> list = new ArrayList<X509Certificate>();
                        while (in.available() > 0) {
                            list.add((X509Certificate) factory.generateCertificate(in));
                        }
                        subject = list.get(0)
                            .getSubjectX500Principal()
                            .getName(X500Principal.CANONICAL)
                            .split(",")[0]
                            .split("=")[1];
                        CertPath path = factory.generateCertPath(list);

                        PKIXParameters params = new PKIXParameters(trustAnchors);
                        params.setRevocationEnabled(false);

                        validator.validate(path, params);
                        verified = subject;
                    } catch (Exception ex) {
                        rejected = subject;
                        return false;
                    }
                }
            }
            return true;
        }

        public void completed(String mechanism, boolean authenticated) {}
    }

    public AuthListenerTest(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        serviceKeyStoreListener = new TestKeyStoreListener();
        keyStoreListener = new TestKeyStoreListener();
        setUp(serviceKeyStoreListener, keyStoreListener);
    }

    public void setUp(KeyStoreListener serviceKeyStoreListener, 
                      KeyStoreListener keyStoreListener) throws Exception{
        serviceBus = new BusAttachment("receiver");
        serviceBus.registerKeyStoreListener(serviceKeyStoreListener);
        service = new SecureService();
        assertEquals(Status.OK, serviceBus.registerBusObject(service, "/secure"));
        assertEquals(Status.OK, serviceBus.connect());
        DBusProxyObj control = serviceBus.getDBusProxyObj();
        assertEquals(DBusProxyObj.RequestNameResult.PrimaryOwner, 
                     control.RequestName("org.alljoyn.bus.BusAttachmentTest", 
                                         DBusProxyObj.REQUEST_NAME_NO_FLAGS));

        bus = new BusAttachment("sender");
        bus.registerKeyStoreListener(keyStoreListener);
        assertEquals(Status.OK, bus.connect());
        ProxyBusObject proxyObj = bus.getProxyBusObject("org.alljoyn.bus.BusAttachmentTest",
                                                        "/secure", 
                                                        BusAttachment.SESSION_ID_ANY,
                                                        new Class[] { SecureInterface.class, InsecureInterface.class });
        proxy = proxyObj.getInterface(SecureInterface.class);
        insecureProxy = proxyObj.getInterface(InsecureInterface.class);
        abortCount = 3;
    }

    public void tearDown() throws Exception {
        logonEntry = null;
        proxy = null;
        bus.disconnect();
        bus = null;

        DBusProxyObj control = serviceBus.getDBusProxyObj();
        assertEquals(DBusProxyObj.ReleaseNameResult.Released, 
                     control.ReleaseName("org.alljoyn.bus.BusAttachmentTest"));
        serviceBus.disconnect();
        serviceBus.unregisterBusObject(service);
        serviceBus = null;
    }
    
    public void doPing(String mechanism) throws Exception {
        assertEquals(Status.OK, serviceBus.registerAuthListener(mechanism, serviceAuthListener));
        assertEquals(Status.OK, bus.registerAuthListener(mechanism, authListener));
        BusException ex = null;
        try {
            proxy.Ping("hello");
        } catch (BusException e) {
            ex = e;
        }
        /*
         * Make insecure second call to ensure that all the authentication transactions have run
         * their course on both sides.
         */
        insecureProxy.InsecurePing("goodbye");
        if (ex != null) {
            throw ex;
        }
    }

    public void listener(String mechanism) throws Exception {
        serviceAuthListener = new BusAuthListener("service");
        authListener = new BusAuthListener("client");
        doPing(mechanism);
        assertEquals(mechanism, authListener.mechanism);
        assertEquals(1, authListener.count);
        assertEquals(1, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }
    
    public void retryClient(String mechanism) throws Exception {
        serviceAuthListener = new BusAuthListener("service");
        authListener = new RetryAuthListener("client");
        doPing(mechanism);
    }

    public void retryService(String mechanism) throws Exception {
        serviceAuthListener = new RetryAuthListener("service");
        authListener = new BusAuthListener("client");
        doPing(mechanism);
    }

    public void abortClient(String mechanism) throws Exception {
        serviceAuthListener = new BusAuthListener("service");
        authListener = new AbortAuthListener("client");
        try {
            doPing(mechanism);
        } catch (BusException ex) {
        }
    }

    public void abortService(String mechanism) throws Exception {
        serviceAuthListener = new AbortAuthListener("service");
        authListener = new BusAuthListener("client");
        try {
            doPing(mechanism);
        } catch (BusException ex) {
        }
    }

    public void testSrpListener() throws Exception {
        listener("ALLJOYN_SRP_KEYX");
    }
    public void testSrpRetryClient() throws Exception {
        retryClient("ALLJOYN_SRP_KEYX");
        assertEquals(2, authListener.count);
        assertEquals(2, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }
    public void testSrpRetryService() throws Exception {
        retryService("ALLJOYN_SRP_KEYX");
        assertEquals(2, authListener.count);
        assertEquals(2, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }
    public void testSrpAbortClient() throws Exception {
        abortClient("ALLJOYN_SRP_KEYX");
        assertEquals(4, authListener.count);
        assertEquals(4, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }
    public void testSrpAbortService() throws Exception {
        abortService("ALLJOYN_SRP_KEYX");
        assertEquals(3, authListener.count);
        assertEquals(4, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }
    public void testSrpAbortImmediatelyClient() throws Exception {
        abortCount = 0;
        abortClient("ALLJOYN_SRP_KEYX");
        assertEquals(1, authListener.count);
        assertEquals(1, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }
    public void testSrpAbortImmediatelyService() throws Exception {
        abortCount = 0;
        abortService("ALLJOYN_SRP_KEYX");
        assertEquals(0, authListener.count);
        assertEquals(1, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }

    public void testSrpLogonListenerPassword() throws Exception {
        listener("ALLJOYN_SRP_LOGON");
    }
    public void testSrpLogonListenerPasswordSeparately() throws Exception {
        clientPasswordCount = 1;
        listener("ALLJOYN_SRP_LOGON");
    }
    public void testSrpLogonListenerPasswordUserNameOnce() throws Exception {
        clientPasswordCount = 1;
        String mechanism = "ALLJOYN_SRP_LOGON";
        serviceAuthListener = new BusAuthListener("service");
        authListener = new UserNameOnceAuthListener("client");
        doPing(mechanism);
        assertEquals(mechanism, authListener.mechanism);
        assertEquals(1, authListener.count);
        assertEquals(1, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }
    public void testSrpLogonListenerLogonEntry() throws Exception {
        logonEntry = "EEAF0AB9ADB38DD69C33F80AFA8FC5E86072618775FF3C0B9EA2314C9C256576D674DF7496EA81D3383B4813D692C6E0E0D5D8E250B98BE48E495C1D6089DAD15DC7D7B46154D6B6CE8EF4AD69B15D4982559B297BCF1885C529F566660E57EC68EDBC3C05726CC02FD4CBF4976EAA9AFD5138FE8376435B9FC61D2FC0EB06E3:02:9D446DE02A:825862685243B939BA032B712431FF2175F1F4B93F113DD114D542EC2FB8F4A908393F0D532A990CA8BE342FBBB30C16202B67B152CEE75856EFCA4F3F2ABC3179DA2F555741D85E8C17F90CE7B2CF9737AD7DE1BB871B90372B91B8906114DC4DFDF2D24265F124F2032E9A161AB281A05F84ED2FA5E2357A9B5DA4C4843260";
        listener("ALLJOYN_SRP_LOGON");
    }
    public void testSrpLogonRetryPasswordClient() throws Exception {
        retryClient("ALLJOYN_SRP_LOGON");
        assertEquals(2, authListener.count);
        assertEquals(1, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }
    /* ALLJOYN-75 */
    public void testSrpLogonRetryUserNameClient() throws Exception {
        serviceAuthListener = new UserNameAuthListener("service");
        authListener = new RetryUserNameAuthListener("client");
        doPing("ALLJOYN_SRP_LOGON");
        assertEquals(2, authListener.count);
        assertEquals(2, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }
    /* Service only gets asked for password once, so no service retry test */
    public void testSrpLogonAbortClient() throws Exception {
        abortClient("ALLJOYN_SRP_LOGON");
        assertEquals(4, authListener.count);
        assertEquals(1, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }
    public void testSrpLogonAbortService() throws Exception {
        abortCount = 0;
        abortService("ALLJOYN_SRP_LOGON");
        assertEquals(1, authListener.count);
        assertEquals(1, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }
    public void testSrpLogonAbortImmediatelyClient() throws Exception {
        abortCount = 0;
        abortClient("ALLJOYN_SRP_LOGON");
        assertEquals(1, authListener.count);
        assertEquals(0, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(0, serviceAuthListener.completed);
    }

    public void assertNewPasswordRequested(ArrayList<AuthListener.AuthRequest> authRequests) {
        boolean newPasswordRequested = false;
        for (AuthListener.AuthRequest request : authRequests) {
            if (request instanceof AuthListener.PasswordRequest 
                && ((AuthListener.PasswordRequest) request).isNewPassword()) {
                newPasswordRequested = true;
                break;
            }
        }
        assertEquals(true, newPasswordRequested);
    }
    public void assertPasswordRequested(ArrayList<AuthListener.AuthRequest> authRequests) {
        boolean passwordRequested = false;
        for (AuthListener.AuthRequest request : authRequests) {
            if (request instanceof AuthListener.PasswordRequest 
                && !((AuthListener.PasswordRequest) request).isNewPassword()
                && !((AuthListener.PasswordRequest) request).isOneTimePassword()) {
                passwordRequested = true;
                break;
            }
        }
        assertEquals(true, passwordRequested);
    }
    public int passwordRequests(ArrayList<AuthListener.AuthRequest> authRequests) {
        int requests = 0;
        for (AuthListener.AuthRequest request : authRequests) {
            if (request instanceof AuthListener.PasswordRequest) {
                ++requests;
            }
        }
        return requests;
    }

    public void testRsaListener() throws Exception {
        listener("ALLJOYN_RSA_KEYX");
    }
    public void testRsaListenerClientNewSelfSignedCert() throws Exception {
        serviceAuthListener = new BusAuthListener("service");
        authListener = new BusAuthListener("client");
        authListener.x509cert.clear();
        doPing("ALLJOYN_RSA_KEYX");
        assertNewPasswordRequested(authListener.authRequests);
    }
    public void testRsaListenerClientExistingSelfSignedCert() throws Exception {
        /* Create the self-signed cert */
        keyStoreListener.saveKeys = true;
        serviceAuthListener = new BusAuthListener("service");
        authListener = new BusAuthListener("client");
        authListener.x509cert.clear();
        doPing("ALLJOYN_RSA_KEYX");
        tearDown();

        /* Now test the auth request */
        setUp(serviceKeyStoreListener, keyStoreListener);
        authListener.authRequests.clear();
        doPing("ALLJOYN_RSA_KEYX");
        assertPasswordRequested(authListener.authRequests);
    }
    public void testRsaListenerServiceNewSelfSignedCert() throws Exception {
        serviceAuthListener = new BusAuthListener("service");
        authListener = new BusAuthListener("client");
        serviceAuthListener.x509cert.clear();
        serviceAuthListener.privKey.clear();
        doPing("ALLJOYN_RSA_KEYX");
        assertNewPasswordRequested(serviceAuthListener.authRequests);
    }
    public void testRsaListenerServiceExistingSelfSignedCert() throws Exception {
        /* Create the self-signed cert */
        serviceKeyStoreListener.saveKeys = true;
        serviceAuthListener = new BusAuthListener("service");
        serviceAuthListener.x509cert.clear();
        authListener = new BusAuthListener("client");
        doPing("ALLJOYN_RSA_KEYX");
        tearDown();

        /* Now test the auth request */
        setUp(serviceKeyStoreListener, keyStoreListener);
        serviceAuthListener.authRequests.clear();
        doPing("ALLJOYN_RSA_KEYX");
        assertPasswordRequested(serviceAuthListener.authRequests);
    }
    public void testRsaListenerServiceExistingSelfSignedCertWithPassword() throws Exception {
        /* Create the self-signed cert */
        serviceKeyStoreListener.saveKeys = true;
        serviceAuthListener = new BusAuthListener("service");
        serviceAuthListener.x509cert.clear();
        serviceAuthListener.privKey.clear();
        authListener = new BusAuthListener("client");
        doPing("ALLJOYN_RSA_KEYX");
        tearDown();

        /* Now re-authenticate and supply only password - mechanism should not ask for password twice. */
        setUp(serviceKeyStoreListener, keyStoreListener);
        serviceAuthListener.authRequests.clear();
        doPing("ALLJOYN_RSA_KEYX");
        assertEquals(1, passwordRequests(serviceAuthListener.authRequests));
    }
    /* ALLJOYN-42 */
    public void testRsaListenerServiceExistingSelfSignedCertWithPrivateKeyAndPassword() throws Exception {
        /* Create the self-signed cert */
        serviceKeyStoreListener.saveKeys = true;
        serviceAuthListener = new BusAuthListener("service");
        serviceAuthListener.x509cert.clear();
        authListener = new BusAuthListener("client");
        doPing("ALLJOYN_RSA_KEYX");
        tearDown();

        /* Now re-authenticate and supply only private key and password. */
        setUp(serviceKeyStoreListener, keyStoreListener);
        serviceAuthListener.authRequests.clear();
        doPing("ALLJOYN_RSA_KEYX");
        assertEquals(1, passwordRequests(serviceAuthListener.authRequests));

        /* TODO should be able to generate public key from private key. */
    }
    public void testRsaServiceNoPassword() throws Exception {
        /* service returns only cert and private key on first request */
        servicePasswordCount = 1;
        listener("ALLJOYN_RSA_KEYX");
        assertEquals(2, passwordRequests(serviceAuthListener.authRequests));
    }
    public void testRsaServiceNoPrivateKey() throws Exception {
        /* service supplies cert and password in request */
        serviceAuthListener = new BusAuthListener("service");
        serviceAuthListener.privKey.clear();
        authListener = new BusAuthListener("client");
        try {
            doPing("ALLJOYN_RSA_KEYX");
        } catch (BusException ex) {
        }

    }
    public void testRsaServiceNoPrivateKeyNoPassword() throws Exception {
        /* service supplies only cert in request */
        serviceAuthListener = new BusAuthListener("service");
        serviceAuthListener.privKey.clear();
        servicePasswordCount = 1;
        authListener = new BusAuthListener("client");
        try {
            doPing("ALLJOYN_RSA_KEYX");
        } catch (BusException ex) {
        }
    }
    /* Client doesn't use password, so no client retry test */
    public void testRsaRetryService() throws Exception {
        retryService("ALLJOYN_RSA_KEYX");
        assertEquals(1, serviceAuthListener.completed);
        assertEquals(2, serviceAuthListener.count);
        assertEquals(1, authListener.count);
        assertEquals(1, authListener.completed);
    }
    public void testRsaAbortClient() throws Exception {
        abortCount = 0;
        abortClient("ALLJOYN_RSA_KEYX");
        assertEquals(1, authListener.count);
        assertEquals(0, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(0, serviceAuthListener.completed);
    }
    public void testRsaAbortService() throws Exception {
        abortService("ALLJOYN_RSA_KEYX");
        assertEquals(1, authListener.count);
        assertEquals(4, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }
    public void testRsaAbortImmediatelyService() throws Exception {
        abortCount = 0;
        abortService("ALLJOYN_RSA_KEYX");
        assertEquals(1, authListener.count);
        assertEquals(1, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }

    /* TODO Only test abort of multiple mechanisms from client, service has no say in picking mechanism. */

    public void testAbortFirstMechanismClient() throws Exception {
        /* Authentication uses ALLJOYN_SRP_KEYX first regardless of order. */
        String authMechanisms = "ALLJOYN_SRP_KEYX ALLJOYN_SRP_LOGON";
        serviceAuthListener = new BusAuthListener("service");
        authListener = new AbortFirstMechanismAuthListener("client");
        assertEquals(Status.OK, serviceBus.registerAuthListener(authMechanisms, serviceAuthListener));
        assertEquals(Status.OK, bus.registerAuthListener(authMechanisms, authListener));
        try {
            proxy.Ping("hello");
        } catch (BusException ex) {
        }
        assertEquals(1, authListener.count);
        assertEquals(1, serviceAuthListener.count);
        assertEquals(1, authListener.completed);
        assertEquals(1, serviceAuthListener.completed);
    }

    /* ALLJOYN-25 */
    public void testAuthListenerUnknownName() throws Exception {
        serviceAuthListener = new BusAuthListener("service");
        authListener = new BusAuthListener("client");
        assertEquals(Status.OK, serviceBus.registerAuthListener("ALLJOYN_SRP_KEYX", serviceAuthListener));
        assertEquals(Status.OK, bus.registerAuthListener("ALLJOYN_SRP_KEYX", authListener));
        ProxyBusObject proxyObj = bus.getProxyBusObject("unknown.name",
                                                        "/secure", 
                                                        BusAttachment.SESSION_ID_ANY,
                                                        new Class[] { SecureInterface.class });
        proxy = proxyObj.getInterface(SecureInterface.class);
        boolean thrown = false;
        try {
           proxy.Ping("hello");
        } catch (BusException ex) {
            thrown = true;
        }
        assertEquals(true, thrown);
    }

    public void testRsaVerify() throws Exception {
        RsaAuthListener serviceAuthListener = new RsaAuthListener(
            /* certificate */
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIC5DCCAk2gAwIBAgIBAzANBgkqhkiG9w0BAQUFADBVMQswCQYDVQQGEwJBVTET\n" +
            "MBEGA1UECBMKU29tZS1TdGF0ZTEhMB8GA1UEChMYSW50ZXJuZXQgV2lkZ2l0cyBQ\n" +
            "dHkgTHRkMQ4wDAYDVQQDEwV1c2VyMTAeFw0xMDA5MDEyMTEyMDhaFw0xMTA5MDEy\n" +
            "MTEyMDhaMFUxCzAJBgNVBAYTAkFVMRMwEQYDVQQIEwpTb21lLVN0YXRlMSEwHwYD\n" +
            "VQQKExhJbnRlcm5ldCBXaWRnaXRzIFB0eSBMdGQxDjAMBgNVBAMTBXVzZXIyMIGf\n" +
            "MA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC2ZX0M5+WzWHm2DDyEjzFwOZ/Pkch9\n" +
            "P0lWtQYTKG6nS6pdGfJPmxrjk/AW4gA8UpsIhqzBznHcM1gUmDjca+/XBulBM9ww\n" +
            "83xFRl+wzPLvQpU0E4WJQMvB+rzWZSwmv8VlxJ43WXHi1CEHfbxxqSKlQl63h4Ew\n" +
            "T8709RbEYN/9YQIDAQABo4HDMIHAMAkGA1UdEwQCMAAwLAYJYIZIAYb4QgENBB8W\n" +
            "HU9wZW5TU0wgR2VuZXJhdGVkIENlcnRpZmljYXRlMB0GA1UdDgQWBBSkfYB0Nx2J\n" +
            "OoxbIzl3Fnfgn3Mn5jBmBgNVHSMEXzBdoVikVjBUMQswCQYDVQQGEwJBVTETMBEG\n" +
            "A1UECBMKU29tZS1TdGF0ZTEhMB8GA1UEChMYSW50ZXJuZXQgV2lkZ2l0cyBQdHkg\n" +
            "THRkMQ0wCwYDVQQDEwRyb290ggECMA0GCSqGSIb3DQEBBQUAA4GBAKcvMddSOTjn\n" +
            "0XQpvTuIAxHwMdu/UUXHHYbVs7OFk7+MLdMrQnqEroWbBt1aI6xB30ZTbrzXnMgP\n" +
            "4QJIF1Dt0ScLaQwUbVOO4+mPetet4otGb3tLTYYakQXDhAGKewm01Qa5wk9SG2PH\n" +
            "AOLB40noJ5BY8ZYo2Z4DNhFFDG0QfAf3\n" +
            "-----END CERTIFICATE-----\n" +
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICLzCCAZigAwIBAgIBAjANBgkqhkiG9w0BAQUFADBUMQswCQYDVQQGEwJBVTET\n" +
            "MBEGA1UECBMKU29tZS1TdGF0ZTEhMB8GA1UEChMYSW50ZXJuZXQgV2lkZ2l0cyBQ\n" +
            "dHkgTHRkMQ0wCwYDVQQDEwRyb290MB4XDTEwMDkwMTIxMTAyNloXDTExMDkwMTIx\n" +
            "MTAyNlowVTELMAkGA1UEBhMCQVUxEzARBgNVBAgTClNvbWUtU3RhdGUxITAfBgNV\n" +
            "BAoTGEludGVybmV0IFdpZGdpdHMgUHR5IEx0ZDEOMAwGA1UEAxMFdXNlcjEwgZ8w\n" +
            "DQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAMt+ckt5kpLzIY3lVxS0aQi5m+mM5Kgv\n" +
            "mnmHtUkbs8M7NDqQb7hO58+1zbq/LC+1PsaRkQ+iFLPmI2YvqlKGnvyqWjEzfzol\n" +
            "GAQlpTdQoQkatwm8hHzNoVhY48wP5+bu7eqBMIIpAGRNPwxizercohcQZksOpycy\n" +
            "bxgOX2LtzgBjAgMBAAGjEDAOMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEFBQAD\n" +
            "gYEANi9nG202uyhGpN2tt699A5tPF7apic7+vfkeA5i7zxvynqUj5mBhVbVheyOu\n" +
            "SSkmNG4xGMAkcBK4PevSpHMNnTTcTTNuA22QWhnndm+VIfcIHSSWmqm1KiTZ3LM3\n" +
            "6OLT8N+OVZEHVYDycb6hbkl6SkgkeXmpi+vqGFpx/Q+aj4o=\n" +
            "-----END CERTIFICATE-----\n" +
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICzTCCAjagAwIBAgIBADANBgkqhkiG9w0BAQUFADBUMQswCQYDVQQGEwJBVTET\n" +
            "MBEGA1UECBMKU29tZS1TdGF0ZTEhMB8GA1UEChMYSW50ZXJuZXQgV2lkZ2l0cyBQ\n" +
            "dHkgTHRkMQ0wCwYDVQQDEwRyb290MB4XDTEwMDkwMTIxMDYzOFoXDTEzMDgzMTIx\n" +
            "MDYzOFowVDELMAkGA1UEBhMCQVUxEzARBgNVBAgTClNvbWUtU3RhdGUxITAfBgNV\n" +
            "BAoTGEludGVybmV0IFdpZGdpdHMgUHR5IEx0ZDENMAsGA1UEAxMEcm9vdDCBnzAN\n" +
            "BgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAo5iX69wYP/sMNzfKSTHLxdtFOLkbakDH\n" +
            "h0MPxcXsG53LRdpUpZe1kf4DjTZGZ3PLGW9F+eJtEQ2DP6JWU20lLP4vosKUTZvw\n" +
            "dsrftKkMPiGVqlKTwDvZI2/6tWlmGEUCdRXg1sTHzgQguaXscLCdC/+VzwaASnL1\n" +
            "Pk0GDVpWcFUCAwEAAaOBrjCBqzAdBgNVHQ4EFgQUdDjaTf5WazoaumyVCaQ5tU55\n" +
            "EUYwfAYDVR0jBHUwc4AUdDjaTf5WazoaumyVCaQ5tU55EUahWKRWMFQxCzAJBgNV\n" +
            "BAYTAkFVMRMwEQYDVQQIEwpTb21lLVN0YXRlMSEwHwYDVQQKExhJbnRlcm5ldCBX\n" +
            "aWRnaXRzIFB0eSBMdGQxDTALBgNVBAMTBHJvb3SCAQAwDAYDVR0TBAUwAwEB/zAN\n" +
            "BgkqhkiG9w0BAQUFAAOBgQCHqyne7jWV2tk4VdHazHLdtVMQarGqVN8NOsb1T0zl\n" +
            "RMRXX9UHRoDs968z7ZOAI7i2TwD/JxhwvlQOnVEQT1Wf4zfU4LGsyR7Igb3mwotU\n" +
            "z67MiR3KpBCAVdsiA0Gv4xVCYGZsAqyrl0Ob3BDJe+Ce0eSSfWY3DWI7GEf3tYUz\n" +
            "qQ==\n" +
            "-----END CERTIFICATE-----\n",
            /* privateKey */
            "-----BEGIN RSA PRIVATE KEY-----\n" +
            "Proc-Type: 4,ENCRYPTED\n" +
            "DEK-Info: DES-EDE3-CBC,B2C36955B455FEFA\n" +
            "\n" +
            "sb3V1Egfy5taVkS5jGUSdsdm51Aw65e8cqSr2E/2pcfbWzZmVwFx9igxZgqujPCh\n" +
            "4lIvyueeVcVA92o58vmnsAdBPss4zSAOMlDxy/JEpbuGHiDVO1nsKWiKKmMimcv6\n" +
            "Z3N69KbA+2hP7DODen7Qs/57a44I6R7HXGfwTalWPc/Q25VZGMhhtAJWDQ634zwo\n" +
            "NPm1Q5qm2t3ZWQG/d1lTRsYmYGdGmp5xpeTRC/ghLei5HYAW72jO0HEvypjZP86g\n" +
            "2ghCazw/r2nLvOMoMjjTDddqJTUzuNOVEzka2A6u7htrRR933uBcsmQCL8xK7PsV\n" +
            "waWBMAh1PcRTSReJJJ62WrIo/+QQ84FwV5ms7cDCPJCSNDa6v7XEmtN0E2kEnvfA\n" +
            "aAH2JEZjqOHor3n5od0OaP635VexZws7/Dg2jYWEi/jzW6WM7rmYkTZep4xkycoj\n" +
            "0Utj79Kt1xa/pDHMYjHhQcE7qmrL06VtMbwWaoi2e92DhhlySAaGlnmigVMZiwIk\n" +
            "TXyClSCgmJF1B7shgsNrIGCGZI16IvtHHcj6c4kmTCHZD7OMwNKUFFdxe4jHFrHn\n" +
            "xgvc2Lx3ZWZhXw0jKzKnWEU2oRzQwlAnYmeghIALgvAHJXLftAa92E2t27XZ0Yrs\n" +
            "OecVuvWq6QtzNhdutR9L3CP7IS1lzq2iIssi6FcGh3tUFKcFDyQRZm1nq9il1Ezn\n" +
            "NMB96oIxGATen1xMsHxnJZGJ120ZYE7Zp/8WI/sS+4G1HWVL2cetLqRfZKvpYT+V\n" +
            "sFYcTy+qiIRuBREH/t4vz1oH65ewIaxDrKjeQxZ0BDqe7+DGVHWi5w==\n" +
            "-----END RSA PRIVATE KEY-----\n",
            /* password */
            "user2".toCharArray());
        /* Trust client certificate */
        serviceAuthListener.addTrustAnchor(
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBszCCARwCCQDuCh+BWVBk2DANBgkqhkiG9w0BAQUFADAeMQ0wCwYDVQQKDARN\n" +
            "QnVzMQ0wCwYDVQQDDARHcmVnMB4XDTEwMDUxNzE1MTg1N1oXDTExMDUxNzE1MTg1\n" +
            "N1owHjENMAsGA1UECgwETUJ1czENMAsGA1UEAwwER3JlZzCBnzANBgkqhkiG9w0B\n" +
            "AQEFAAOBjQAwgYkCgYEArSd4r62mdaIRG9xZPDAXfImt8e7GTIyXeM8z49Ie1mrQ\n" +
            "h7roHbn931Znzn20QQwFD6pPC7WxStXJVH0iAoYgzzPsXV8kZdbkLGUMPl2GoZY3\n" +
            "xDSD+DA3m6krcXcN7dpHv9OlN0D9Trc288GYuFEENpikZvQhMKPDUAEkucQ95Z8C\n" +
            "AwEAATANBgkqhkiG9w0BAQUFAAOBgQBkYY6zzf92LRfMtjkKs2am9qvjbqXyDJLS\n" +
            "viKmYe1tGmNBUzucDC5w6qpPCTSe23H2qup27///fhUUuJ/ssUnJ+Y77jM/u1O9q\n" +
            "PIn+u89hRmqY5GKHnUSZZkbLB/yrcFEchHli3vLo4FOhVVHwpnwLtWSpfBF9fWcA\n" +
            "7THIAV79Lg==\n" +
            "-----END CERTIFICATE-----");
        RsaAuthListener authListener = new RsaAuthListener(
            /* certificate */
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBszCCARwCCQDuCh+BWVBk2DANBgkqhkiG9w0BAQUFADAeMQ0wCwYDVQQKDARN\n" +
            "QnVzMQ0wCwYDVQQDDARHcmVnMB4XDTEwMDUxNzE1MTg1N1oXDTExMDUxNzE1MTg1\n" +
            "N1owHjENMAsGA1UECgwETUJ1czENMAsGA1UEAwwER3JlZzCBnzANBgkqhkiG9w0B\n" +
            "AQEFAAOBjQAwgYkCgYEArSd4r62mdaIRG9xZPDAXfImt8e7GTIyXeM8z49Ie1mrQ\n" +
            "h7roHbn931Znzn20QQwFD6pPC7WxStXJVH0iAoYgzzPsXV8kZdbkLGUMPl2GoZY3\n" +
            "xDSD+DA3m6krcXcN7dpHv9OlN0D9Trc288GYuFEENpikZvQhMKPDUAEkucQ95Z8C\n" +
            "AwEAATANBgkqhkiG9w0BAQUFAAOBgQBkYY6zzf92LRfMtjkKs2am9qvjbqXyDJLS\n" +
            "viKmYe1tGmNBUzucDC5w6qpPCTSe23H2qup27///fhUUuJ/ssUnJ+Y77jM/u1O9q\n" +
            "PIn+u89hRmqY5GKHnUSZZkbLB/yrcFEchHli3vLo4FOhVVHwpnwLtWSpfBF9fWcA\n" +
            "7THIAV79Lg==\n" +
            "-----END CERTIFICATE-----",
            /* privateKey */
            "-----BEGIN RSA PRIVATE KEY-----\n" +
            "Proc-Type: 4,ENCRYPTED\n" +
            "DEK-Info: AES-128-CBC,0AE4BAB94CEAA7829273DD861B067DBA\n" +
            "\n" +
            "LSJOp+hEzNDDpIrh2UJ+3CauxWRKvmAoGB3r2hZfGJDrCeawJFqH0iSYEX0n0QEX\n" +
            "jfQlV4LHSCoGMiw6uItTof5kHKlbp5aXv4XgQb74nw+2LkftLaTchNs0bW0TiGfQ\n" +
            "XIuDNsmnZ5+CiAVYIKzsPeXPT4ZZSAwHsjM7LFmosStnyg4Ep8vko+Qh9TpCdFX8\n" +
            "w3tH7qRhfHtpo9yOmp4hV9Mlvx8bf99lXSsFJeD99C5GQV2lAMvpfmM8Vqiq9CQN\n" +
            "9OY6VNevKbAgLG4Z43l0SnbXhS+mSzOYLxl8G728C6HYpnn+qICLe9xOIfn2zLjm\n" +
            "YaPlQR4MSjHEouObXj1F4MQUS5irZCKgp4oM3G5Ovzt82pqzIW0ZHKvi1sqz/KjB\n" +
            "wYAjnEGaJnD9B8lRsgM2iLXkqDmndYuQkQB8fhr+zzcFmqKZ1gLRnGQVXNcSPgjU\n" +
            "Y0fmpokQPHH/52u+IgdiKiNYuSYkCfHX1Y3nftHGvWR3OWmw0k7c6+DfDU2fDthv\n" +
            "3MUSm4f2quuiWpf+XJuMB11px1TDkTfY85m1aEb5j4clPGELeV+196OECcMm4qOw\n" +
            "AYxO0J/1siXcA5o6yAqPwPFYcs/14O16FeXu+yG0RPeeZizrdlv49j6yQR3JLa2E\n" +
            "pWiGR6hmnkixzOj43IPJOYXySuFSi7lTMYud4ZH2+KYeK23C2sfQSsKcLZAFATbq\n" +
            "DY0TZHA5lbUiOSUF5kgd12maHAMidq9nIrUpJDzafgK9JrnvZr+dVYM6CiPhiuqJ\n" +
            "bXvt08wtKt68Ymfcx+l64mwzNLS+OFznEeIjLoaHU4c=\n" +
            "-----END RSA PRIVATE KEY-----",
            /* password */
            "123456".toCharArray());
        /* Trust service root certificate */
        authListener.addTrustAnchor(
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICzTCCAjagAwIBAgIBADANBgkqhkiG9w0BAQUFADBUMQswCQYDVQQGEwJBVTET\n" +
            "MBEGA1UECBMKU29tZS1TdGF0ZTEhMB8GA1UEChMYSW50ZXJuZXQgV2lkZ2l0cyBQ\n" +
            "dHkgTHRkMQ0wCwYDVQQDEwRyb290MB4XDTEwMDkwMTIxMDYzOFoXDTEzMDgzMTIx\n" +
            "MDYzOFowVDELMAkGA1UEBhMCQVUxEzARBgNVBAgTClNvbWUtU3RhdGUxITAfBgNV\n" +
            "BAoTGEludGVybmV0IFdpZGdpdHMgUHR5IEx0ZDENMAsGA1UEAxMEcm9vdDCBnzAN\n" +
            "BgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAo5iX69wYP/sMNzfKSTHLxdtFOLkbakDH\n" +
            "h0MPxcXsG53LRdpUpZe1kf4DjTZGZ3PLGW9F+eJtEQ2DP6JWU20lLP4vosKUTZvw\n" +
            "dsrftKkMPiGVqlKTwDvZI2/6tWlmGEUCdRXg1sTHzgQguaXscLCdC/+VzwaASnL1\n" +
            "Pk0GDVpWcFUCAwEAAaOBrjCBqzAdBgNVHQ4EFgQUdDjaTf5WazoaumyVCaQ5tU55\n" +
            "EUYwfAYDVR0jBHUwc4AUdDjaTf5WazoaumyVCaQ5tU55EUahWKRWMFQxCzAJBgNV\n" +
            "BAYTAkFVMRMwEQYDVQQIEwpTb21lLVN0YXRlMSEwHwYDVQQKExhJbnRlcm5ldCBX\n" +
            "aWRnaXRzIFB0eSBMdGQxDTALBgNVBAMTBHJvb3SCAQAwDAYDVR0TBAUwAwEB/zAN\n" +
            "BgkqhkiG9w0BAQUFAAOBgQCHqyne7jWV2tk4VdHazHLdtVMQarGqVN8NOsb1T0zl\n" +
            "RMRXX9UHRoDs968z7ZOAI7i2TwD/JxhwvlQOnVEQT1Wf4zfU4LGsyR7Igb3mwotU\n" +
            "z67MiR3KpBCAVdsiA0Gv4xVCYGZsAqyrl0Ob3BDJe+Ce0eSSfWY3DWI7GEf3tYUz\n" +
            "qQ==\n" +
            "-----END CERTIFICATE-----\n");
        assertEquals(Status.OK, serviceBus.registerAuthListener("ALLJOYN_RSA_KEYX", serviceAuthListener));
        assertEquals(Status.OK, bus.registerAuthListener("ALLJOYN_RSA_KEYX", authListener));
        proxy.Ping("hello");
        assertEquals("user2", authListener.verified);
        assertEquals("greg", serviceAuthListener.verified);
    }

    public void testRsaReject() throws Exception {
        RsaAuthListener serviceAuthListener = new RsaAuthListener(
            /* certificate */
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIC5DCCAk2gAwIBAgIBAzANBgkqhkiG9w0BAQUFADBVMQswCQYDVQQGEwJBVTET\n" +
            "MBEGA1UECBMKU29tZS1TdGF0ZTEhMB8GA1UEChMYSW50ZXJuZXQgV2lkZ2l0cyBQ\n" +
            "dHkgTHRkMQ4wDAYDVQQDEwV1c2VyMTAeFw0xMDA5MDEyMTEyMDhaFw0xMTA5MDEy\n" +
            "MTEyMDhaMFUxCzAJBgNVBAYTAkFVMRMwEQYDVQQIEwpTb21lLVN0YXRlMSEwHwYD\n" +
            "VQQKExhJbnRlcm5ldCBXaWRnaXRzIFB0eSBMdGQxDjAMBgNVBAMTBXVzZXIyMIGf\n" +
            "MA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC2ZX0M5+WzWHm2DDyEjzFwOZ/Pkch9\n" +
            "P0lWtQYTKG6nS6pdGfJPmxrjk/AW4gA8UpsIhqzBznHcM1gUmDjca+/XBulBM9ww\n" +
            "83xFRl+wzPLvQpU0E4WJQMvB+rzWZSwmv8VlxJ43WXHi1CEHfbxxqSKlQl63h4Ew\n" +
            "T8709RbEYN/9YQIDAQABo4HDMIHAMAkGA1UdEwQCMAAwLAYJYIZIAYb4QgENBB8W\n" +
            "HU9wZW5TU0wgR2VuZXJhdGVkIENlcnRpZmljYXRlMB0GA1UdDgQWBBSkfYB0Nx2J\n" +
            "OoxbIzl3Fnfgn3Mn5jBmBgNVHSMEXzBdoVikVjBUMQswCQYDVQQGEwJBVTETMBEG\n" +
            "A1UECBMKU29tZS1TdGF0ZTEhMB8GA1UEChMYSW50ZXJuZXQgV2lkZ2l0cyBQdHkg\n" +
            "THRkMQ0wCwYDVQQDEwRyb290ggECMA0GCSqGSIb3DQEBBQUAA4GBAKcvMddSOTjn\n" +
            "0XQpvTuIAxHwMdu/UUXHHYbVs7OFk7+MLdMrQnqEroWbBt1aI6xB30ZTbrzXnMgP\n" +
            "4QJIF1Dt0ScLaQwUbVOO4+mPetet4otGb3tLTYYakQXDhAGKewm01Qa5wk9SG2PH\n" +
            "AOLB40noJ5BY8ZYo2Z4DNhFFDG0QfAf3\n" +
            "-----END CERTIFICATE-----\n" +
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICLzCCAZigAwIBAgIBAjANBgkqhkiG9w0BAQUFADBUMQswCQYDVQQGEwJBVTET\n" +
            "MBEGA1UECBMKU29tZS1TdGF0ZTEhMB8GA1UEChMYSW50ZXJuZXQgV2lkZ2l0cyBQ\n" +
            "dHkgTHRkMQ0wCwYDVQQDEwRyb290MB4XDTEwMDkwMTIxMTAyNloXDTExMDkwMTIx\n" +
            "MTAyNlowVTELMAkGA1UEBhMCQVUxEzARBgNVBAgTClNvbWUtU3RhdGUxITAfBgNV\n" +
            "BAoTGEludGVybmV0IFdpZGdpdHMgUHR5IEx0ZDEOMAwGA1UEAxMFdXNlcjEwgZ8w\n" +
            "DQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAMt+ckt5kpLzIY3lVxS0aQi5m+mM5Kgv\n" +
            "mnmHtUkbs8M7NDqQb7hO58+1zbq/LC+1PsaRkQ+iFLPmI2YvqlKGnvyqWjEzfzol\n" +
            "GAQlpTdQoQkatwm8hHzNoVhY48wP5+bu7eqBMIIpAGRNPwxizercohcQZksOpycy\n" +
            "bxgOX2LtzgBjAgMBAAGjEDAOMAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEFBQAD\n" +
            "gYEANi9nG202uyhGpN2tt699A5tPF7apic7+vfkeA5i7zxvynqUj5mBhVbVheyOu\n" +
            "SSkmNG4xGMAkcBK4PevSpHMNnTTcTTNuA22QWhnndm+VIfcIHSSWmqm1KiTZ3LM3\n" +
            "6OLT8N+OVZEHVYDycb6hbkl6SkgkeXmpi+vqGFpx/Q+aj4o=\n" +
            "-----END CERTIFICATE-----\n",
            /* privateKey */
            "-----BEGIN RSA PRIVATE KEY-----\n" +
            "Proc-Type: 4,ENCRYPTED\n" +
            "DEK-Info: DES-EDE3-CBC,B2C36955B455FEFA\n" +
            "\n" +
            "sb3V1Egfy5taVkS5jGUSdsdm51Aw65e8cqSr2E/2pcfbWzZmVwFx9igxZgqujPCh\n" +
            "4lIvyueeVcVA92o58vmnsAdBPss4zSAOMlDxy/JEpbuGHiDVO1nsKWiKKmMimcv6\n" +
            "Z3N69KbA+2hP7DODen7Qs/57a44I6R7HXGfwTalWPc/Q25VZGMhhtAJWDQ634zwo\n" +
            "NPm1Q5qm2t3ZWQG/d1lTRsYmYGdGmp5xpeTRC/ghLei5HYAW72jO0HEvypjZP86g\n" +
            "2ghCazw/r2nLvOMoMjjTDddqJTUzuNOVEzka2A6u7htrRR933uBcsmQCL8xK7PsV\n" +
            "waWBMAh1PcRTSReJJJ62WrIo/+QQ84FwV5ms7cDCPJCSNDa6v7XEmtN0E2kEnvfA\n" +
            "aAH2JEZjqOHor3n5od0OaP635VexZws7/Dg2jYWEi/jzW6WM7rmYkTZep4xkycoj\n" +
            "0Utj79Kt1xa/pDHMYjHhQcE7qmrL06VtMbwWaoi2e92DhhlySAaGlnmigVMZiwIk\n" +
            "TXyClSCgmJF1B7shgsNrIGCGZI16IvtHHcj6c4kmTCHZD7OMwNKUFFdxe4jHFrHn\n" +
            "xgvc2Lx3ZWZhXw0jKzKnWEU2oRzQwlAnYmeghIALgvAHJXLftAa92E2t27XZ0Yrs\n" +
            "OecVuvWq6QtzNhdutR9L3CP7IS1lzq2iIssi6FcGh3tUFKcFDyQRZm1nq9il1Ezn\n" +
            "NMB96oIxGATen1xMsHxnJZGJ120ZYE7Zp/8WI/sS+4G1HWVL2cetLqRfZKvpYT+V\n" +
            "sFYcTy+qiIRuBREH/t4vz1oH65ewIaxDrKjeQxZ0BDqe7+DGVHWi5w==\n" +
            "-----END RSA PRIVATE KEY-----\n",
            /* password */
            "user2".toCharArray());
        /* Trust client certificate */
        serviceAuthListener.addTrustAnchor(
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBszCCARwCCQDuCh+BWVBk2DANBgkqhkiG9w0BAQUFADAeMQ0wCwYDVQQKDARN\n" +
            "QnVzMQ0wCwYDVQQDDARHcmVnMB4XDTEwMDUxNzE1MTg1N1oXDTExMDUxNzE1MTg1\n" +
            "N1owHjENMAsGA1UECgwETUJ1czENMAsGA1UEAwwER3JlZzCBnzANBgkqhkiG9w0B\n" +
            "AQEFAAOBjQAwgYkCgYEArSd4r62mdaIRG9xZPDAXfImt8e7GTIyXeM8z49Ie1mrQ\n" +
            "h7roHbn931Znzn20QQwFD6pPC7WxStXJVH0iAoYgzzPsXV8kZdbkLGUMPl2GoZY3\n" +
            "xDSD+DA3m6krcXcN7dpHv9OlN0D9Trc288GYuFEENpikZvQhMKPDUAEkucQ95Z8C\n" +
            "AwEAATANBgkqhkiG9w0BAQUFAAOBgQBkYY6zzf92LRfMtjkKs2am9qvjbqXyDJLS\n" +
            "viKmYe1tGmNBUzucDC5w6qpPCTSe23H2qup27///fhUUuJ/ssUnJ+Y77jM/u1O9q\n" +
            "PIn+u89hRmqY5GKHnUSZZkbLB/yrcFEchHli3vLo4FOhVVHwpnwLtWSpfBF9fWcA\n" +
            "7THIAV79Lg==\n" +
            "-----END CERTIFICATE-----");
        RsaAuthListener authListener = new RsaAuthListener(
            /* certificate */
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBszCCARwCCQDuCh+BWVBk2DANBgkqhkiG9w0BAQUFADAeMQ0wCwYDVQQKDARN\n" +
            "QnVzMQ0wCwYDVQQDDARHcmVnMB4XDTEwMDUxNzE1MTg1N1oXDTExMDUxNzE1MTg1\n" +
            "N1owHjENMAsGA1UECgwETUJ1czENMAsGA1UEAwwER3JlZzCBnzANBgkqhkiG9w0B\n" +
            "AQEFAAOBjQAwgYkCgYEArSd4r62mdaIRG9xZPDAXfImt8e7GTIyXeM8z49Ie1mrQ\n" +
            "h7roHbn931Znzn20QQwFD6pPC7WxStXJVH0iAoYgzzPsXV8kZdbkLGUMPl2GoZY3\n" +
            "xDSD+DA3m6krcXcN7dpHv9OlN0D9Trc288GYuFEENpikZvQhMKPDUAEkucQ95Z8C\n" +
            "AwEAATANBgkqhkiG9w0BAQUFAAOBgQBkYY6zzf92LRfMtjkKs2am9qvjbqXyDJLS\n" +
            "viKmYe1tGmNBUzucDC5w6qpPCTSe23H2qup27///fhUUuJ/ssUnJ+Y77jM/u1O9q\n" +
            "PIn+u89hRmqY5GKHnUSZZkbLB/yrcFEchHli3vLo4FOhVVHwpnwLtWSpfBF9fWcA\n" +
            "7THIAV79Lg==\n" +
            "-----END CERTIFICATE-----",
            /* privateKey */
            "-----BEGIN RSA PRIVATE KEY-----\n" +
            "Proc-Type: 4,ENCRYPTED\n" +
            "DEK-Info: AES-128-CBC,0AE4BAB94CEAA7829273DD861B067DBA\n" +
            "\n" +
            "LSJOp+hEzNDDpIrh2UJ+3CauxWRKvmAoGB3r2hZfGJDrCeawJFqH0iSYEX0n0QEX\n" +
            "jfQlV4LHSCoGMiw6uItTof5kHKlbp5aXv4XgQb74nw+2LkftLaTchNs0bW0TiGfQ\n" +
            "XIuDNsmnZ5+CiAVYIKzsPeXPT4ZZSAwHsjM7LFmosStnyg4Ep8vko+Qh9TpCdFX8\n" +
            "w3tH7qRhfHtpo9yOmp4hV9Mlvx8bf99lXSsFJeD99C5GQV2lAMvpfmM8Vqiq9CQN\n" +
            "9OY6VNevKbAgLG4Z43l0SnbXhS+mSzOYLxl8G728C6HYpnn+qICLe9xOIfn2zLjm\n" +
            "YaPlQR4MSjHEouObXj1F4MQUS5irZCKgp4oM3G5Ovzt82pqzIW0ZHKvi1sqz/KjB\n" +
            "wYAjnEGaJnD9B8lRsgM2iLXkqDmndYuQkQB8fhr+zzcFmqKZ1gLRnGQVXNcSPgjU\n" +
            "Y0fmpokQPHH/52u+IgdiKiNYuSYkCfHX1Y3nftHGvWR3OWmw0k7c6+DfDU2fDthv\n" +
            "3MUSm4f2quuiWpf+XJuMB11px1TDkTfY85m1aEb5j4clPGELeV+196OECcMm4qOw\n" +
            "AYxO0J/1siXcA5o6yAqPwPFYcs/14O16FeXu+yG0RPeeZizrdlv49j6yQR3JLa2E\n" +
            "pWiGR6hmnkixzOj43IPJOYXySuFSi7lTMYud4ZH2+KYeK23C2sfQSsKcLZAFATbq\n" +
            "DY0TZHA5lbUiOSUF5kgd12maHAMidq9nIrUpJDzafgK9JrnvZr+dVYM6CiPhiuqJ\n" +
            "bXvt08wtKt68Ymfcx+l64mwzNLS+OFznEeIjLoaHU4c=\n" +
            "-----END RSA PRIVATE KEY-----",
            /* password */
            "123456".toCharArray());
        /* Only trust ourself */
        authListener.addTrustAnchor(
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBszCCARwCCQDuCh+BWVBk2DANBgkqhkiG9w0BAQUFADAeMQ0wCwYDVQQKDARN\n" +
            "QnVzMQ0wCwYDVQQDDARHcmVnMB4XDTEwMDUxNzE1MTg1N1oXDTExMDUxNzE1MTg1\n" +
            "N1owHjENMAsGA1UECgwETUJ1czENMAsGA1UEAwwER3JlZzCBnzANBgkqhkiG9w0B\n" +
            "AQEFAAOBjQAwgYkCgYEArSd4r62mdaIRG9xZPDAXfImt8e7GTIyXeM8z49Ie1mrQ\n" +
            "h7roHbn931Znzn20QQwFD6pPC7WxStXJVH0iAoYgzzPsXV8kZdbkLGUMPl2GoZY3\n" +
            "xDSD+DA3m6krcXcN7dpHv9OlN0D9Trc288GYuFEENpikZvQhMKPDUAEkucQ95Z8C\n" +
            "AwEAATANBgkqhkiG9w0BAQUFAAOBgQBkYY6zzf92LRfMtjkKs2am9qvjbqXyDJLS\n" +
            "viKmYe1tGmNBUzucDC5w6qpPCTSe23H2qup27///fhUUuJ/ssUnJ+Y77jM/u1O9q\n" +
            "PIn+u89hRmqY5GKHnUSZZkbLB/yrcFEchHli3vLo4FOhVVHwpnwLtWSpfBF9fWcA\n" +
            "7THIAV79Lg==\n" +
            "-----END CERTIFICATE-----");
        assertEquals(Status.OK, serviceBus.registerAuthListener("ALLJOYN_RSA_KEYX", serviceAuthListener));
        assertEquals(Status.OK, bus.registerAuthListener("ALLJOYN_RSA_KEYX", authListener));
        try {
            proxy.Ping("hello");
        } catch (BusException ex) {
        }
        assertEquals("user2", authListener.rejected);
        assertEquals("greg", serviceAuthListener.verified);
    }
}
