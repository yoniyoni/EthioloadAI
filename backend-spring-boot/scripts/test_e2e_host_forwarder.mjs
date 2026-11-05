import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { PassThrough } from 'node:stream';
import net from 'node:net';
import test from 'node:test';
import {
  APPROVED_DATABASE_ALIAS,
  APPROVED_DATABASE_ID,
  APPROVED_DATABASE_IP,
  APPROVED_DATABASE_NAME,
  APPROVED_E2E_NETWORK_ID,
  BACKEND_ADDRESS,
  BACKEND_MAC,
  CHILD_TERMINATION_GRACE_MS,
  FORWARDER_EXECUTABLE,
  IDLE_TIMEOUT_MS,
  MAX_CONNECTIONS,
  dockerExecArguments,
  startHostForwarder,
  validateConfig,
  verifyDockerRuntime,
} from './e2e-host-forwarder.mjs';

const helperId = 'a'.repeat(64);
const project = 'isolated-e2e-test';
const networkName = 'isolated-e2e-net';
const backendId = 'b'.repeat(64);
const networkId = APPROVED_E2E_NETWORK_ID;
const databaseId = APPROVED_DATABASE_ID;
const databaseName = APPROVED_DATABASE_NAME;
const databaseAlias = APPROVED_DATABASE_ALIAS;
const runtimeRequirements = {
  networkId,
  databaseId,
  databaseName,
  databaseAlias,
  databaseIP: APPROVED_DATABASE_IP,
};

function runtimeOutputs(overrides = {}) {
  const helperNetworks = overrides.helperNetworks ??
    { [networkName]: { IPAddress: '172.26.0.4' } };
  const helperPortBindings = overrides.helperPortBindings ?? {};
  const backendNetworks = overrides.backendNetworks ??
    { [networkName]: { IPAddress: '172.26.0.3', MacAddress: BACKEND_MAC } };
  const backendPortBindings = overrides.backendPortBindings ?? {};
  const databaseNetworks = overrides.databaseNetworks ?? {
    [networkName]: {
      NetworkID: networkId,
      IPAddress: runtimeRequirements.databaseIP,
      Aliases: [databaseAlias],
    },
  };
  const networkContainers = overrides.networkContainers ?? {
    [helperId]: { Name: `${project}-forwarder-1`, IPv4Address: '172.26.0.4/16' },
    [backendId]: { Name: `${project}-backend-1`, IPv4Address: '172.26.0.3/16' },
    [databaseId]: { Name: databaseName, IPv4Address: `${runtimeRequirements.databaseIP}/16` },
  };
  return {
    helper: [
      helperId, `/${project}-forwarder-1`, 'true', project, 'forwarder', 'false',
      JSON.stringify(helperNetworks), JSON.stringify(helperPortBindings),
    ].join('|'),
    backend: [
      backendId, `/${project}-backend-1`, 'true', project, 'backend', 'false',
      JSON.stringify(backendNetworks), JSON.stringify(backendPortBindings),
    ].join('|'),
    database: [
      databaseId, `/${databaseName}`, 'true', JSON.stringify(databaseNetworks),
    ].join('|'),
    network: `${networkId}|true|${JSON.stringify(networkContainers)}`,
  };
}

function mockDocker(outputs = runtimeOutputs()) {
  return async (args) => {
    if (args[0] === 'network') return outputs.network;
    if (args.at(-1) === databaseName) return outputs.database;
    if (args.at(-1) === helperId) return outputs.helper;
    if (args.at(-1) === `${project}-backend-1`) return outputs.backend;
    throw new Error('unexpected Docker command');
  };
}

test('host forwarder has one fixed destination and one fixed docker exec command', () => {
  assert.equal(BACKEND_ADDRESS, '172.26.0.3:8080');
  assert.deepEqual(dockerExecArguments(helperId), [
    'exec', '--interactive', helperId, FORWARDER_EXECUTABLE, 'forward',
  ]);
  assert.equal(dockerExecArguments(helperId).includes('sh'), false);
  assert.equal(dockerExecArguments(helperId).includes(BACKEND_ADDRESS), false);
});

test('configuration requires exact container identity inputs and rejects malformed values', () => {
  assert.deepEqual(validateConfig({ helperId, project, networkName }), {
    helperId,
    project,
    networkName,
    helperName: `/${project}-forwarder-1`,
    backendName: `/${project}-backend-1`,
  });
  assert.throws(() => validateConfig({ helperId: 'not-an-id', project, networkName }));
  assert.throws(() => validateConfig({ helperId, project: '../bad', networkName }));
  assert.throws(() => validateConfig({ helperId, project, networkName: 'bad network' }));
});

