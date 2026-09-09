package com.common.Util.FlowString;


import lombok.NonNull;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;

import java.text.ParseException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import  static com.common.Util.TimeUtil.TimeUtil.*;

/**
 * 流水号生成方法
 */
public class FlowString {

    /**
     * 订单流水号
     * @param sendTime 发货时间
     * @param customerPhoneNum 收货人手机号后4位
     * @param equipment 前四位
     * @param interval 时间间隔
     * @param runt 租金
     * @return 返回订单流水号（主键）
     */
    public static String orderString(Date sendTime,String customerPhoneNum,String equipment,Integer interval,double runt) throws ParseException {
        return timeStamp(sendTime)+customerPhoneNum+equipment+interval+ Math.round(runt);
    }


    /**
     * 设备条形码生成方法 中文取首字母大写，英文取所有全部大写，剩余数字与字母中间填充0000
     */
    public static String wordString(String deviceName) {
        String name = deviceName.replace("(","").replace(")","").replace("号","");
        String endString=name.substring(name.length() -2,name.length());
        String convert = "";
        for (int i = 0; i < name.length(); i++) {
            char word = name.charAt(i);
            String[] pinyin = PinyinHelper.toHanyuPinyinStringArray(word);
            if(pinyin!=null){

                convert += pinyin[0].charAt(0);
            }else{
                convert +=word;

            }
        }
        String bigWord=convert.toUpperCase();
        //提取最后endString中的数字
        String regEx3 = "[0-9]";
        String endNum=matchResult(Pattern.compile(regEx3),endString);
        //定义新得字符串
        String wordString=bigWord.replace(endNum,"")+"0000"+endNum;
        System.out.println(wordString);
        return wordString;
    }



//    public static void main(String[] args)
//    {
//        String regEx1 = "[\\u4e00-\\u9fa5]";
//        String regEx2 = "[a-z||A-Z]";
//        String regEx3 = "[0-9]";
//        String str = "御2哈苏（13号）".replace("号","");
//        String chinese = matchResult(Pattern.compile(regEx1),str);
//        String chineseFirst=chineseFirst(chinese);
//        System.out.println(chineseFirst);
//        String english = matchResult(Pattern.compile(regEx2),str);
//        String number = matchResult(Pattern.compile(regEx3),str);
//        System.out.println(chinese+"\n"+english+"\n"+number);
//    }


    /**
     * 提出汉字，英文，数字
     */
    public static String matchResult(Pattern p,String str)
    {
        StringBuilder string = new StringBuilder();
        Matcher m = p.matcher(str);
        while (m.find())
            for (int i = 0; i <= m.groupCount(); i++)
            {
                string.append(m.group());
            }
        return string.toString();
    }
}
