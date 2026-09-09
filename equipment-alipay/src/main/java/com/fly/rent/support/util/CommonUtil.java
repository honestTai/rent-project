package com.fly.rent.support.util;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;

/**
 * 公共工具类
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Component
public class CommonUtil {

    /**
     * 获取随机字符串
     * @return 随机字符串
     */
    public static String getNonceStr() {
        char[] dict = {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
        StringBuffer sb = new StringBuffer();
        Random random = new Random();
        for (int i = 0; i < 31; i++) {
            sb.append(String.valueOf(dict[(int) (Math.random() * 36)]));
        }
        return sb.toString();
    }

    /**
     * 获取订单号
     * @return 订单号
     */
    public static String getNo() {
        String l1= RandomUtils.nextInt(0,9)+"";
        String l2= RandomUtils.nextInt(0,9)+"";
        String res=System.currentTimeMillis()+l1+l2;
        return res;
    }

    /**
     * 获取随机账号
     * @return 随机账号
     */
    public static String getRandomAcco() {
        return System.currentTimeMillis()+String.valueOf(RandomUtils.nextInt(0,9))+String.valueOf(RandomUtils.nextInt(0,9));
    }

    /**
     * 字符串转数组
     * @param goodIds 字符串
     * @return 数组
     */
    public static String[] strToArr(String goodIds) {
        if (null==goodIds){
            return new String[0];
        }
        return goodIds.split(",");
    }

    /**
     * 生成订单编号
     * @return 订单编号
     */
    public String createNo() {
        String l1= RandomUtils.nextInt(0,9)+"";
        String l2= RandomUtils.nextInt(0,9)+"";
        String res=System.currentTimeMillis()+l1+l2;
        return res;
    }
	
	/**
     * 将逗号分隔的字符拆成数组
     * @param ids ID字符串
     * @return 数组
     */
    public String[] getArr(String ids){
        if(null!=ids){
            String[] arr=ids.split(",");
            return arr;
        }
        return new String[0];
    }

    /**
     * 生成 32 位随机字符串，包含：数字、字母大小写
     * @return 32位随机字符串
     */
    public String gen32RandomString() {
        char[] dict = {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
        StringBuffer sb = new StringBuffer();
        Random random = new Random();
        for (int i = 0; i < 31; i++) {
            sb.append(String.valueOf(dict[(int) (Math.random() * 36)]));
        }
        return sb.toString();
    }


    /**
     * MD5 签名
     *
     * @param str 字符串
     * @return 签名后的字符串信息
     */
    public String encodeMD5(String str) {
        return MD5Encode(str, "UTF-8");
    }

    /**
     * 将 XML 转化为 map
     *
     * @param strxml XML字符串
     * @return Map对象
     * @throws IOException IO异常
     */
    public Map transferXmlToMap(String strxml) throws IOException {
        strxml = strxml.replaceFirst("encoding=\".*\"", "encoding=\"UTF-8\"");
        if (null == strxml || "".equals(strxml)) {
            return null;
        }
        Map m = new HashMap();
        InputStream in = new ByteArrayInputStream(strxml.getBytes("UTF-8"));
        SAXBuilder builder = new SAXBuilder();
        Document doc = null;
        try {
            doc = builder.build(in);
        } catch (JDOMException e) {
            throw new IOException(e.getMessage()); // 统一转化为 IO 异常输出
        }
        // 解析 DOM
        Element root = doc.getRootElement();
        List list = root.getChildren();
        Iterator it = list.iterator();
        while (it.hasNext()) {
            Element e = (Element) it.next();
            String k = e.getName();
            String v = "";
            List children = e.getChildren();
            if (children.isEmpty()) {
                v = e.getTextNormalize();
            } else {
                v = getChildrenText(children);
            }
            m.put(k, v);
        }
        //关闭流
        in.close();
        return m;
    }

    /**
     * 将 Map 转化为 XML
     *
     * @param map Map对象
     * @return XML字符串
     */
    public String transferMapToXml(SortedMap<String, Object> map) {
        StringBuffer sb = new StringBuffer();
        sb.append("<xml>");
        for (String key : map.keySet()) {
            sb.append("<").append(key).append(">")
                    .append(map.get(key))
                    .append("</").append(key).append(">");
        }
        return sb.append("</xml>").toString();
    }

    /**
     * SHA1加密
     * @param str 字符串
     * @return 加密后的大写字符串
     */
    public static String SHA1(String str){
        String sign = DigestUtils.sha1Hex(str);
        return sign.toUpperCase();
    }

    // 辅助 transferXmlToMap 方法递归提取子节点数据
    private String getChildrenText(List<Element> children) {
        StringBuffer sb = new StringBuffer();
        if (!children.isEmpty()) {
            Iterator<Element> it = children.iterator();
            while (it.hasNext()) {
                Element e = (Element) it.next();
                String name = e.getName();
                String value = e.getTextNormalize();
                List<Element> list = e.getChildren();
                sb.append("<" + name + ">");
                if (!list.isEmpty()) {
                    sb.append(getChildrenText(list));
                }
                sb.append(value);
                sb.append("</" + name + ">");
            }
        }
        return sb.toString();
    }

    /**
     * MD5编码
     * @param origin 原始字符串
     * @param charsetname 字符集
     * @return 编码后的字符串
     */
    public static String MD5Encode(String origin, String charsetname) {
        String resultString = null;
        try {
            resultString = new String(origin);
            MessageDigest md = MessageDigest.getInstance("MD5");
            if (charsetname == null || "".equals(charsetname))
                resultString = byteArrayToHexString(md.digest(resultString
                        .getBytes()));
            else
                resultString = byteArrayToHexString(md.digest(resultString
                        .getBytes(charsetname)));
        } catch (Exception exception) {
        }
        return resultString;
    }

    private static String byteArrayToHexString(byte b[]) {
        StringBuffer resultSb = new StringBuffer();
        for (int i = 0; i < b.length; i++)
            resultSb.append(byteToHexString(b[i]));

        return resultSb.toString();
    }

    private static final String hexDigits[] = {"0", "1", "2", "3", "4", "5",
            "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};

    private static String byteToHexString(byte b) {
        int n = b;
        if (n < 0)
            n += 256;
        int d1 = n / 16;
        int d2 = n % 16;
        return hexDigits[d1] + hexDigits[d2];
    }
}

