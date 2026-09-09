package com.fly.rent.miniapp.order.dto;

import lombok.Data;

import java.util.List;

/**
 * 小程序用户寄回订单请求。
 * 兼容旧版只传 orderId、courCode、courno、courName、returnType 的请求，同时扩展照片和文字说明。
 */
@Data
public class MiniappOrderReturnRequest {

    /** 本地订单主键，必填，用于定位 rent_order。 */
    private Integer orderId;

    /** 归还方式，如线上快递或线下归还；为空时按线上快递处理。 */
    private String returnType;

    /** 快递公司编码，对应支付宝履约 delivery_id。 */
    private String courCode;

    /** 快递单号，对应支付宝履约 waybill_id。 */
    private String courno;

    /** 快递公司名称，后台展示用。 */
    private String courName;

    /** 用户上传的寄回照片列表，元素可以是 OSS key 或完整 URL。 */
    private List<String> photoUrls;

    /** 兼容小程序可能使用 returnPhotos 字段上传照片。 */
    private List<String> returnPhotos;

    /** 用户填写的寄回详情说明，例如包装、设备状态或异常描述。 */
    private String detailRemark;

    /** 兼容小程序可能使用 detail 字段提交文字说明。 */
    private String detail;
}
