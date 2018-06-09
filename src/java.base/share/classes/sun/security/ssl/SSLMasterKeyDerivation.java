/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.security.ssl;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import sun.security.internal.spec.TlsMasterSecretParameterSpec;
import sun.security.ssl.CipherSuite.HashAlg;
import static sun.security.ssl.CipherSuite.HashAlg.H_NONE;

enum SSLMasterKeyDerivation implements SSLKeyDerivationGenerator {
    SSL30       ("kdf_ssl30", S30MasterSecretKeyDerivationGenerator.instance),
    TLS10       ("kdf_tls10", T10MasterSecretKeyDerivationGenerator.instance),
    TLS12       ("kdf_tls12", T12MasterSecretKeyDerivationGenerator.instance),
    TLS13       ("kdf_tls13", null);

    final String name;
    final SSLKeyDerivationGenerator keyDerivationGenerator;

    SSLMasterKeyDerivation(String name,
            SSLKeyDerivationGenerator keyDerivationGenerator) {
        this.name = name;
        this.keyDerivationGenerator = keyDerivationGenerator;
    }

    static SSLMasterKeyDerivation valueOf(ProtocolVersion protocolVersion) {
        switch (protocolVersion) {
            case SSL30:
                return SSLMasterKeyDerivation.SSL30;
            case TLS10:
            case TLS11:
            case DTLS10:
                return SSLMasterKeyDerivation.TLS10;
            case TLS12:
            case DTLS12:
                return SSLMasterKeyDerivation.TLS12;
            case TLS13:
                return SSLMasterKeyDerivation.TLS13;
            default:
                return null;
        }
    }

    @Override
    public SSLKeyDerivation createKeyDerivation(HandshakeContext context,
            SecretKey secretKey) throws IOException {
        return keyDerivationGenerator.createKeyDerivation(context, secretKey);
    }

    private static final class S30MasterSecretKeyDerivationGenerator
            implements SSLKeyDerivationGenerator {
        private static final S30MasterSecretKeyDerivationGenerator instance =
                new S30MasterSecretKeyDerivationGenerator();

        // Prevent instantiation of this class.
        private S30MasterSecretKeyDerivationGenerator() {
            // blank
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
            HandshakeContext context, SecretKey secretKey) throws IOException {
            return new LegacyMasterKeyDerivation(context, secretKey);
        }
    }


    private static final class T10MasterSecretKeyDerivationGenerator
            implements SSLKeyDerivationGenerator {
        private static final T10MasterSecretKeyDerivationGenerator instance =
                new T10MasterSecretKeyDerivationGenerator();

        // Prevent instantiation of this class.
        private T10MasterSecretKeyDerivationGenerator() {
            // blank
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
            HandshakeContext context, SecretKey secretKey) throws IOException {
            return new LegacyMasterKeyDerivation(context, secretKey);
        }
    }

    private static final class T12MasterSecretKeyDerivationGenerator
            implements SSLKeyDerivationGenerator {
        private static final T12MasterSecretKeyDerivationGenerator instance =
                new T12MasterSecretKeyDerivationGenerator();

        // Prevent instantiation of this class.
        private T12MasterSecretKeyDerivationGenerator() {
            // blank
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
            HandshakeContext context, SecretKey secretKey) throws IOException {
            return new LegacyMasterKeyDerivation(context, secretKey);
        }

    }

    private static final
            class LegacyMasterKeyDerivation implements SSLKeyDerivation {

        final HandshakeContext context;
        final SecretKey preMasterSecret;

        LegacyMasterKeyDerivation(
                HandshakeContext context, SecretKey preMasterSecret) {
            this.context = context;
            this.preMasterSecret = preMasterSecret;
        }

        @Override
        @SuppressWarnings("deprecation")
        public SecretKey deriveKey(String algorithm,
                AlgorithmParameterSpec params) throws IOException {

            CipherSuite cipherSuite = context.negotiatedCipherSuite;
            ProtocolVersion protocolVersion = context.negotiatedProtocol;

            // What algs/params do we need to use?
            String masterAlg;
            HashAlg hashAlg;

            byte majorVersion = protocolVersion.major;
            byte minorVersion = protocolVersion.minor;
            if (protocolVersion.isDTLS) {
                // Use TLS version number for DTLS key calculation
                if (protocolVersion.id == ProtocolVersion.DTLS10.id) {
                    majorVersion = ProtocolVersion.TLS11.major;
                    minorVersion = ProtocolVersion.TLS11.minor;

                    masterAlg = "SunTlsMasterSecret";
                    hashAlg = H_NONE;
                } else {    // DTLS 1.2
                    majorVersion = ProtocolVersion.TLS12.major;
                    minorVersion = ProtocolVersion.TLS12.minor;

                    masterAlg = "SunTls12MasterSecret";
                    hashAlg = cipherSuite.hashAlg;
                }
            } else {
                if (protocolVersion.id >= ProtocolVersion.TLS12.id) {
                    masterAlg = "SunTls12MasterSecret";
                    hashAlg = cipherSuite.hashAlg;
                } else {
                    masterAlg = "SunTlsMasterSecret";
                    hashAlg = H_NONE;
                }
            }

            TlsMasterSecretParameterSpec spec;
            if (context.handshakeSession.useExtendedMasterSecret) {
                // reset to use the extended master secret algorithm
                masterAlg = "SunTlsExtendedMasterSecret";

                // For the session hash, use the handshake messages up to and
                // including the ClientKeyExchange message.
                context.handshakeHash.utilize();
                byte[] sessionHash = context.handshakeHash.digest();
                spec = new TlsMasterSecretParameterSpec(
                        preMasterSecret,
                        (majorVersion & 0xFF), (minorVersion & 0xFF),
                        sessionHash,
                        hashAlg.name, hashAlg.hashLength, hashAlg.blockSize);
            } else {
                spec = new TlsMasterSecretParameterSpec(
                        preMasterSecret,
                        (majorVersion & 0xFF), (minorVersion & 0xFF),
                        context.clientHelloRandom.randomBytes,
                        context.serverHelloRandom.randomBytes,
                        hashAlg.name, hashAlg.hashLength, hashAlg.blockSize);
            }

            try {
                KeyGenerator kg = JsseJce.getKeyGenerator(masterAlg);
                kg.init(spec);
                return kg.generateKey();
            } catch (InvalidAlgorithmParameterException |
                    NoSuchAlgorithmException iae) {
                // unlikely to happen, otherwise, must be a provider exception
                //
                // For RSA premaster secrets, do not signal a protocol error
                // due to the Bleichenbacher attack. See comments further down.
                if (SSLLogger.isOn && SSLLogger.isOn("handshake")) {
                    SSLLogger.fine("RSA master secret generation error.", iae);
                }
                throw new ProviderException(iae);
            }
        }
    }
}
