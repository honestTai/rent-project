package com.common.Entity;

import com.common.ResultStatus;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;

/**
 * 与 {@link ReturnResult} 同形（code、msg、data），其中 {@code data} 为已序列化好的 JSON 片段（通常为数组），
 * 通过 {@link JsonRawValue} 写入响应体，避免先反序列化为 Java 对象再序列化一次。
 */
@Data
public class ReturnResultJsonRaw {

    private Integer code;

    private String msg;

    @JsonRawValue
    private String data;

    public static ReturnResultJsonRaw ofJsonArray(String jsonArray) {
        ReturnResultJsonRaw r = new ReturnResultJsonRaw();
        r.setCode(ResultStatus.SUCCESS.getCode());
        r.setMsg(ResultStatus.SUCCESS.getMessage());
        r.setData(jsonArray);
        return r;
    }
}
