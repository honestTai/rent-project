package com.fly.rent.report.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 周期报表列表对象。
 */
@Data
public class PeriodicReportListVo {
    private Integer id;
    private String reportType;
    private String periodKey;
    private String reportTitle;
    private BigDecimal totalRevenue;
    private Integer salesCount;
    private BigDecimal profitAmount;
    private BigDecimal lossAmount;
    private String activeRentals;
    private String cancelledOrders;
    private String coveredProvinceCount;
    private String generateMode;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date periodStartTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date periodEndTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date generatedAt;
}
