import { execFile } from 'node:child_process';

export const captureContainerName = 'ethioloadai-e2e-mail-capture';

function runDocker(args, execute = execFile) {
  return new Promise((resolve, reject) => {
    execute('docker', args, {
      windowsHide: true,
      encoding: 'utf8',
      timeout: 15000,
      maxBuffer: 4 * 1024 * 1024,
    }, (error, stdout, stderr) => {
      void stderr;
      if (error) {
        reject(new Error('Could not access the expected E2E mail capture container'));
        return;
      }
      resolve(stdout.trim());
    });
  });
}

function identityTemplate() {
  return '{{.Name}}|{{.State.Running}}|{{index .Config.Labels "com.docker.compose.service"}}';
}

export function createCaptureClient(execute = execFile) {
  async function request(path) {
    const identity = await runDocker([
      'inspect',
      `--format=${identityTemplate()}`,
      captureContainerName,
    ], execute);
    if (identity !== `/${captureContainerName}|true|mail-capture`) {
      throw new Error('The E2E mail capture container identity is not the expected one');
    }
    return runDocker([
      'exec',
      captureContainerName,
      'python',
      '-B',
      '/app/capture-invitation-smtp-client.py',
      path,
    ], execute);
  }

  async function cursor() {
    const raw = await request('/cursor');
    let response;
    try {
      response = JSON.parse(raw);
    } catch {
      throw new Error('The E2E mail capture cursor request failed');
    }
    if (!response || !Number.isSafeInteger(response.cursor) || response.cursor < 0) {
      throw new Error('The E2E mail capture cursor response was invalid');
    }
    return response.cursor;
  }

  async function message(after, recipient, expectedCursor) {
    if (!Number.isSafeInteger(after) || after < 0 || !recipient
        || (expectedCursor !== undefined
          && (!Number.isSafeInteger(expectedCursor) || expectedCursor <= after))) {
      throw new Error('The requested capture cursor was invalid');
    }
    let response;
    const raw = await request(`/?after=${after}`);
    try {
      response = JSON.parse(raw);
    } catch {
      throw new Error('The E2E mail capture message request failed');
    }
    if (!response || !Array.isArray(response.messages)
        || response.count !== response.messages.length) {
      throw new Error('The E2E mail capture message response was invalid');
    }
    const matches = response.messages.filter(item =>
      Array.isArray(item.envelope_recipients)
      && item.envelope_recipients.length === 1
      && item.envelope_recipients[0] === recipient
      && Number.isSafeInteger(item.cursor)
      && item.cursor > after
      && (expectedCursor === undefined || item.cursor === expectedCursor));
    if (matches.length !== 1) {
      throw new Error('Expected exactly one matching new invitation message');
    }
    return matches[0];
  }

  return { cursor, message };
}

const defaultClient = createCaptureClient();
export const captureCursor = () => defaultClient.cursor();
export const capturedMessage = (after, recipient, expectedCursor) =>
  defaultClient.message(after, recipient, expectedCursor);
