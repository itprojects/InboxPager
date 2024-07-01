/*
 * InboxPager, an android email client.
 * Copyright (C) 2024  ITPROJECTS
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **/
package net.inbox.server;

import android.content.Context;
import android.util.Base64;

import net.inbox.pager.R;

import java.util.HashMap;

import gnu.crypto.mode.IMode;
import gnu.crypto.mode.ModeFactory;
import gnu.crypto.pad.IPad;
import gnu.crypto.pad.PadFactory;

public class EndToEnd {

    public final static String[] cipher_types = new String[]{ "AES", "Twofish" };
    public final static String[] cipher_modes = new String[]{ "CBC", "CTR", "ECB", "ICM", "OFB" };
    public final static String[] cipher_paddings = new String[]{ "PKCS7", "TBC" };

    private final static int block_size = 16;// of message
    private final static int key_size = 32;// char length of String key
    private static String cipher_name = "AES";
    private static String cipher_mode = "CBC";
    private static String cipher_padding = "PKCS7";

    /**
     * Text encryption. 32 chars key minimum.
     * The text is first decoded from BASE64 transfer encoding.
     * Supported ciphers: AES, Twofish
     * Supported modes: CBC, CTR, ECB, ICM, OFB
     * Supported padding: PKCS7, TBC
     **/
    public static String encrypt(Context ctx, String s_key, String s_data, String cypher_type,
                                  String cypher_mode, String cypher_padding) throws Exception {

        byte[] key = pad(key_size, s_key.getBytes());
        byte[] data = pad(block_size, s_data.getBytes());
        byte[] encrypted_data = new byte[data.length];

        cipher_name = cypher_type;
        cipher_mode = cypher_mode;
        cipher_padding = cypher_padding;

        try {
            HashMap<Object, Object> attributes = new HashMap<>();
            attributes.put(IMode.KEY_MATERIAL, key);
            attributes.put(IMode.CIPHER_BLOCK_SIZE, block_size);
            attributes.put(IMode.STATE, IMode.ENCRYPTION);

            IMode mode = ModeFactory.getInstance(cipher_mode, cipher_name, block_size);
            mode.init(attributes);

            for (int i = 0; i < data.length; i += mode.currentBlockSize())
                mode.update(data, i, encrypted_data, i);
            return Base64.encodeToString(encrypted_data, Base64.DEFAULT);
        } catch (Exception e) {
            throw new Exception(String.format(ctx.getString(R.string.crypto_failed_encryption),
                    cipher_name, cipher_mode, cipher_padding));
        }
    }

    /**
     * Text decryption. 32 chars key minimum.
     * The text is first decoded from BASE64 transfer encoding.
     * Supported ciphers: AES, Twofish
     * Supported modes: CBC, CTR, ECB, ICM, OFB
     * Supported padding: PKCS7, TBC
     **/
    public static String decrypt(Context ctx, String s_key, String s_data, String cypher_type,
                                  String cypher_mode, String cypher_padding) throws Exception {

        byte[] key = pad(key_size, s_key.getBytes());
        byte[] data = Base64.decode(s_data, Base64.DEFAULT);

        cipher_name = cypher_type;
        cipher_mode = cypher_mode;
        cipher_padding = cypher_padding;

        try {
            HashMap<Object, Object> attributes = new HashMap<>();
            attributes.put(IMode.KEY_MATERIAL, key);
            attributes.put(IMode.CIPHER_BLOCK_SIZE, block_size);
            attributes.put(IMode.STATE, IMode.DECRYPTION);

            IMode mode = ModeFactory.getInstance(cipher_mode, cipher_name, block_size);
            mode.init(attributes);

            byte[] decrypted_data = new byte[data.length];
            for (int i = 0; i < data.length; i += mode.currentBlockSize())
                mode.update(data, i, decrypted_data, i);
            int n = unpad(ctx, decrypted_data);
            if (n == -1) return null;
            byte[] unpadded_data = new byte[decrypted_data.length - n];
            System.arraycopy(decrypted_data, 0, unpadded_data, 0, unpadded_data.length);
            return new String(unpadded_data);
        } catch (Exception e) {
            throw new Exception(String.format(ctx.getString(R.string.crypto_failed_decryption),
                    cipher_name, cipher_mode, cipher_padding));
        }
    }

    /**
     * Adding spaces at the end of the data, or key.
     * Required by cryptography algorithms.
     */
    private static byte[] pad(int data_size, byte[] data) {
        IPad padding = PadFactory.getInstance(cipher_padding);
        padding.init(data_size);
        byte[] pad = padding.pad(data, 0, data.length);
        byte[] padded_data = new byte[data.length + pad.length];
        System.arraycopy(data, 0, padded_data, 0, data.length);
        System.arraycopy(pad, 0, padded_data, data.length, pad.length);
        return padded_data;
    }

    /**
     * Removing spaces at the end of the data, or key.
     * Required by cryptography algorithms.
     */
    private static int unpad(Context ctx, byte[] data) throws Exception {
        try {
            IPad padding = PadFactory.getInstance(cipher_padding);
            padding.init(block_size);
            return padding.unpad(data, 0, data.length);
        } catch (Exception e) {
            throw new Exception(String.format(ctx
                    .getString(R.string.crypto_failed_unpadding), cipher_padding));
        }
    }
}
