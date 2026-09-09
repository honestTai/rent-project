package com.fly.rent.web.rent;

import com.fly.rent.web.support.AbstractWebController;
import com.fly.rent.entity.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/**
 * 租赁组件 Web 后台接口。
 * 提供退款、商家确认、发货、收货、完结、详情、风控、同步、关单等操作。
 *
 * @author HonestTat
 * @since 2026-03-12
 */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class WebRentComponentController extends AbstractWebController {

    private final WebRentComponentService rentComponentService;

    /**
     * 退款处理。
     * 根据 isAgree 决定同意或拒绝退款。
     */
    @PostMapping("/rent-component/refund")
    public Result refund(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.refund(request(body)));
    }

    /**
     * 商家确认审核。
     * 根据 isAgree 决定通过或拒绝。
     */
    @PostMapping("/rent-component/merchant-confirm")
    public Result merchantConfirm(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.merchantConfirm(request(body)));
    }

    /**
     * 身份证照片二次审核。
     * 审核通过后才推进订单到商家审核通过，审核不通过时小程序用户可重新上传。
     */
    @PostMapping("/rent-component/identity-photo-review")
    public Result identityPhotoReview(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.identityPhotoReview(request(body)));
    }

    /**
     * 发货。
     * 提交物流信息并调用支付宝发货接口。
     */
    @PostMapping("/rent-component/send")
    public Result send(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.send(request(body)));
    }

    /**
     * 确认收货。
     * 确认用户归还设备已签收。
     */
    @PostMapping("/rent-component/confirm-send")
    public Result confirmSend(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.confirmSend(request(body)));
    }

    /**
     * 完结订单。
     * 订单履约完成后调用完结接口。
     */
    @PostMapping("/rent-component/complete")
    public Result complete(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.complete(request(body)));
    }

    /**
     * 查询支付宝租赁订单详情。
     * 返回地址、价格、商品、租期账单、状态等完整信息。
     */
    @PostMapping("/rent-component/detail")
    public Result detail(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.detail(request(body)));
    }

    /**
     * 查询用户寄回记录。
     * 返回小程序用户提交的寄回快递、照片和说明，用于后台订单页核对归还资料。
     */
    @PostMapping("/rent-component/return-record")
    public Result returnRecord(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.returnRecord(request(body)));
    }

    /**
     * 查询用户风控信息。
     * 返回支付宝风控查询结果。
     */
    @PostMapping("/rent-component/risk-detail")
    public Result riskDetail(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.riskDetail(request(body)));
    }

    /**
     * 同步订单状态。
     * 从支付宝拉取最新状态并回写到本地。
     */
    @PostMapping("/rent-component/sync")
    public Result sync(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.sync(request(body)));
    }

    /**
     * 关闭订单。
     * 调用支付宝关单并将本地状态更新为 CLOSED。
     */
    @PostMapping("/rent-component/close")
    public Result close(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.close(request(body)));
    }

    /**
     * 更新订单备注。
     * 将前端输入的备注信息保存到订单中，并记录操作台账。
     */
    @PostMapping("/rent-component/remark")
    public Result updateRemark(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.updateRemark(request(body)));
    }

    /**
     * 订单列表分页。
     * 支持 orderNo、goodTitle、userTitle、tel、courno、keyword、alipayStatus、status、pickupWay、start、end、page、limit。
     */
    @PostMapping("/rent-component/page")
    public Result page(@RequestBody(required = false) Map<String, Object> body) {
        return rentComponentService.page(request(body));
    }

    /**
     * 人工补生成签约协议 PDF。
     */
    @PostMapping("/rent-component/contract/generate")
    public Result generateContractPdf(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.generateContractPdf(request(body)));
    }

    @PostMapping("/rent-component/esign-contract")
    public Result esignContract(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.esignContract(request(body)));
    }

    @PostMapping("/rent-component/esign-contract/sign")
    public Result startEsignContract(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.startEsignContract(request(body)));
    }

    @GetMapping("/rent-component/esign-contract/signed-file")
    public ResponseEntity<byte[]> esignSignedFile(@RequestParam("orderId") String orderId) {
        return rentComponentService.esignSignedFile(request(Collections.<String, Object>singletonMap("orderId", orderId)));
    }

    @PostMapping("/rent-component/installment-bills")
    public Result installmentBills(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.installmentBills(request(body)));
    }

    @PostMapping("/rent-component/withhold/sign")
    public Result withholdSign(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.withholdSign(request(body)));
    }

    /**
     * 人工回传租赁合同到支付宝。
     * 用于确认收货后人工补偿合同 file_id 回传，接口内部复用幂等状态，已成功时不会重复上传。
     */
    @PostMapping("/rent-component/contract/sync")
    public Result syncContractToAlipay(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.syncContractToAlipay(request(body)));
    }

    /**
     * 押金/预授权查询。
     * 入参 orderId 或 orderNo，返回押金相关字段。
     */
    @PostMapping("/rent-component/deposit/query")
    public Result depositQuery(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.depositQuery(request(body)));
    }

    /**
     * 扣减押金。
     * 入参 orderId、deductAmount、feeType、reasonCode、remark。
     */
    @PostMapping("/rent-component/deposit/deduct")
    public Result deductDeposit(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.deductDeposit(request(body)));
    }

    /**
     * 查询扣减记录。
     * 入参 orderId 或 orderNo，可选 page/limit。
     */
    @PostMapping("/rent-component/deposit/deduct-records")
    public Result deductRecords(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.deductRecordsPage(request(body)));
    }

    /**
     * 继续确认已创建的售后扣减单。
     * 入参 recordId。
     */
    @PostMapping("/rent-component/deposit/deduct/confirm")
    public Result confirmDeductRecord(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.confirmDeductRecord(request(body)));
    }

    /**
     * 预览单个订单的支付宝售后同步差异。
     */
    @PostMapping("/rent-component/aftersale/preview")
    public Result previewAftersales(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.previewAftersales(request(body)));
    }

    /**
     * 按本地订单分页扫描支付宝售后。
     */
    @PostMapping("/rent-component/aftersale/scan")
    public Result scanAftersales(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.scanAftersales(request(body)));
    }

    /**
     * 导入选中的支付宝售后快照到本地扣减台账。
     */
    @PostMapping("/rent-component/aftersale/import")
    public Result importAftersales(@RequestBody(required = false) Map<String, Object> body) {
        return success(rentComponentService.importAftersales(request(body)));
    }
}
