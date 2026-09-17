package com.stagecore.player;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

/**
 * Hardware-backed when available Android Keystore identity used by the official
 * StageCore device channel. The public key is exported as an uncompressed P-256
 * X9.63 point because that is the wire format accepted by StageCore.
 */
public final class StageCoreDeviceIdentity {
    public static final String KEY_ALGORITHM = "P256_X963_SHA256";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String PREFIX = "stagecore.tablet.identity.";

    private final String deviceId;
    private final String alias;

    public StageCoreDeviceIdentity(String deviceId) {
        this.deviceId = deviceId == null ? "" : deviceId.trim();
        this.alias = PREFIX + Integer.toHexString(this.deviceId.hashCode());
    }

    public synchronized String publicKeyBase64() throws Exception {
        ECPublicKey publicKey = (ECPublicKey) keyPair().getPublic();
        byte[] x = unsigned32(publicKey.getW().getAffineX().toByteArray());
        byte[] y = unsigned32(publicKey.getW().getAffineY().toByteArray());
        ByteBuffer point = ByteBuffer.allocate(65);
        point.put((byte) 0x04);
        point.put(x);
        point.put(y);
        return Base64.encodeToString(point.array(), Base64.NO_WRAP);
    }

    public synchronized String signAuthentication(String challengeId, String nonceBase64) throws Exception {
        String message = "StageCore Companion Authentication v1\n"
                + deviceId + "\n"
                + challengeId + "\n"
                + nonceBase64;
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign((PrivateKey) keyPair().getPrivate());
        signer.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP);
    }

    private KeyPair keyPair() throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        if (store.containsAlias(alias)) {
            KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) store.getEntry(alias, null);
            return new KeyPair(entry.getCertificate().getPublicKey(), entry.getPrivateKey());
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY
        )
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build();
        generator.initialize(spec);
        return generator.generateKeyPair();
    }

    private static byte[] unsigned32(byte[] input) {
        if (input.length == 32) return input;
        if (input.length == 33 && input[0] == 0) return Arrays.copyOfRange(input, 1, 33);
        byte[] out = new byte[32];
        int copy = Math.min(32, input.length);
        System.arraycopy(input, input.length - copy, out, 32 - copy, copy);
        return out;
    }
}