test('runtime validation requires running expected helper/backend on one internal network', async () => {
  assert.equal(await verifyDockerRuntime(
    { helperId, project, networkName },
    mockDocker(),
  ), true);
  assert.equal(await verifyDockerRuntime(
    { helperId, project, networkName },
    mockDocker(runtimeOutputs({ helperNetworks: { application: { IPAddress: '10.0.0.2' } } })),
  ), false);
  assert.equal(await verifyDockerRuntime(
    { helperId, project, networkName },
    mockDocker(runtimeOutputs({ backendNetworks: { application: { IPAddress: '172.26.0.3' } } })),
  ), false);
  assert.equal(await verifyDockerRuntime(
    { helperId, project, networkName },
    mockDocker(runtimeOutputs({
      backendNetworks: {
        [networkName]: { IPAddress: '172.26.0.3', MacAddress: '00:00:00:00:00:00' },
      },
    })),
  ), false);
  assert.equal(await verifyDockerRuntime(
    { helperId, project, networkName },
    mockDocker(runtimeOutputs({ networkContainers: {} })),
  ), false);
  const publicNetwork = mockDocker();
  const publicRunner = async (args) => args[0] === 'network'
    ? `${networkId}|false|${JSON.stringify(JSON.parse(runtimeOutputs().network.split('|')[2]))}`
    : publicNetwork(args);
  assert.equal(await verifyDockerRuntime(
    { helperId, project, networkName },
    publicRunner,
  ), false);
  assert.equal(await verifyDockerRuntime(
    { helperId, project, networkName },
    mockDocker(),
    '172.29.0.3',
  ), false);
  assert.equal(await verifyDockerRuntime(
    { helperId, project, networkName },
    mockDocker(runtimeOutputs({
      databaseNetworks: {
        [networkName]: {
          NetworkID: networkId,
          IPAddress: runtimeRequirements.databaseIP,
          Aliases: ['unexpected-alias'],
        },
      },
    })),
  ), false);
  assert.equal(await verifyDockerRuntime(
    { helperId, project, networkName },
    mockDocker(),
    '172.26.0.3',
    { ...runtimeRequirements, networkId: 'f'.repeat(64) },
  ), false);
  assert.equal(await verifyDockerRuntime(
    { helperId, project, networkName },
    mockDocker(runtimeOutputs({ helperPortBindings: { '12526/tcp': [{ HostPort: '12526' }] } })),
  ), false);
  assert.equal(await verifyDockerRuntime(
    { helperId, project, networkName },
    mockDocker(runtimeOutputs({ backendPortBindings: { '8080/tcp': [{ HostPort: '8080' }] } })),
  ), false);
  assert.equal(await verifyDockerRuntime(
    { helperId, project, networkName },
    mockDocker(),
    '172.26.0.3',
    { ...runtimeRequirements, databaseId: 'f'.repeat(64) },
  ), false);
});

test('runtime validation fails closed when Docker is unavailable or identities mismatch', async () => {
  assert.equal(await verifyDockerRuntime(
    { helperId, project, networkName },
    async () => { throw new Error('sensitive Docker output'); },
  ), false);
  assert.equal(await verifyDockerRuntime(
    { helperId: 'c'.repeat(64), project, networkName },
    mockDocker(),
  ), false);
});

test('host listener is loopback-only and pipes raw bytes through fixed Docker exec', async () => {
  const executions = [];
  const running = await startHostForwarder(
    { helperId, project, networkName },
    {
      listenPort: 0,
      runDocker: mockDocker(),
      spawnProcess: (command, args, options) => {
        executions.push({ command, args, options });
        const child = new EventEmitter();
        child.stdin = new PassThrough();
        child.stdout = new PassThrough();
        child.exitCode = null;
        child.signalCode = null;
        child.kill = () => {
          child.exitCode = 1;
          child.stdin.destroy();
          child.stdout.end();
          child.emit('close', 1, null);
        };
        const request = [];
        child.stdin.on('data', (chunk) => request.push(chunk));
        child.stdin.once('end', () => {
          child.stdout.end(Buffer.concat(request));
          child.exitCode = 0;
          child.emit('close', 0, null);
        });
        return child;
      },
    },
  );

  try {
    assert.equal(running.address.address, '127.0.0.1');
    const request = Buffer.from(
      'POST /path?q=one HTTP/1.1\r\nContent-Length: 4\r\nX-Test: yes\r\n\r\ntest',
    );
    const response = await new Promise((resolve, reject) => {
      const socket = net.createConnection(running.address.port, '127.0.0.1');
      const chunks = [];
      socket.once('error', reject);
      socket.on('data', (chunk) => chunks.push(chunk));
      socket.once('end', () => resolve(Buffer.concat(chunks)));
      socket.once('connect', () => socket.end(request));
    });

    assert.deepEqual(response, request);
    assert.equal(executions.length, 1);
    assert.equal(executions[0].command, 'docker');
    assert.deepEqual(executions[0].args, dockerExecArguments(helperId));
    assert.equal(executions[0].options.shell, false);
    assert.deepEqual(executions[0].options.stdio, ['pipe', 'pipe', 'ignore']);
  } finally {
    await running.close();
  }
});

test('shutdown terminates Docker exec and waits for its close event', async () => {
  let child;
  let resolveSpawn;
  const spawned = new Promise((resolve) => { resolveSpawn = resolve; });
  let killCalls = 0;
  const running = await startHostForwarder(
    { helperId, project, networkName },
    {
      listenPort: 0,
      runDocker: mockDocker(),
      spawnProcess: () => {
        child = new EventEmitter();
        child.stdin = new PassThrough();
        child.stdout = new PassThrough();
        child.exitCode = null;
        child.signalCode = null;
        child.kill = (signal) => {
          killCalls += 1;
          assert.equal(signal, 'SIGTERM');
          setTimeout(() => {
            child.signalCode = 'SIGTERM';
            child.stdin.destroy();
            child.stdout.destroy();
            child.emit('close', null, 'SIGTERM');
          }, 50);
          return true;
        };
        resolveSpawn(child);
        return child;
      },
    },
  );
  const socket = net.createConnection(running.address.port, '127.0.0.1');
  await new Promise((resolve, reject) => {
    socket.once('connect', resolve);
    socket.once('error', reject);
  });
  await spawned;

  const closed = new Promise((resolve) => child.once('close', resolve));
  const started = Date.now();
  await running.close();
  await closed;
  assert.ok(Date.now() - started >= 40, 'close returned before child close event');
  assert.equal(killCalls, 1);
  assert.equal(child.signalCode, 'SIGTERM');
});

test('connection limit, idle timeout, and child grace period stay bounded', () => {
  assert.equal(MAX_CONNECTIONS, 16);
  assert.equal(IDLE_TIMEOUT_MS, 60_000);
  assert.equal(CHILD_TERMINATION_GRACE_MS, 5_000);
});
