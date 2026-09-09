import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

import { parse } from '@vue/compiler-sfc';

test('AgentQuestionPrompt is an accessible touch-friendly Vue component', async () => {
  const source = await readFile(new URL('./AgentQuestionPrompt.vue', import.meta.url), 'utf8');
  const { descriptor, errors } = parse(source, { filename: 'AgentQuestionPrompt.vue' });

  assert.deepEqual(errors, []);
  const template = descriptor.template?.content || '';
  assert.doesNotMatch(template, /aria-live=/);
  assert.match(template, /v-if="props\.questions\.length"/);
  assert.match(template, /aria-hidden="true"/);
  assert.match(template, /<h2\b/);
  assert.doesNotMatch(template, /<h3\b/);
  assert.match(template, /<button\b/);
  assert.match(source, /emit\('select', option\)/);
  assert.match(source, /:focus-visible/);
  assert.match(source, /touch-action:\s*manipulation/);
  assert.match(source, /min-height:\s*40px/);
  assert.match(source, /overflow-wrap:\s*anywhere/);
});

test('AgentChatPanel renders clarifications as the only message content', async () => {
  const source = await readFile(new URL('../AgentChatPanel.vue', import.meta.url), 'utf8');

  assert.match(source, /v-if="isClarificationMessage\(message\)"/);
  assert.match(source, /<AgentQuestionPrompt\b/);
  assert.match(source, /:placeholder="composerPlaceholder"/);
  assert.match(source, /extractAgentQuestions/);
  assert.match(
    source,
    /<p\s+class="agent-sr-only"\s+aria-live="polite"\s+aria-atomic="true"\s*>\s*\{\{\s*clarificationAnnouncement\s*\}\}\s*<\/p>/s,
  );
  const announcerIndex = source.indexOf('<p class="agent-sr-only"');
  const launcherConditionIndex = source.indexOf('v-if="!isPageMode && !panelOpen"');
  assert.ok(announcerIndex > 0 && announcerIndex < launcherConditionIndex);
  assert.equal(source.match(/aria-live="polite"/g)?.length, 1);
  assert.match(source, /const clarificationAnnouncement = computed\(\(\) =>/);
  assert.match(source, /isClarificationMessage\(latestAssistant\)/);
  assert.match(source, /question\.inferred !== true/);
  assert.match(source, /typeof window\.matchMedia === 'function'/);
  assert.match(source, /window\.matchMedia\('\(pointer: fine\)'\)\.matches/);
  assert.match(source, /v-if="shouldShowAgentProcess\(message\)"/);
  assert.match(source, /<AgentRunTimeline\s+v-if="message\.runEvents\?\.length"/s);
  assert.match(source, /<AgentWorkspaceSummary\s+v-if="message\.workspace"/s);
  assert.match(source, /<AgentToolEvidence\s+v-if="message\.toolCalls\?\.length"/s);
  assert.doesNotMatch(source, /class="agent-questions"/);
  assert.doesNotMatch(source, /\.agent-questions\b/);
  assert.doesNotMatch(source, /agent-question-card/);
  assert.doesNotMatch(source, /function extractQuestions\b/);
  assert.doesNotMatch(source, /function inferQuestionsFromText\b/);
  assert.doesNotMatch(source, /function optionLabel\b/);
});
