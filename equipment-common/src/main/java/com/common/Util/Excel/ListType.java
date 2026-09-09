package com.common.Util.Excel;

import java.util.ArrayList;
import java.util.List;

public class ListType {
    /**
     * 获取对象值（导入）
     */
    public static Object getValue(String val) {
        List<String> list = new ArrayList<>();
        if(!StringUtils.isBlank(val)) {
            for (String s : val.split(",")) {
                list.add(s);
            }
        }
        return list;
    }

    /**
     * 设置对象值（导出）
     */
    public static String setValue(Object val) {
        if (val != null){
            List<String> list = (List<String>)val;
            StringBuffer sb = null;
            for (String item: list){
                if(StringUtils.isBlank(item)){
                    continue;
                }
                if(sb == null){
                    sb = new StringBuffer(item);
                }else{
                    sb.append(",").append(item);
                }
            }

            if(sb!=null) {
                return sb.toString().replace("[]", "");
            }
        }
        return "";
    }
}
