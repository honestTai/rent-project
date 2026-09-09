package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 支付宝商品同步日志。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("alipay_goods_sync_log")
public class AlipayGoodsSyncLog extends Model<AlipayGoodsSyncLog> {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 本地商品 ID，关联 goods.goods_id。 */
    private Integer goodId;

    /** 单次同步任务 ID，用于前端或日志排查定位。 */
    private String taskId;

    /** 触发来源：manual 表示手动同步，auto 表示本地保存后自动提交。 */
    private String triggerType;

    /** 同步状态：QUEUED/RUNNING/SUCCESS/FAILED/SKIPPED。 */
    private String syncStatus;

    /** 实际同步模式：create/modify/direct_modify 等。 */
    private String syncMode;

    /** 支付宝商品 ID。 */
    private String alipayItemId;

    /** 同步日志摘要或失败原因。 */
    private String message;

    /** 同步结果详情 JSON。 */
    private String detailJson;

    /** 同步开始时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startedAt;

    /** 同步结束时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date finishedAt;

    /** 同步耗时，单位毫秒。 */
    private Long durationMs;

    /** 创建时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    /** 更新时间。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}
