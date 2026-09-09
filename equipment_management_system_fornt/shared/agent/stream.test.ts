import assert from 'node:assert/strict';
import test from 'node:test';

import { createSseParser } from './stream.ts';

test('parses an event split across network chunks', () => {
  const events: any[] = [];
  const parser = createSseParser((event) => events.push(event));

  parser.push('event: run.started\ndata: {"type":"run.started","runId":"r1",');
  parser.push('"sequence":1,"timestamp":"now","data":{}}\n\n');

  assert.equal(events.length, 1);
  assert.equal(events[0].type, 'run.started');
  assert.equal(events[0].runId, 'r1');
});

test('parses multiple events from one chunk', () => {
  const events: any[] = [];
  const parser = createSseParser((event) => events.push(event));

  parser.push([
    'event: tool.started',
    'data: {"type":"tool.started","runId":"r1","sequence":2,"timestamp":"now","data":{"name":"a"}}',
    '',
    'event: tool.completed',
    'data: {"type":"tool.completed","runId":"r1","sequence":3,"timestamp":"now","data":{"name":"a"}}',
    '',
    '',
  ].join('\n'));

  assert.deepEqual(events.map((event) => event.type), ['tool.started', 'tool.completed']);
});

test('ignores malformed data and continues with later valid events', () => {
  const events: any[] = [];
  const parser = createSseParser((event) => events.push(event));

  parser.push('event: broken\ndata: {broken}\n\n');
  parser.push('event: run.completed\ndata: {"type":"run.completed","runId":"r1","sequence":4,"timestamp":"now","data":{"answer":"ok"}}\n\n');

  assert.equal(events.length, 1);
  assert.equal(events[0].data.answer, 'ok');
});
