package com.twelvet.system.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.framework.security.domain.LoginUser;
import com.twelvet.framework.security.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * @author twelvet
 * @Description: 在线令牌管理
 */
@Tag(description = "TokenController", name = "令牌管理")
@RestController
@RequestMapping("/system/token")
public class TokenController extends TWTController {

	private static final Logger log = LoggerFactory.getLogger(TokenController.class);

	/**
	 * 分页查询在线令牌
	 * @param pageNum 页码
	 * @param pageSize 每页条数
	 * @param username 用户名（可选过滤）
	 * @return JsonResult<TableDataInfo>
	 */
	@Operation(summary = "分页查询在线令牌")
	@SaCheckPermission("system:token:list")
	@GetMapping("/pageQuery")
	public JsonResult<TableDataInfo<Map<String, Object>>> pageQuery(@RequestParam(defaultValue = "1") int pageNum,
			@RequestParam(defaultValue = "10") int pageSize, @RequestParam(required = false) String username) {

		// 查询所有令牌（某些Redis实现返回的是完整键名而非令牌值，需提取令牌值）
		String tokenKeyPrefix = StpUtil.getTokenName() + ":" + "login" + ":" + "token" + ":";
		List<String> tokenKeys = StpUtil.searchTokenValue("", 0, -1, true);

		List<Map<String, Object>> list = new ArrayList<>();
		for (String tokenKey : tokenKeys) {
			try {
				// 从完整 Redis 键名中提取令牌值
				String tokenValue = tokenKey;
				if (tokenValue.startsWith(tokenKeyPrefix)) {
					tokenValue = tokenValue.substring(tokenKeyPrefix.length());
				}

				SaSession session = StpUtil.getTokenSessionByToken(tokenValue);
				LoginUser loginUser = (LoginUser) session.get(SecurityUtils.LOGIN_USER_KEY);
				if (loginUser == null) {
					continue;
				}
				// 按用户名过滤
				if (username != null && !username.isEmpty() && !loginUser.getUsername().contains(username)) {
					continue;
				}

				Map<String, Object> item = new LinkedHashMap<>();
				item.put("username", loginUser.getUsername());
				item.put("clientId", String.valueOf(StpUtil.getLoginIdByToken(tokenValue)));
				item.put("accessToken", tokenValue);
				// 会话创建时间 ≈ 令牌签发时间
				long createTime = session.getCreateTime();
				item.put("issuedAt", new Date(createTime));
				// 计算过期时间
				long timeout = session.timeout();
				if (timeout > 0) {
					item.put("expiresAt", new Date(createTime + timeout * 1000));
				}
				else {
					item.put("expiresAt", null);
				}
				list.add(item);
			}
			catch (NotLoginException e) {
				// 令牌已过期或被踢下线，跳过（不中断整个请求）
				log.debug("跳过已失效的令牌: {}", tokenKey);
			}
		}

		// 按签发时间倒序排列
		list.sort((a, b) -> ((Date) b.get("issuedAt")).compareTo((Date) a.get("issuedAt")));

		// 手动分页
		int total = list.size();
		int fromIndex = Math.min((pageNum - 1) * pageSize, total);
		int toIndex = Math.min(fromIndex + pageSize, total);
		List<Map<String, Object>> pageList = fromIndex >= total ? Collections.emptyList()
				: list.subList(fromIndex, toIndex);

		return JsonResult.success(TableDataInfo.page(pageList, total));
	}

	/**
	 * 强退用户
	 * @param tokenValue 令牌值
	 * @return 操作结果
	 */
	@Operation(summary = "强退用户")
	@SaCheckPermission("system:token:remove")
	@DeleteMapping("/{tokenValue}")
	public JsonResult<Void> remove(@PathVariable String tokenValue) {
		StpUtil.kickoutByTokenValue(tokenValue);
		return JsonResult.success();
	}

}
