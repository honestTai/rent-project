<template>
  <section class="agent-sources" aria-label="联网来源">
    <header>
      <div class="agent-sources__eyebrow">
        <t-icon name="internet" />
        <span>联网来源</span>
      </div>
      <strong>{{ visibleSources.length }} 条可引用结果</strong>
    </header>

    <div class="agent-sources__grid">
      <a
        v-for="source in visibleSources"
        :key="source.url"
        :href="source.url"
        target="_blank"
        rel="noopener noreferrer"
        class="agent-source-card"
      >
        <div class="agent-source-card__meta">
          <span>{{ source.source || sourceHost(source.url) }}</span>
          <i>#{{ source.rank }}</i>
        </div>
        <strong>{{ source.title }}</strong>
        <p v-if="source.snippet">{{ source.snippet }}</p>
        <div class="agent-source-card__open">
          <span>查看原文</span>
          <t-icon name="jump" />
        </div>
      </a>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import type { AgentSource } from '../../agent/types';

const props = defineProps<{ sources: AgentSource[] }>();

const visibleSources = computed(() => (props.sources || [])
  .filter((source) => source.url?.startsWith('https://'))
  .slice(0, 5));

function sourceHost(url: string) {
  try {
    return new URL(url).hostname;
  } catch {
    return '公开网页';
  }
}
</script>

<style scoped>
.agent-sources {
  display: grid;
  gap: 10px;
  margin-top: 13px;
  padding: 12px;
  border: 1px solid #dce7f4;
  border-radius: 12px;
  background: linear-gradient(145deg, #f8fbff 0%, #fff 72%);
  box-shadow: 0 8px 24px rgb(31 72 119 / 6%);
}

.agent-sources header,
.agent-sources__eyebrow,
.agent-source-card__meta,
.agent-source-card__open {
  display: flex;
  align-items: center;
}

.agent-sources header {
  justify-content: space-between;
  gap: 12px;
}

.agent-sources__eyebrow {
  gap: 6px;
  color: #2467a8;
  font-size: 11px;
  font-weight: 750;
  letter-spacing: .04em;
}

.agent-sources header > strong {
  color: #8090a6;
  font-size: 10px;
  font-weight: 600;
}

.agent-sources__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.agent-source-card {
  display: grid;
  min-width: 0;
  gap: 6px;
  padding: 11px 12px;
  border: 1px solid #e5edf6;
  border-radius: 10px;
  background: rgb(255 255 255 / 92%);
  color: inherit;
  text-decoration: none;
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
}

.agent-source-card:hover {
  border-color: #8bb9ea;
  box-shadow: 0 9px 20px rgb(31 87 145 / 10%);
  transform: translateY(-1px);
}

.agent-source-card__meta {
  justify-content: space-between;
  gap: 8px;
  color: #6f8299;
  font-size: 10px;
}

.agent-source-card__meta span,
.agent-source-card > strong,
.agent-source-card p {
  overflow: hidden;
}

.agent-source-card__meta span {
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-source-card__meta i {
  color: #4e82bc;
  font-style: normal;
}

.agent-source-card > strong {
  color: #25364d;
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-source-card p {
  display: -webkit-box;
  margin: 0;
  color: #718198;
  font-size: 11px;
  line-height: 17px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.agent-source-card__open {
  justify-content: flex-end;
  gap: 4px;
  color: #2f71b7;
  font-size: 10px;
  font-weight: 650;
}

@media (max-width: 760px) {
  .agent-sources__grid { grid-template-columns: 1fr; }
}
</style>
