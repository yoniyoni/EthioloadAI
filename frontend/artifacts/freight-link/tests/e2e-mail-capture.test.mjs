import assert from 'node:assert/strict';
import test from 'node:test';
import { captureContainerName, createCaptureClient } from '../scripts/e2e-mail-capture.mjs';

function mockDocker(outputs) {
  const calls = [];
  const execute = (command, args, options, callback) => {
    calls.push({ command, args, options });
    const output = outputs.shift();
    callback(output.error || null, output.stdout || '', '');
  };
  return { execute, calls };
}

test('capture retrieval checks exact identity and reads key only inside fixed docker exec', async () => {
  const docker = mockDocker([
    { stdout: `/${captureContainerName}|true|mail-capture` },
    { stdout: JSON.stringify({ cursor: 12 }) },
  ]);
  const client = createCaptureClient(docker.execute);
  assert.equal(await client.cursor(), 12);
  assert.equal(docker.calls[0].command, 'docker');
  assert.deepEqual(docker.calls[1].args.slice(0, 3), ['exec', captureContainerName, 'python']);
  assert.equal(docker.calls[1].args.at(-1), '/cursor');
  assert.ok(docker.calls[1].args.includes('-B'));
  assert.ok(docker.calls[1].args.includes('/app/capture-invitation-smtp-client.py'));
  assert.ok(!docker.calls[1].args.some(arg => arg.includes('test-mail-capture-key-value')));
  assert.equal(Object.hasOwn(docker.calls[1].options, 'env'), false);
});

test('cursor retrieval rejects stale messages and requires one exact recipient and message cursor', async () => {
  const docker = mockDocker([
    { stdout: `/${captureContainerName}|true|mail-capture` },
    { stdout: JSON.stringify({
      count: 3,
      messages: [
        { cursor: 2, envelope_recipients: ['target@example.test'], token: 'stale' },
        { cursor: 8, envelope_recipients: ['other@example.test'], token: 'other' },
        { cursor: 9, envelope_recipients: ['target@example.test'], token: 'new' },
      ],
    }) },
  ]);
  const mail = await createCaptureClient(docker.execute)
    .message(4, 'target@example.test', 9);
  assert.equal(mail.token, 'new');
});

test('capture retrieval sanitizes failures and rejects a different container identity', async () => {
  const docker = mockDocker([{ stdout: '/unexpected|true|mail-capture' }]);
  const client = createCaptureClient(docker.execute);
  await assert.rejects(client.cursor(), /identity is not the expected one/);
  assert.equal(docker.calls.length, 1);
});

test('ambiguous messages fail without exposing invitation tokens in errors', async () => {
  const docker = mockDocker([
    { stdout: `/${captureContainerName}|true|mail-capture` },
    { stdout: JSON.stringify({
      count: 2,
      messages: [
        { cursor: 5, envelope_recipients: ['target@example.test'], token: 'private-token-one' },
        { cursor: 6, envelope_recipients: ['target@example.test'], token: 'private-token-two' },
      ],
    }) },
  ]);
  await assert.rejects(
    createCaptureClient(docker.execute).message(4, 'target@example.test'),
    error => !error.message.includes('private-token'),
  );
});
