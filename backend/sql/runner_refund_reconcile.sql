-- ============================================================
-- 退款对账定时任务（2026-11-03 批次2）
-- cancelled 但 pay_status='paid' 的滞留任务兜底重试退款（退款失败自动补偿）
-- 可重复执行（NOT EXISTS 幂等）；status='1' 暂停，需要时后台启用
-- ============================================================

INSERT INTO sys_job
    (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent,
     status, create_by, create_time, remark)
SELECT '互助-退款对账补偿', 'DEFAULT', 'runnerTask.reconcileStalePaidRefunds', '0 0/30 * * * ?', 3, 1,
       '1', 'admin', NOW(),
       '每30分钟扫cancelled且pay_status=paid的滞留任务，兜底重试退款(幂等: refund非paid拒绝+仅paid可翻)'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_job j WHERE j.invoke_target = 'runnerTask.reconcileStalePaidRefunds');

-- 同步修正既有超时扫描任务的备注（其退款语义已于批次1落地，旧备注"仅取消并落日志"已过时）
UPDATE sys_job SET remark = '每5分钟扫超时未接单任务，自动取消并原路退款(失败由退款对账任务兜底)'
WHERE invoke_target = 'runnerTask.scanTimeoutPending'
  AND remark LIKE '%触发退款%';
