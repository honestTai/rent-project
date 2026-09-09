<template>
  <section class="agent-page">
    <header class="agent-page__header">
      <div>
        <span>支付宝租赁 · AI OPERATIONS</span>
        <h1>支付宝业务智能工作台</h1>
        <p>联合订单、押金、风控、服务日志与官方规则，完成只读排查。</p>
      </div>
      <strong><i></i>只读安全模式</strong>
    </header>
    <AgentChatPanel
      mode="page"
      system="alipay"
      title="支付宝后台解决助手"
      subtitle="支付宝租赁"
      primary-tag="支付宝优先"
      placeholder="输入消息，按 Enter 发送..."
      input-tip="Enter 发送，Shift + Enter 换行"
      empty-text="可以直接输入订单号、日志关键词、时间范围或经营分析需求。"
      :api="agentApi"
      :chart-renderer="AnalyticsChart"
    />
  </section>
</template>

<script setup lang="ts">
import AgentChatPanel from '@shared/components/AgentChatPanel.vue';

import { rentApi, type AnyRecord } from '@/api/rent';
import AnalyticsChart from '@/pages/alipay/components/AnalyticsChart.vue';

const agentApi = {
  sendMessage: (data: AnyRecord) => rentApi.sendAgentMessage(data),
  streamMessage: rentApi.streamAgentMessage,
  cancelRun: rentApi.cancelAgentMessage,
  listConversations: (system: string) => rentApi.listAgentConversations(system),
  getConversation: (id: string, system: string) => rentApi.getAgentConversation(id, system),
  deleteConversation: (id: string, system: string) => rentApi.deleteAgentConversation(id, system),
};
</script>

<style lang="less" scoped>
.agent-page {
  display: grid;
  height: calc(100vh - 88px);
  min-height: 0;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
  padding: 20px 24px 24px;
  background: #f5f7fb;
}

.agent-page__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  padding: 0 2px 15px;
}

.agent-page__header div { display: grid; gap: 3px; }
.agent-page__header span { color: #3975b9; font-size: 10px; font-weight: 700; letter-spacing: .08em; }
.agent-page__header h1 { margin: 0; color: #17243a; font-size: 23px; font-weight: 700; line-height: 31px; }
.agent-page__header p { margin: 0; color: #78869a; font-size: 12px; }
.agent-page__header > strong { display: inline-flex; align-items: center; gap: 7px; padding: 6px 10px; border: 1px solid #d9eadf; border-radius: 999px; background: #f2fbf5; color: #15803d; font-size: 11px; }
.agent-page__header > strong i { width: 7px; height: 7px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 0 4px rgb(34 197 94 / 12%); }
@media (max-width: 760px) {
  .agent-page { padding: 14px; }
  .agent-page__header { align-items: flex-start; }
  .agent-page__header p, .agent-page__header > strong { display: none; }
}
</style>
