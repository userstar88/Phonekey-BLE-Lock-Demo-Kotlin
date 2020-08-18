package com.userstar.phonekeybasicfunctiondemokotlin.services.userstar;

import android.annotation.SuppressLint;

import java.nio.charset.Charset;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class AES {

    public static String encrypt(String content, String password) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            byte[] byteContent = content.getBytes();
            cipher.init(1, genKey(password));
            byte[] result = cipher.doFinal(byteContent);
            return parseByte2HexStr(result);
        } catch (Exception var5) {
            var5.printStackTrace();
            return null;
        }
    }

    public static String decrypt(String content, String password) {
        try {
            byte[] decryptFrom = parseHexStr2Byte(content);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(2, genKey(password));
            byte[] result = cipher.doFinal(decryptFrom);
            return new String(result);
        } catch (Exception var5) {
            var5.printStackTrace();
            return null;
        }
    }

    @SuppressLint({"DeletedProvider"})
    private static SecretKeySpec genKey(String strKey) {
        byte[] enCodeFormat = new byte[]{0};

        try {
            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG", "Crypto");
            secureRandom.setSeed(strKey.getBytes());
            kgen.init(128, secureRandom);
            SecretKey secretKey = kgen.generateKey();
            enCodeFormat = secretKey.getEncoded();
        } catch (Exception var5) {
            var5.printStackTrace();
        }

        return new SecretKeySpec(enCodeFormat, "AES");
    }

    private static String parseByte2HexStr(byte[] buf) {
        StringBuffer sb = new StringBuffer();

        for(int i = 0; i < buf.length; ++i) {
            String hex = Integer.toHexString(buf[i] & 255);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }

            sb.append(hex.toUpperCase());
        }

        return sb.toString();
    }

    private static byte[] parseHexStr2Byte(String hexStr) {
        if (hexStr.length() < 1) {
            return null;
        } else {
            byte[] result = new byte[hexStr.length() / 2];

            for(int i = 0; i < hexStr.length() / 2; ++i) {
                int high = Integer.parseInt(hexStr.substring(i * 2, i * 2 + 1), 16);
                int low = Integer.parseInt(hexStr.substring(i * 2 + 1, i * 2 + 2), 16);
                result[i] = (byte)(high * 16 + low);
            }

            return result;
        }
    }

    public static String Encrypt2(String sSrc, String sKey) throws Exception {
        byte[] raw = sKey.getBytes(Charset.forName("ISO_8859_1"));
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(1, skeySpec);
        byte[] encrypted = cipher.doFinal(sSrc.getBytes(Charset.forName("ISO_8859_1")));
        String str = parseByte2HexStr(encrypted);
        return str;
    }

    public static String Decrypt2(String sSrc, String sKey) throws Exception {
        try {
            byte[] raw = sKey.getBytes(Charset.forName("ISO_8859_1"));
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(2, skeySpec);
            byte[] encrypted1 = parseHexStr2Byte(sSrc);

            try {
                byte[] original = cipher.doFinal(encrypted1);
                String originalString = new String(original);
                return originalString;
            } catch (Exception var8) {
                System.out.println(var8.toString());
                return null;
            }
        } catch (Exception var9) {
            System.out.println(var9.toString());
            return null;
        }
    }

    public static String parseHexStr2Ascii(String str) {
        int li_len = str.length();
        String ls_return = "";
        str = str.toUpperCase();

        for(int i = 0; i < li_len / 2; ++i) {
            String ls_char = str.substring(i * 2, i * 2 + 2);
            int AcsiiCode = Integer.parseInt(ls_char, 16);
            char Asc2Char = (char)AcsiiCode;
            ls_return = ls_return + Character.toString(Asc2Char);
        }

        return ls_return;
    }

    public static String parseAscii2HexStr(String str) {
        int li_len = str.length();
        String ls_return = "";
        if (li_len == 0) {
            return "";
        } else {
            char[] ls_char = str.toCharArray();

            for(int i = 0; i < li_len; ++i) {
                int Char2AsciiCode = ls_char[i];
                ls_return = ls_return + Integer.toHexString(Char2AsciiCode).toUpperCase();
            }

            return ls_return;
        }
    }
}
