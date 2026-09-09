export interface StarterLane {
  key: string;
  title: string;
  icon: string;
  detail: string;
  prompt: string;
}

const commonLane: StarterLane = {
  key: 'today',
  title: '今日业务体检',
  icon: 'check-circle',
  detail: '汇总今天最需要关注的订单、设备和异常信号',
  prompt: '对今天的业务做一次只读体检，列出最需要关注的问题和证据',
};

const profiles: Record<string, StarterLane[]> = {
  alipay: [
    { key: 'alipay-risk', title: '订单与风控排查', icon: 'secured', detail: '联合订单、押金、操作记录和服务日志定位问题', prompt: '排查支付宝异常订单和风控问题，按影响范围、证据和建议动作整理' },
    { key: 'alipay-docs', title: '官方规则核对', icon: 'internet', detail: '结合项目知识和支付宝官方资料核对接入规则', prompt: '结合项目知识库和支付宝官方文档，核对最近需要关注的接入规则' },
  ],
  rental: [
    { key: 'rental-device', title: '设备经营表现', icon: 'chart-bar', detail: '比较设备订单、收益、利用率和异常情况', prompt: '分析最近30天设备租赁表现，找出高收益和需要关注的设备并生成图表' },
    { key: 'rental-ops', title: '履约与维修排查', icon: 'tools', detail: '检查订单履约、库存、维修和操作日志', prompt: '检查设备租赁履约、库存和维修异常，列出证据与处理优先级' },
  ],
  secondhand: [
    { key: 'second-sync', title: '抖音同步排查', icon: 'refresh', detail: '联合消息、接口和服务日志定位同步失败', prompt: '排查抖音订单同步失败，结合消息日志、接口日志和服务日志归因' },
    { key: 'second-profit', title: '库存与利润分析', icon: 'chart-combo', detail: '识别高利润、滞销和价格异常的二手设备', prompt: '分析最近30天二手设备库存、销售额和利润，列出重点设备并生成图表' },
  ],
};

export function starterLanesForSystem(system: string): StarterLane[] {
  return [commonLane, ...(profiles[system] || profiles.alipay)];
}
