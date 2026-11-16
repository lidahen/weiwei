package com.twelvet.runner.server.listener;

import com.twelvet.framework.core.tenant.TenantContext;
import com.twelvet.im.server.service.IImService;
import com.twelvet.runner.api.domain.RunnerTask;
import com.twelvet.runner.api.domain.RunnerTaskLog;
import com.twelvet.runner.server.domain.RunnerNotifyLog;
import com.twelvet.runner.server.event.RunnerTaskNotifyEvent;
import com.twelvet.runner.server.mapper.RunnerNotifyLogMapper;
import com.twelvet.runner.server.mapper.RunnerTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 互助状态流转 IM 通知监听器
 * <p>
 * 设计：docs/跑腿消息通知设计.md
 * <ul>
 * <li>@Async 异步执行，失败只记日志 + 落 notify_log，绝不影响主流程事务</li>
 * <li>@Async 线程取不到 TenantContext，事件携带 tenantId（Quartz 场景用任务表 tenant_id 兜底）</li>
 * <li>发送通道复用 IM：IImService.sendMessage(operator, receiver, null, "system", content)</li>
 * </ul>
 *
 * @author twelvet
 */
@Component
public class RunnerTaskNotifyListener {

	private static final Logger log = LoggerFactory.getLogger(RunnerTaskNotifyListener.class);

	private static final String CHANNEL_IM = "im";

	private final RunnerTaskMapper taskMapper;

	private final RunnerNotifyLogMapper notifyLogMapper;

	private final IImService imService;

	public RunnerTaskNotifyListener(RunnerTaskMapper taskMapper, RunnerNotifyLogMapper notifyLogMapper,
			IImService imService) {
		this.taskMapper = taskMapper;
		this.notifyLogMapper = notifyLogMapper;
		this.imService = imService;
	}

	@Async
	@EventListener
	public void onTaskNotify(RunnerTaskNotifyEvent event) {
		Long tid = null;
		try {
			RunnerTask task = taskMapper.selectById(event.getTaskId(), event.getTenantId());
			if (task == null) {
				return;
			}
			// 租户上下文兜底（Quartz 线程无 ThreadLocal 时用任务表归属）
			tid = event.getTenantId() != null ? event.getTenantId() : task.getTenantId();
			TenantContext.set(tid);
			try {
				ReceiverMsg target = resolveReceiverAndContent(event, task);
				if (target == null) {
					return;
				}
				String error = null;
				try {
					imService.sendMessage(event.getOperatorId(), target.receiverId, null, "system", target.content);
				}
				catch (Exception e) {
					error = e.getMessage();
					log.warn("互助通知 IM 发送失败 taskId={}, receiver={}: {}", event.getTaskId(), target.receiverId, error);
				}
				saveLog(tid, event, target, error);
			}
			finally {
				TenantContext.clear();
				tid = null;
			}
		}
		catch (Exception e) {
			log.warn("互助通知处理异常 taskId={}: {}", event.getTaskId(), e.getMessage());
		}
		finally {
			if (tid != null) {
				TenantContext.clear();
			}
		}
	}

	private void saveLog(Long tid, RunnerTaskNotifyEvent event, ReceiverMsg target, String error) {
		try {
			RunnerNotifyLog nl = new RunnerNotifyLog();
			nl.setTaskId(event.getTaskId());
			nl.setAction(event.getAction());
			nl.setSenderId(event.getOperatorId());
			nl.setReceiverId(target.receiverId);
			nl.setContent(target.content);
			nl.setChannel(CHANNEL_IM);
			nl.setStatus(error == null ? RunnerNotifyLog.STATUS_SENT : RunnerNotifyLog.STATUS_FAILED);
			nl.setErrorMsg(error);
			notifyLogMapper.insert(nl, tid);
		}
		catch (Exception e) {
			log.warn("互助通知日志落库失败 taskId={}: {}", event.getTaskId(), e.getMessage());
		}
	}

	/**
	 * 定位接收人与文案；返回 null 表示无需通知（对方为空/给自己发）
	 */
	private ReceiverMsg resolveReceiverAndContent(RunnerTaskNotifyEvent event, RunnerTask task) {
		String title = task.getTitle();
		String action = event.getAction();
		Long receiver;
		String content;
		switch (action) {
			case RunnerTaskLog.ACTION_ASSIGN:
				receiver = task.getPublisherId();
				content = "【互助】您的任务「" + title + "」已被接单，接单人正在准备服务";
				break;
			case RunnerTaskLog.ACTION_START:
				receiver = task.getPublisherId();
				content = "【互助】任务「" + title + "」接单人已开始服务";
				break;
			case RunnerTaskLog.ACTION_FINISH:
				receiver = task.getPublisherId();
				content = "【互助】任务「" + title + "」接单人已提交完成，请确认验收";
				break;
			case RunnerTaskLog.ACTION_REJECT:
				receiver = task.getPublisherId();
				content = "【互助】任务「" + title + "」接单人拒单，任务已重新放回互助大厅";
				break;
			case RunnerTaskLog.ACTION_CONFIRM:
				receiver = task.getAssigneeId();
				content = "【互助】任务「" + title + "」已完成确认，感谢您的互助服务";
				break;
			case RunnerTaskLog.ACTION_REVIEW:
				receiver = task.getAssigneeId();
				content = "【互助】任务「" + title + "」获得 " + (event.getRemark() == null ? "" : event.getRemark())
						+ " 好评，信用分已更新";
				break;
			case RunnerTaskLog.ACTION_REFUND:
				receiver = task.getAssigneeId();
				content = "【互助】任务「" + title + "」下单人申请退款，客服将介入处理";
				break;
			case RunnerTaskLog.ACTION_CANCEL:
				// 申请退款走 doCancel（status=refund），按退款语义通知接单人
				if (RunnerTask.STATUS_REFUND.equals(task.getStatus())) {
					receiver = task.getAssigneeId();
					content = "【互助】任务「" + title + "」下单人申请退款，客服将介入处理";
				}
				else {
					// 取消：通知对方（发布者取消→通知接单人；反之通知发布者）
					receiver = event.getOperatorId().equals(task.getPublisherId()) ? task.getAssigneeId()
							: task.getPublisherId();
					content = "【互助】任务「" + title + "」已被取消：" + (event.getRemark() == null ? "" : event.getRemark());
				}
				break;
			default:
				return null;
		}
		if (receiver == null || receiver.equals(event.getOperatorId())) {
			return null;
		}
		return new ReceiverMsg(receiver, content);
	}

	private static class ReceiverMsg {

		final Long receiverId;

		final String content;

		ReceiverMsg(Long receiverId, String content) {
			this.receiverId = receiverId;
			this.content = content;
		}

	}

}
