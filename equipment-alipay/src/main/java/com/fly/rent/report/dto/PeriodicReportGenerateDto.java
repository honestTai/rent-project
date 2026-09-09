package com.fly.rent.report.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 手动补生成参数。
 */
@Data
public class PeriodicReportGenerateDto {
    private String reportType;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date targetDate;
    private String periodKey;
}
