package com.fly.rent.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 周期报表实体。
 */
@Data
@TableName("periodic_report")
public class PeriodicReport {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    @TableField("report_type")
    private String reportType;
    @TableField("period_key")
    private String periodKey;
    @TableField("period_start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date periodStartTime;
    @TableField("period_end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date periodEndTime;
    @TableField("report_title")
    private String reportTitle;
    @TableField("total_revenue")
    private BigDecimal totalRevenue;
    @TableField("sales_count")
    private Integer salesCount;
    @TableField("profit_amount")
    private BigDecimal profitAmount;
    @TableField("loss_amount")
    private BigDecimal lossAmount;
    @TableField("extra_metric_1")
    private String extraMetric1;
    @TableField("extra_metric_2")
    private String extraMetric2;
    @TableField("extra_metric_3")
    private String extraMetric3;
    @TableField("generate_mode")
    private String generateMode;
    @TableField("content_json")
    private String contentJson;
    @TableField("generated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date generatedAt;
    @TableField("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
    @TableField("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
