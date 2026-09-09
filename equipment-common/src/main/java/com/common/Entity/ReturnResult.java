package com.common.Entity;

import com.common.ResultStatus;
import lombok.Data;

import static com.common.Constant.constant.SUCCESS;

/**
 * 统一接口返回类
 */
@Data
public class ReturnResult<T> {
    /**
     * 状态码
     */
    public Integer code;

    /**
     * 返回信息
     */
    public String msg;

    public ResultStatus resultStatus;

    /**
     * 返回内容
     */
    public T data;

    public ReturnResult(ResultStatus resultStatus, T data) {
        this.code = resultStatus.getCode();
        this.msg = resultStatus.getMessage();
        this.data = data;
    }

    public ReturnResult(ResultStatus resultStatus) {
        this.code = resultStatus.getCode();
        this.msg = resultStatus.getMessage();
    }

    public ReturnResult(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public ReturnResult(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    // ==================== 便捷静态工厂方法 ====================

    /**
     * 操作成功，带数据
     */
    public static <T> ReturnResult<T> success(T data) {
        return new ReturnResult<>(ResultStatus.SUCCESS, data);
    }

    /**
     * 操作成功，带消息和数据
     */
    public static <T> ReturnResult<T> success(String msg, T data) {
        return new ReturnResult<>(SUCCESS, msg, data);
    }

    /**
     * 操作成功，仅消息
     */
    public static ReturnResult<Void> success(String msg) {
        return new ReturnResult<>(SUCCESS, msg);
    }

    /**
     * 操作失败
     */
    public static <T> ReturnResult<T> failure() {
        return new ReturnResult<>(ResultStatus.INTERNAL_SERVER_ERROR, null);
    }

    /**
     * 操作失败，自定义状态
     */
    public static <T> ReturnResult<T> failure(ResultStatus resultStatus) {
        return new ReturnResult<>(resultStatus);
    }
}
