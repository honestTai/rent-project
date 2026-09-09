<template>
  <section class="agent-welcome-v2">
    <div class="agent-welcome-v2__mark"><t-icon name="robot-2-filled" /></div>
    <span class="agent-welcome-v2__eyebrow">AI 业务工作台 · 只读安全</span>
    <h2>{{ title }}</h2>
    <p>{{ description }}</p>
    <div class="agent-welcome-v2__lanes">
      <button v-for="lane in lanes" :key="lane.key" type="button" @click="$emit('select', lane.prompt)">
        <span><t-icon :name="lane.icon" /></span>
        <strong>{{ lane.title }}</strong>
        <em>{{ lane.detail }}</em>
        <small>开始分析 <t-icon name="arrow-right" /></small>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
export interface WelcomeLane {
  key: string;
  title: string;
  icon: string;
  detail: string;
  prompt: string;
}

defineProps<{ title: string; description: string; lanes: WelcomeLane[] }>();
defineEmits<{ select: [prompt: string] }>();
</script>

<style scoped>
.agent-welcome-v2 { display: grid; width: min(900px, 100%); align-self: center; justify-self: center; place-items: center; margin: auto; padding: 28px 22px 18px; text-align: center; }
.agent-welcome-v2__mark { display: inline-flex; width: 52px; height: 52px; align-items: center; justify-content: center; margin-bottom: 13px; border: 1px solid #cfe2fb; border-radius: 16px; background: linear-gradient(145deg, #eef6ff, #fff); box-shadow: 0 12px 28px rgb(22 119 255 / 13%); color: #1677ff; font-size: 29px; }
.agent-welcome-v2__eyebrow { color: #3975b9; font-size: 11px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
.agent-welcome-v2 h2 { margin: 8px 0 0; color: #13213a; font-size: clamp(26px, 3vw, 36px); font-weight: 700; line-height: 1.25; letter-spacing: -.02em; }
.agent-welcome-v2 p { max-width: 620px; margin: 10px 0 0; color: #66758c; font-size: 14px; line-height: 22px; }
.agent-welcome-v2__lanes { display: grid; width: 100%; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-top: 26px; }
.agent-welcome-v2__lanes button { position: relative; display: grid; min-height: 150px; align-content: flex-start; gap: 8px; padding: 17px; overflow: hidden; border: 1px solid #e1e9f3; border-radius: 14px; background: linear-gradient(150deg, #fff 62%, #f4f8fd); color: inherit; cursor: pointer; text-align: left; transition: transform .18s ease, border-color .18s ease, box-shadow .18s ease; }
.agent-welcome-v2__lanes button::after { position: absolute; right: -20px; bottom: -32px; width: 84px; height: 84px; border-radius: 50%; background: rgb(22 119 255 / 5%); content: ''; }
.agent-welcome-v2__lanes button:hover { border-color: #8fc2ff; box-shadow: 0 14px 30px rgb(37 78 126 / 10%); transform: translateY(-3px); }
.agent-welcome-v2__lanes button > span { display: inline-flex; width: 34px; height: 34px; align-items: center; justify-content: center; border-radius: 10px; background: #eaf3ff; color: #1677ff; font-size: 18px; }
.agent-welcome-v2__lanes strong { color: #1d2b44; font-size: 14px; font-weight: 700; }
.agent-welcome-v2__lanes em { color: #6f7d91; font-size: 12px; font-style: normal; line-height: 18px; }
.agent-welcome-v2__lanes small { display: inline-flex; align-items: center; gap: 4px; margin-top: auto; color: #3975b9; font-size: 11px; font-weight: 600; }
@media (max-width: 900px) { .agent-welcome-v2__lanes { grid-template-columns: 1fr; } .agent-welcome-v2__lanes button { min-height: 118px; } }
</style>
