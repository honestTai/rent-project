<template>
  <footer :class="`${prefix}-footer site-record-footer`">
    <div class="site-record-footer__inner">
      <nav class="site-record-footer__project-links" aria-label="项目链接">
        <a class="site-record-footer__link" :href="sourceUrl" target="_blank" rel="noopener noreferrer">
          源代码 / AGPL-3.0
        </a>
        <a
          class="site-record-footer__link"
          href="https://honestTai.github.io/rent-project/"
          target="_blank"
          rel="noopener noreferrer"
        >
          操作文档
        </a>
        <a class="site-record-footer__link" href="https://hrouter.net/" target="_blank" rel="noopener noreferrer">
          赞助 HRouter
        </a>
      </nav>
      <div v-if="footer.icpRecord || footer.policeRecord" class="site-record-footer__records">
        <a
          v-if="footer.icpRecord"
          class="site-record-footer__link"
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ footer.icpRecord }}
        </a>
        <a
          v-if="footer.policeRecord"
          class="site-record-footer__link"
          href="https://www.beian.gov.cn/portal/registerSystemInfo"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ footer.policeRecord }}
        </a>
      </div>
      <div v-if="footer.copyright" class="site-record-footer__copyright">
        {{ footer.copyright }}
      </div>
    </div>
  </footer>
</template>
<script setup lang="ts">
import { onMounted, reactive } from 'vue';

import { fetchSiteFooterConfig } from '@/api/site-footer';
import { prefix } from '@/config/global';

const sourceUrl = import.meta.env.VITE_SOURCE_URL?.trim() || 'https://github.com/honestTai/rent-project';

const footer = reactive({
  icpRecord: '',
  policeRecord: '',
  copyright: 'Copyright © 2026 HONESTTAI',
});

onMounted(async () => {
  try {
    const config = await fetchSiteFooterConfig();
    footer.icpRecord = config['site.footer.icp-record'] || footer.icpRecord;
    footer.policeRecord = config['site.footer.police-record'] || footer.policeRecord;
    footer.copyright = config['site.footer.copyright'] || footer.copyright;
  } catch {
    // 配置接口不可用时保留项目链接和默认版权，不显示备案占位内容。
  }
});
</script>
<style lang="less" scoped>
.site-record-footer {
  padding: 20px 24px;
  color: #667085;
  background: #f7f9fc;
  border-top: 1px solid #e4e7ec;
  font-size: 13px;
  line-height: 20px;
}

.site-record-footer__inner {
  display: flex;
  max-width: 1600px;
  margin: 0 auto;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}

.site-record-footer__project-links,
.site-record-footer__records {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 10px 22px;
}

.site-record-footer__link {
  color: inherit;
  text-decoration: none;
  transition: color 0.2s ease;

  &:hover {
    color: #465fff;
  }
}

.site-record-footer__copyright {
  flex: 0 0 auto;
  text-align: right;
}

@media (width <= 768px) {
  .site-record-footer {
    padding: 16px;
  }

  .site-record-footer__inner {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .site-record-footer__copyright {
    text-align: left;
  }
}
</style>
