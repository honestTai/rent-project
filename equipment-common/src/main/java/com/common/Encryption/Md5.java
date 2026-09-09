package com.common.Encryption;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Md5 签名算法
 */
public class Md5 {

    /**
     * 登录密码MD5加密
     */
    public static String md5String (String string) {
        StringBuffer hexString = new StringBuffer();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(string.getBytes());
            byte[] hash = md.digest();
            for (int i = 0; i < hash.length; i++) {
                if ((0xff & hash[i]) < 0x10) {
                    hexString.append("0" + Integer.toHexString((0xFF & hash[i])));
                } else {
                    hexString.append(Integer.toHexString(0xFF & hash[i]));
                }
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return string;
    }

}
