package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.trade.server.domain.SettlementReconDiff;
import com.twelvet.trade.server.mapper.SellerBalanceFlowMapper;
import com.twelvet.trade.server.mapper.SettlementOrderMapper;
import com.twelvet.trade.server.mapper.SettlementReconDiffMapper;
import com.twelvet.trade.server.service.IReconService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 每日结算对账实现
 * <p>
 * 六类差异全部走 uk_diff 幂等入队；对账结束按业务日冻结存在未闭环差异的卖家。 解冻是运营在差异处理后的独立动作，本服务不自动解冻。
 * </p>
 *
 * @author twelvet
 */
@Service
public class ReconServiceImpl implements IReconService {

	private static final Logger log = LoggerFactory.getLogger(ReconServiceImpl.class);

	private static final String TYPE_MISSING = "missing_settlement";

	private static final String TYPE_PHANTOM = "phantom_settlement";

	private static final String TYPE_AMOUNT = "amount_mismatch";

	private static final String TYPE_STATUS = "status_mismatch";

	private static final String TYPE_DUPLICATE = "duplicate_settlement";

	private static final String TYPE_LEDGER = "ledger_mismatch";

	private final SettlementOrderMapper settlementOrderMapper;

	private final SellerBalanceFlowMapper balanceFlowMapper;

	private final SettlementReconDiffMapper diffMapper;

	public ReconServiceImpl(SettlementOrderMapper settlementOrderMapper, SellerBalanceFlowMapper balanceFlowMapper,
			SettlementReconDiffMapper diffMapper) {
		this.settlementOrderMapper = settlementOrderMapper;
		this.balanceFlowMapper = balanceFlowMapper;
		this.diffMapper = diffMapper;
	}

	@Override
	public int reconcile(LocalDate bizDate) {
		int added = 0;
		for (Map<String, Object> row : settlementOrderMapper.selectMissingSettlement(bizDate)) {
			SettlementReconDiff diff = newDiff(TYPE_MISSING, "missing:" + asLong(row, "orderId"), bizDate);
			diff.setOrderId(asLong(row, "orderId"));
			diff.setSellerId(asLong(row, "sellerId"));
			diff.setTenantId(asLong(row, "tenantId"));
			diff.setChannelAmount(asDecimal(row, "channelAmount"));
			added += insert(diff);
		}
		for (Map<String, Object> row : settlementOrderMapper.selectPhantomSettlement(bizDate)) {
			SettlementReconDiff diff = newDiff(TYPE_PHANTOM, "phantom:" + asLong(row, "settlementId"), bizDate);
			diff.setSettlementId(asLong(row, "settlementId"));
			diff.setOrderId(asLong(row, "orderId"));
			diff.setSellerId(asLong(row, "sellerId"));
			diff.setTenantId(asLong(row, "tenantId"));
			diff.setPlatformAmount(asDecimal(row, "platformAmount"));
			added += insert(diff);
		}
		for (Map<String, Object> row : settlementOrderMapper.selectAmountMismatch(bizDate)) {
			SettlementReconDiff diff = newDiff(TYPE_AMOUNT, "amount:" + asLong(row, "settlementId"), bizDate);
			diff.setSettlementId(asLong(row, "settlementId"));
			diff.setOrderId(asLong(row, "orderId"));
			diff.setSellerId(asLong(row, "sellerId"));
			diff.setTenantId(asLong(row, "tenantId"));
			diff.setChannelAmount(asDecimal(row, "channelAmount"));
			diff.setPlatformAmount(asDecimal(row, "platformAmount"));
			added += insert(diff);
		}
		for (Map<String, Object> row : settlementOrderMapper.selectRefundStatusMismatch(bizDate)) {
			SettlementReconDiff diff = newDiff(TYPE_STATUS, "status:" + asLong(row, "refundId"), bizDate);
			diff.setOrderId(asLong(row, "orderId"));
			diff.setSettlementId(asLong(row, "settlementId"));
			diff.setSellerId(asLong(row, "sellerId"));
			diff.setTenantId(asLong(row, "tenantId"));
			added += insert(diff);
		}
		for (Map<String, Object> row : settlementOrderMapper.selectDuplicateSettlement(bizDate)) {
			SettlementReconDiff diff = newDiff(TYPE_DUPLICATE, "duplicate:" + asLong(row, "orderId"), bizDate);
			diff.setOrderId(asLong(row, "orderId"));
			diff.setSellerId(asLong(row, "sellerId"));
			diff.setTenantId(asLong(row, "tenantId"));
			added += insert(diff);
		}
		for (Map<String, Object> row : balanceFlowMapper.selectAccountsWithLedgerDrift()) {
			Long accountId = asLong(row, "accountId");
			SettlementReconDiff diff = newDiff(TYPE_LEDGER, "ledger:" + accountId + ":" + bizDate, bizDate);
			diff.setSellerId(asLong(row, "sellerId"));
			diff.setTenantId(asLong(row, "tenantId"));
			diff.setChannelAmount(asDecimal(row, "flowSum"));
			diff.setPlatformAmount(asDecimal(row, "balance"));
			added += insert(diff);
		}
		// 独立检查最后一条 balance_after：金额合计正确时同样可能写错，不能嵌在合计漂移循环里
		for (Map<String, Object> row : balanceFlowMapper.selectAccountsWithLastBalanceDrift()) {
			Long accountId = asLong(row, "accountId");
			SettlementReconDiff diff = newDiff(TYPE_LEDGER, "ledger:" + accountId + ":" + bizDate + ":last", bizDate);
			diff.setSellerId(asLong(row, "sellerId"));
			diff.setTenantId(asLong(row, "tenantId"));
			diff.setChannelAmount(asDecimal(row, "lastBalance"));
			diff.setPlatformAmount(asDecimal(row, "balance"));
			added += insert(diff);
		}
		int frozen = diffMapper.freezeSellersWithOpenDiffs(bizDate.toString());
		log.info("[结算] 对账完成 bizDate={} 新增差异={} 冻结卖家行数={}", bizDate, added, frozen);
		return added;
	}

	@Override
	public TableDataInfo<SettlementReconDiff> adminPage(String bizDate, String status, Long tenantId) {
		PageUtils.startPage();
		List<SettlementReconDiff> rows = diffMapper.selectPage(bizDate, status, tenantId);
		return PageUtils.getDataTable(rows);
	}

	@Override
	public void resolve(Long diffId, String note, Long operatorId, Long tenantId) {
		if (diffMapper.resolve(diffId, operatorId, note, tenantId) == 0) {
			throw new TWTException("差异已处理");
		}
		log.info("[结算] 差异处理 diffId={} operatorId={}", diffId, operatorId);
	}

	private SettlementReconDiff newDiff(String type, String bizKey, LocalDate bizDate) {
		SettlementReconDiff diff = new SettlementReconDiff();
		diff.setBizDate(bizDate);
		diff.setType(type);
		diff.setBizKey(bizKey);
		diff.setStatus("open");
		return diff;
	}

	private int insert(SettlementReconDiff diff) {
		return diffMapper.insertIgnore(diff) > 0 ? 1 : 0;
	}

	private static Long asLong(Map<String, Object> row, String key) {
		Object value = row.get(key);
		return value == null ? null : ((Number) value).longValue();
	}

	private static BigDecimal asDecimal(Map<String, Object> row, String key) {
		Object value = row.get(key);
		if (value == null) {
			return null;
		}
		return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
	}

}