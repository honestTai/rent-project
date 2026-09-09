<template>
  <section class="no-permission-page tail-dashboard-page">
    <section class="dashboard-header">
      <div class="dashboard-header__copy">
        <div class="dashboard-kicker">Permission</div>
        <h1>暂无权限</h1>
        <p>当前账号还没有HONESTTAI 租赁管理页面权限，请联系中台管理员分配后再访问。</p>
      </div>
    </section>

    <section class="no-permission-card ant-pro-content-card">
      <t-result theme="warning" title="暂无HONESTTAI 租赁管理权限" description="请联系中台管理员分配 alipay 系统页面权限。" />
    </section>
  </section>
</template>

<style scoped>
.no-permission-page {
  min-height: calc(100vh - 88px);
}

.no-permission-card {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fff;
}
</style>
