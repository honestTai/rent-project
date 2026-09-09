package com.fly.rent.web.goods;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.entity.CatalogAdminOperation;
import com.fly.rent.mapper.CatalogAdminOperationMapper;
import com.fly.rent.common.support.RentApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;

/** 目录操作审计与高风险操作幂等凭证。 */
@Service
@RequiredArgsConstructor
public class CatalogOperationAuditService {

    private final CatalogAdminOperationMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CatalogAdminOperation begin(String key, String action, String resourceCode,
                                       String operatorName, String requestSummary) {
        if (!StringUtils.hasText(key)) return null;
        CatalogAdminOperation existing = find(key.trim(), action);
        if (existing != null) return requireCompleted(existing, resourceCode, operatorName, requestSummary);
        CatalogAdminOperation operation = new CatalogAdminOperation();
        operation.setIdempotencyKey(key.trim());
        operation.setAction(action);
        operation.setResourceCode(resourceCode);
        operation.setOperatorName(operatorName);
        operation.setRequestSummary(requestSummary);
        operation.setSuccess(0);
        Date now = new Date();
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        try {
            mapper.insert(operation);
            return operation;
        } catch (DuplicateKeyException ex) {
            return requireCompleted(find(key.trim(), action), resourceCode, operatorName, requestSummary);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(CatalogAdminOperation operation, String resultSummary) {
        if (operation == null) return;
        operation.setSuccess(1);
        operation.setResultSummary(resultSummary);
        operation.setFailReason(null);
        operation.setUpdatedAt(new Date());
        mapper.updateById(operation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(CatalogAdminOperation operation, String failReason) {
        if (operation == null) return;
        operation.setSuccess(0);
        operation.setFailReason(StringUtils.hasText(failReason) ? failReason : "catalog operation failed");
        operation.setUpdatedAt(new Date());
        mapper.updateById(operation);
    }

    @Transactional
    public void record(String action, String resourceCode, String operatorName,
                       String requestSummary, String resultSummary) {
        CatalogAdminOperation operation = new CatalogAdminOperation();
        operation.setAction(action);
        operation.setResourceCode(resourceCode);
        operation.setOperatorName(operatorName);
        operation.setRequestSummary(requestSummary);
        operation.setResultSummary(resultSummary);
        operation.setSuccess(1);
        Date now = new Date();
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        mapper.insert(operation);
    }

    private CatalogAdminOperation find(String key, String action) {
        return mapper.selectOne(new QueryWrapper<CatalogAdminOperation>()
                .eq("idempotency_key", key).eq("action", action).last("LIMIT 1"));
    }

    private CatalogAdminOperation requireCompleted(CatalogAdminOperation operation, String resourceCode,
                                                    String operatorName, String requestSummary) {
        if (operation != null && (!safeEquals(operation.getResourceCode(), resourceCode)
                || !safeEquals(operation.getOperatorName(), operatorName)
                || !safeEquals(operation.getRequestSummary(), requestSummary))) {
            throw new RentApiException(4004, "Idempotency-Key 已用于不同的目录请求");
        }
        if (operation != null && StringUtils.hasText(operation.getFailReason())) {
            throw new RentApiException(4004, "该幂等请求上次执行失败，请使用新的 Idempotency-Key");
        }
        if (operation == null || !Integer.valueOf(1).equals(operation.getSuccess())) {
            throw new RentApiException(4004, "相同 Idempotency-Key 的请求正在处理");
        }
        return operation;
    }

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
