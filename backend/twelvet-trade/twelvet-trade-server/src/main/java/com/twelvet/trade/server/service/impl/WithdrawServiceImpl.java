package com.twelvet.trade.server.service.impl;

import com.twelvet.framework.core.exception.TWTException;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.jdbc.web.utils.PageUtils;
import com.twelvet.framework.utils.IdUtils;
import com.twelvet.trade.server.constant.BalanceFlowType;
import com.twelvet.trade.server.constant.WithdrawStatus;
import com.twelvet.trade.server.domain.SellerAccount;
import com.twelvet.trade.server.domain.SellerBalanceFlow;
import com.twelvet.trade.server.domain.TradeWithdraw;
import com.twelvet.trade.server.mapper.TradeWithdrawMapper;
import com.twelvet.trade.server.service.ISellerAccountService;
import com.twelvet.trade.server.service.IWithdrawService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 卖家提现实现
 * <p>
 * 申请即扣余额（withdraw_out），驳回用 adjust 退回，打款不再动余额。所有状态推进走带守卫的 UPDATE 并检查行数。
 * </p>
 *
 * @author twelvet
 */
@Service
public class WithdrawServiceImpl implements IWithdrawService {

	private static final Logger log = LoggerFactory.getLogger(WithdrawServiceImpl.class);

	private final ISellerAccountService accountService;

	private final TradeWithdrawMapper withdrawMapper;

	public WithdrawServiceImpl(ISellerAccountService accountService, TradeWithdrawMapper withdrawMapper) {
		this.accountService = accountService;
		this.withdrawMapper = withdrawMapper;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BigDecimal apply(Long sellerId, Long tenantId, BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new TWTException("提现金额必须大于 0");
		}
		if (amount.scale() > 2) {
			throw new TWTException("提现金额最多保留两位小数");
		}
		SellerAccount account = accountService.getOrCreate(sellerId, tenantId);
		if (!"verified".equals(account.getKycStatus())) {
			throw new TWTException("请先完成实名认证");
		}
		if (account.getFrozen() != null && account.getFrozen() == 1) {
			throw new TWTException("账户已冻结（对账差异或负余额），请联系平台处理");
		}
		if (account.getBalance().compareTo(amount) < 0) {
			throw new TWTException("可提现余额不足");
		}
		TradeWithdraw withdraw = new TradeWithdraw();
		withdraw.setWithdrawNo("W" + IdUtils.fastSimpleUUID());
		withdraw.setAccountId(account.getAccountId());
		withdraw.setSellerId(sellerId);
		withdraw.setTenantId(tenantId);
		withdraw.setAmount(amount);
		withdraw.setStatus(WithdrawStatus.PENDING);
		withdrawMapper.insert(withdraw);
		SellerBalanceFlow flow = new SellerBalanceFlow();
		flow.setSellerId(sellerId);
		flow.setTenantId(tenantId);
		flow.setWithdrawId(withdraw.getWithdrawId());
		flow.setType(BalanceFlowType.WITHDRAW_OUT);
		flow.setAmount(amount.negate());
		flow.setBizKey("withdraw:" + withdraw.getWithdrawId());
		if (!accountService.applyBalance(flow)) {
			// 幂等键已消费（同一提现单已扣过）：回滚提现单，不二次扣减
			throw new TWTException("提现单已处理");
		}
		log.info("[结算] 提现申请 sellerId={} withdrawId={} amount={}", sellerId, withdraw.getWithdrawId(), amount);
		return flow.getBalanceAfter();
	}

	@Override
	public TableDataInfo<TradeWithdraw> myPage(Long sellerId, Long tenantId) {
		PageUtils.startPage();
		List<TradeWithdraw> rows = withdrawMapper.selectBySeller(sellerId, tenantId);
		return PageUtils.getDataTable(rows);
	}

	@Override
	public TableDataInfo<TradeWithdraw> adminPage(String status, Long tenantId) {
		PageUtils.startPage();
		List<TradeWithdraw> rows = withdrawMapper.selectAdminPage(status, tenantId);
		return PageUtils.getDataTable(rows);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void review(Long withdrawId, String action, String reason, Long operatorId, Long tenantId) {
		if (!"approve".equals(action) && !"reject".equals(action)) {
			throw new TWTException("审核动作只能是 approve 或 reject");
		}
		boolean reject = "reject".equals(action);
		// 只有驳回需要原单的卖家/金额做退回；通过直接走状态守卫（0 行 = 不存在或已处理）
		TradeWithdraw withdraw = reject ? withdrawMapper.selectById(withdrawId, tenantId) : null;
		if (reject && withdraw == null) {
			throw new TWTException("提现单不存在");
		}
		int rows = withdrawMapper.review(withdrawId, WithdrawStatus.PENDING,
				reject ? WithdrawStatus.REJECTED : WithdrawStatus.APPROVED, operatorId, reject ? reason : null,
				tenantId);
		if (rows == 0) {
			throw new TWTException("提现状态已变更，请刷新后重试");
		}
		if (reject) {
			SellerBalanceFlow flow = new SellerBalanceFlow();
			flow.setSellerId(withdraw.getSellerId());
			flow.setTenantId(withdraw.getTenantId());
			flow.setWithdrawId(withdrawId);
			flow.setType(BalanceFlowType.ADJUST);
			flow.setAmount(withdraw.getAmount());
			flow.setBizKey("withdraw_reject:" + withdrawId);
			if (!accountService.applyBalance(flow)) {
				// 幂等键已消费：退款已执行过，状态回滚重试也不会重复加钱
				throw new TWTException("提现驳回退款已处理");
			}
		}
		log.info("[结算] 提现审核 withdrawId={} action={} operatorId={}", withdrawId, action, operatorId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void markPaid(Long withdrawId, String voucherUrl, Long operatorId, Long tenantId) {
		if (voucherUrl == null || voucherUrl.isBlank()) {
			throw new TWTException("请上传打款凭证");
		}
		int rows = withdrawMapper.markPaid(withdrawId, voucherUrl.trim(), operatorId, tenantId);
		if (rows == 0) {
			throw new TWTException("提现状态已变更或未审核通过");
		}
		log.info("[结算] 提现打款完成 withdrawId={} operatorId={}", withdrawId, operatorId);
	}

}