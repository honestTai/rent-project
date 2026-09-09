-- 支付宝租赁合同回传补偿定时任务。
-- 只补齐中台任务配置，不改业务数据。新任务默认停用，已有任务开关保持原值。
-- 完成合同回传联调后由中台按需启用；生产执行前请确认唯一键或按业务键先查重。
INSERT INTO platform_schedule_task
    (system_code, task_code, task_name, cron_expr, execute_type, execute_target, enabled, remark, created_at, updated_at)
SELECT
    'alipay',
    'alipay.contract.sync',
    '支付宝租赁合同回传补偿',
    '0 0 0 * * ?',
    'LOCAL',
    'ScheduledTasks.syncReceivedOrderContracts',
    0,
    '默认停用；完成合同回传联调后由中台启用，每晚0点补偿合同回传',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM platform_schedule_task
    WHERE system_code = 'alipay'
      AND task_code = 'alipay.contract.sync'
);

UPDATE platform_schedule_task
SET task_name = '支付宝租赁合同回传补偿',
    cron_expr = '0 0 0 * * ?',
    execute_type = 'LOCAL',
    execute_target = 'ScheduledTasks.syncReceivedOrderContracts',
    remark = '完成合同回传联调后由中台管理开关，每晚0点补偿合同回传',
    updated_at = NOW()
WHERE system_code = 'alipay'
  AND task_code = 'alipay.contract.sync';
