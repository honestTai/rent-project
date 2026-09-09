<template>
  <section
    v-if="props.questions.length"
    class="agent-question-prompt"
    aria-label="需要补充信息"
  >
    <header class="agent-question-prompt__header">
      <span class="agent-question-prompt__icon" aria-hidden="true">
        <t-icon name="help-circle" />
      </span>
      <span class="agent-question-prompt__eyebrow">需要补充信息</span>
    </header>

    <div class="agent-question-prompt__list">
      <article
        v-for="(question, questionIndex) in props.questions"
        :key="`${headingId(questionIndex)}-${question.question}`"
        class="agent-question-prompt__item"
        :aria-labelledby="headingId(questionIndex)"
      >
        <h2 :id="headingId(questionIndex)" class="agent-question-prompt__title">
          {{ question.question }}
        </h2>
        <p v-if="question.description" class="agent-question-prompt__description">
          {{ question.description }}
        </p>
        <div v-if="question.options?.length" class="agent-question-prompt__options">
          <button
            v-for="(option, optionIndex) in question.options"
            :key="`${headingId(questionIndex)}-option-${optionIndex}`"
            class="agent-question-prompt__option"
            type="button"
            @click="emit('select', option)"
          >
            {{ optionLabel(option) }}
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { useId } from 'vue';

import type { AgentQuestion, AnyRecord } from '../../agent/types';

const props = defineProps<{
  questions: AgentQuestion[];
}>();

const emit = defineEmits<{
  select: [option: string | AnyRecord];
}>();

const componentId = useId();

function headingId(questionIndex: number) {
  return `${componentId}-question-${questionIndex}`;
}

function optionLabel(option: string | AnyRecord) {
  if (typeof option === 'string') return option;
  return String(option.label ?? option.title ?? option.value ?? option.text ?? '-');
}
</script>

<style scoped>
.agent-question-prompt {
  box-sizing: border-box;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  padding: 16px;
  overflow: hidden;
  border: 1px solid #d8e8ff;
  border-radius: 12px;
  background: #f5f9ff;
  color: #1f2937;
}

.agent-question-prompt__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.agent-question-prompt__icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #e7f1ff;
  color: #1677ff;
  font-size: 17px;
}

.agent-question-prompt__eyebrow {
  color: #2367ad;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.agent-question-prompt__list {
  display: grid;
  min-width: 0;
  gap: 16px;
}

.agent-question-prompt__item {
  min-width: 0;
  overflow-wrap: anywhere;
}

.agent-question-prompt__item + .agent-question-prompt__item {
  padding-top: 16px;
  border-top: 1px solid #dfeaff;
}

.agent-question-prompt__title {
  margin: 0;
  color: #172b4d;
  font-size: 15px;
  font-weight: 650;
  line-height: 1.55;
  overflow-wrap: anywhere;
  text-wrap: balance;
}

.agent-question-prompt__description {
  margin: 6px 0 0;
  color: #52657a;
  font-size: 13px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.agent-question-prompt__options {
  display: grid;
  min-width: 0;
  gap: 8px;
  margin-top: 12px;
}

.agent-question-prompt__option {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  min-height: 40px;
  padding: 9px 12px;
  overflow-wrap: anywhere;
  border: 1px solid #c8dcf7;
  border-radius: 8px;
  background: #fff;
  color: #20466f;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  text-align: left;
  touch-action: manipulation;
}

.agent-question-prompt__option:hover {
  border-color: #7eb2ee;
  background: #edf5ff;
  color: #0b5fc2;
}

.agent-question-prompt__option:focus-visible {
  border-color: #1677ff;
  outline: 3px solid rgb(22 119 255 / 24%);
  outline-offset: 2px;
}

@media (max-width: 560px) {
  .agent-question-prompt {
    padding: 14px;
    border-radius: 10px;
  }

  .agent-question-prompt__title {
    font-size: 14px;
  }
}
</style>
