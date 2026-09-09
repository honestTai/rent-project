<template>
  <t-card class="platform-card" :bordered="false">
    <template #title>
      <div class="platform-card-title">
        <h2>{{ resolvedTitle }}</h2>
        <p v-if="resolvedDescription">{{ resolvedDescription }}</p>
      </div>
    </template>
    <div v-if="$slots.actions || $slots.filters" class="platform-toolbar">
      <div class="platform-toolbar__filters">
        <slot name="filters" />
      </div>
      <div class="platform-toolbar__actions">
        <slot name="actions" />
      </div>
    </div>
    <slot />
  </t-card>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const props = defineProps({
  title: {
    type: String,
    default: '',
  },
  description: {
    type: String,
    default: '',
  },
});

const route = useRoute();

const titleText = (value: unknown) => {
  if (!value) return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'object' && value && 'zh_CN' in value) {
    return String((value as Record<string, unknown>).zh_CN || '');
  }
  return '';
};

const descriptionMap: Record<string, string> = {
  系统总览: '汇总四套系统的运行数据、业务指标和快捷入口，便于统一观察经营状态。',
  配置中心: '维护跨系统配置项、分组、敏感值和启用状态，统一管理业务参数。',
  通知通道: '配置飞书、邮件等通知渠道，查看启用状态和系统归属。',
  任务中心: '查看自动任务执行情况、任务状态和最近触发结果。',
  系统监控: '观察服务健康、接口统计和关键运行指标，辅助定位平台异常。',
  系统日志: '集中查询系统请求、异常和审计日志，支持问题追踪。',
  角色授权: '维护角色基础信息和页面、按钮权限授权关系。',
  角色列表: '查看平台角色并维护角色基础信息、授权范围和启用状态。',
  用户角色: '查询用户账号并分配角色，控制各系统访问范围。',
  系统权限树: '查看平台页面、按钮和接口资源树，维护权限资源层级。',
  权限资源: '维护平台页面、按钮和资源树，支撑权限分配。',
  用户管理: '管理平台用户、账号状态、角色关系和系统访问权限。',
};

const resolvedTitle = computed(() => props.title || titleText(route.meta.title) || '页面');
const resolvedDescription = computed(() => props.description || descriptionMap[resolvedTitle.value] || '集中查看本页面的筛选条件、业务数据和可执行操作。');
</script>
