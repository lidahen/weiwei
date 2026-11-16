package com.twelvet.product.server.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.twelvet.framework.core.application.controller.TWTController;
import com.twelvet.framework.core.application.domain.JsonResult;
import com.twelvet.framework.jdbc.web.page.TableDataInfo;
import com.twelvet.product.api.domain.Feed;
import com.twelvet.product.api.domain.FeedComment;
import com.twelvet.product.server.service.IFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

@Tag(description = "动态广场", name = "FeedController")
@RestController
@RequestMapping("/feed")
@Component("productFeedController")
public class FeedController extends TWTController {

	private final IFeedService feedService;

	public FeedController(IFeedService feedService) {
		this.feedService = feedService;
	}

	@SaIgnore
	@Operation(summary = "动态流(最新/推荐)")
	@GetMapping("/stream")
	public JsonResult<List<Feed>> stream(@RequestParam(defaultValue = "latest") String type) {
		Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
		return JsonResult.success(feedService.selectStream(type, userId));
	}

	@SaIgnore
	@Operation(summary = "动态详情")
	@GetMapping("/detail/{id}")
	public JsonResult<Feed> detail(@PathVariable Long id) {
		Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
		Feed feed = feedService.selectDetail(id, userId);
		return JsonResult.success(feed);
	}

	@Operation(summary = "发布动态")
	@PostMapping("/publish")
	public JsonResult<Long> publish(@RequestBody Feed feed) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(feedService.publish(feed, userId));
	}

	@Operation(summary = "编辑动态")
	@PutMapping("/{id}")
	public JsonResult<String> update(@PathVariable Long id, @RequestBody Feed feed) {
		Long userId = StpUtil.getLoginIdAsLong();
		feed.setFeedId(id);
		feedService.update(feed, userId);
		return JsonResult.success("OK");
	}

	@Operation(summary = "删除动态")
	@DeleteMapping("/{id}")
	public JsonResult<String> delete(@PathVariable Long id) {
		Long userId = StpUtil.getLoginIdAsLong();
		feedService.delete(id, userId);
		return JsonResult.success("OK");
	}

	@Operation(summary = "点赞/取消点赞")
	@PostMapping("/like")
	public JsonResult<HashMap<String, Object>> like(@RequestParam Long feedId) {
		Long userId = StpUtil.getLoginIdAsLong();
		String result = feedService.toggleLike(feedId, userId);
		HashMap<String, Object> map = new HashMap<>();
		map.put("liked", "liked".equals(result));
		map.put("likeCount", feedService.getLikeCount(feedId));
		return JsonResult.success(map);
	}

	@Operation(summary = "发表评论/回复")
	@PostMapping("/comment")
	public JsonResult<Long> comment(@RequestBody FeedComment comment) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(feedService.comment(comment, userId));
	}

	@SaIgnore
	@Operation(summary = "评论列表")
	@GetMapping("/comments/{id}")
	public JsonResult<List<FeedComment>> comments(@PathVariable Long id) {
		return JsonResult.success(feedService.selectComments(id));
	}

	@Operation(summary = "我的动态")
	@GetMapping("/my")
	public JsonResult<TableDataInfo<Feed>> my() {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(feedService.selectMyFeeds(userId, null));
	}

	@Operation(summary = "仅自己可见")
	@GetMapping("/my/self")
	public JsonResult<TableDataInfo<Feed>> mySelf() {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(feedService.selectMyFeeds(userId, "self"));
	}

	@Operation(summary = "转发")
	@PostMapping("/forward")
	public JsonResult<Long> forward(@RequestParam Long feedId) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(feedService.forward(feedId, userId));
	}

	@Operation(summary = "举报")
	@PostMapping("/report")
	public JsonResult<String> report(@RequestParam Long feedId, @RequestParam String reasonType,
			@RequestParam(required = false) String reasonDetail) {
		Long userId = StpUtil.getLoginIdAsLong();
		return JsonResult.success(feedService.report(feedId, userId, reasonType, reasonDetail));
	}

	@SaIgnore
	@Operation(summary = "搜索动态")
	@GetMapping("/search")
	public JsonResult<List<Feed>> search(@RequestParam String keyword) {
		Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
		return JsonResult.success(feedService.search(keyword, userId));
	}

	@Operation(summary = "审核动态(管理员)")
	@SaCheckPermission("marketplace:admin:feedAudit")
	@PostMapping("/audit")
	public JsonResult<String> audit(@RequestParam Long feedId, @RequestParam String action,
			@RequestParam(required = false) String reason) {
		return JsonResult.success(feedService.audit(feedId, action, reason));
	}

	/** 运营审核列表 */
	@Operation(summary = "运营审核列表")
	@SaCheckPermission("marketplace:admin:feedAudit")
	@GetMapping("/audit/page")
	public JsonResult<TableDataInfo<Feed>> auditPage() {
		return JsonResult.success(feedService.selectPendingFeeds());
	}

	@Operation(summary = "审核通过")
	@SaCheckPermission("marketplace:admin:feedAudit")
	@PostMapping("/audit/pass")
	public JsonResult<String> auditPass(@RequestParam Long feedId) {
		return JsonResult.success(feedService.audit(feedId, "pass", null));
	}

	@Operation(summary = "审核驳回")
	@SaCheckPermission("marketplace:admin:feedAudit")
	@PostMapping("/audit/reject")
	public JsonResult<String> auditReject(@RequestParam Long feedId, @RequestParam String reason) {
		return JsonResult.success(feedService.audit(feedId, "reject", reason));
	}

	@SaIgnore
	@Operation(summary = "Ping")
	@GetMapping("/ping")
	public JsonResult<String> ping() {
		return JsonResult.success("pong from feed");
	}

}
