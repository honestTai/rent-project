<template>
  <div class="project-overview">
    <section class="overview-hero">
      <span class="overview-eyebrow">RENT PROJECT</span>
      <h1>租赁业务功能导览</h1>
      <p>通过中台管理配置与权限，在租赁后台维护商品和订单，并为自己的支付宝小程序接入业务接口。</p>
      <a href="https://honestTai.github.io/rent-project/" target="_blank" rel="noopener noreferrer">查看操作文档 →</a>
    </section>

    <section class="module-grid" aria-label="项目模块">
      <article v-for="module in modules" :key="module.name" class="module-card">
        <span class="module-category">{{ module.category }}</span>
        <h2>{{ module.name }}</h2>
        <p>{{ module.description }}</p>
        <ul>
          <li v-for="capability in module.capabilities" :key="capability">{{ capability }}</li>
        </ul>
      </article>
    </section>

    <section class="setup-note">
      <h2>接入自己的业务</h2>
      <p>在中台配置自己的支付宝应用、回调域名、存储服务、租赁协议主体与退货联系方式。完成所需服务开通和联调后，再处理真实交易。</p>
      <p>本项目包含小程序业务接口；独立小程序客户端需要自行开发并配置接入。</p>
    </section>
  </div>
</template>

<script setup lang="ts">
const modules = [
  {
    category: '平台管理',
    name: '中台',
    description: '集中管理平台账号、角色权限和各项业务配置。',
    capabilities: ['用户与角色', '菜单及接口权限', '配置中心与通知通道', '任务配置与系统日志'],
  },
  {
    category: '日常运营',
    name: '租赁后台',
    description: '维护租赁商品，处理订单履约与售后，查看经营数据。',
    capabilities: ['商品与规格', '订单与履约', '售后与退款', '经营分析与报表'],
  },
  {
    category: '应用接入',
    name: '小程序接口',
    description: '为支付宝小程序提供商品、用户和租赁交易所需的服务端能力。',
    capabilities: ['用户登录与认证', '商品查询与交易预检', '建单签约与订单查询', '支付与订单状态回调'],
  },
];
</script>

<style scoped lang="less">
.project-overview {
  min-height: 100%;
  padding: 24px;
  color: #182033;
  background: #f7f8fb;
}

.overview-hero {
  padding: 36px 40px;
  color: #fff;
  background: linear-gradient(135deg, #203fc2, #3157f5);
  border-radius: 20px;
}

.overview-eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.overview-hero h1 {
  margin: 12px 0;
  font-size: clamp(26px, 4vw, 36px);
}

.overview-hero p {
  max-width: 760px;
  margin: 0 0 20px;
  line-height: 1.8;
}

.overview-hero a {
  color: #fff;
  text-underline-offset: 4px;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin-top: 22px;
}

.module-card {
  padding: 28px;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 16px;
}

.module-category {
  color: #3157f5;
  font-size: 13px;
}

.module-card h2,
.setup-note h2 {
  margin: 10px 0 14px;
  font-size: 22px;
}

.module-card p,
.setup-note p {
  color: #5f6b83;
  line-height: 1.8;
}

.module-card ul {
  padding-left: 20px;
  line-height: 2;
}

.setup-note {
  margin-top: 22px;
  padding: 24px 28px;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 16px;
}

.setup-note p:last-child {
  margin-bottom: 0;
}

@media (width <= 900px) {
  .module-grid {
    grid-template-columns: 1fr;
  }
}

@media (width <= 600px) {
  .project-overview {
    padding: 14px;
  }

  .overview-hero,
  .module-card,
  .setup-note {
    padding: 24px;
  }
}
</style>
