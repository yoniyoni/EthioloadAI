import { execFile } from 'node:child_process';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import net from 'node:net';

export const HOST_ADDRESS = '127.0.0.1';
export const HOST_PORT = 8080;
export const BACKEND_ADDRESS = '172.26.0.3:8080';
export const FORWARDER_EXECUTABLE = '/e2e-tcp-forwarder';
export const BACKEND_MAC = '82:c1:56:8b:59:3b';
export const APPROVED_E2E_NETWORK_ID =
  'a7321f4f4d63c0d57d279865ea2094b137237588291a53e024a5c323c977b344';
export const APPROVED_DATABASE_ID =
  'bacc65efd564d7f248056ae29417ae48bbfbe9f68d441576c7abb65ac4f22583';
export const APPROVED_DATABASE_NAME = 'ethioloadai-stage3a-e2e-db-20261101';
export const APPROVED_DATABASE_ALIAS = 'ethioloadai-e2e-db';
export const APPROVED_DATABASE_IP = '172.26.0.2';
export const MAX_CONNECTIONS = 16;
export const IDLE_TIMEOUT_MS = 60_000;
export const DOCKER_COMMAND_TIMEOUT_MS = 10_000;
export const CHILD_TERMINATION_GRACE_MS = 5_000;

const HELPER_INSPECT_FORMAT =
  '{{.Id}}|{{.Name}}|{{.State.Running}}|' +
  '{{index .Config.Labels "com.docker.compose.project"}}|' +
  '{{index .Config.Labels "com.docker.compose.service"}}|' +
  '{{index .Config.Labels "com.docker.compose.oneoff"}}|' +
  '{{json .NetworkSettings.Networks}}|' +
  '{{json .HostConfig.PortBindings}}';
const BACKEND_INSPECT_FORMAT =
  '{{.Id}}|{{.Name}}|{{.State.Running}}|' +
  '{{index .Config.Labels "com.docker.compose.project"}}|' +
  '{{index .Config.Labels "com.docker.compose.service"}}|' +
  '{{index .Config.Labels "com.docker.compose.oneoff"}}|' +
  '{{json .NetworkSettings.Networks}}|' +
  '{{json .HostConfig.PortBindings}}';
const DATABASE_INSPECT_FORMAT =
  '{{.Id}}|{{.Name}}|{{.State.Running}}|{{json .NetworkSettings.Networks}}';
const NETWORK_INSPECT_FORMAT = '{{.Id}}|{{.Internal}}|{{json .Containers}}';

const APPROVED_RUNTIME = Object.freeze({
  networkId: APPROVED_E2E_NETWORK_ID,
  databaseId: APPROVED_DATABASE_ID,
  databaseName: APPROVED_DATABASE_NAME,
  databaseAlias: APPROVED_DATABASE_ALIAS,
  databaseIP: APPROVED_DATABASE_IP,
});

export class ForwarderStartupError extends Error {
  constructor() {
    super('The expected E2E forwarder or backend identity could not be verified');
    this.name = 'ForwarderStartupError';
  }
}

export function validateConfig({ helperId, project, networkName }) {
  if (!/^[a-f0-9]{64}$/.test(helperId ?? '')) {
    throw new TypeError('An exact Docker helper container ID is required');
  }
  if (!/^[a-z0-9][a-z0-9_-]{0,62}$/.test(project ?? '')) {
    throw new TypeError('A valid Docker Compose project name is required');
  }
  if (!/^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$/.test(networkName ?? '')) {
    throw new TypeError('A valid E2E Docker network name is required');
  }
  return {
    helperId,
    project,
    networkName,
    helperName: `/${project}-forwarder-1`,
    backendName: `/${project}-backend-1`,
  };
}

export function dockerExecArguments(helperId) {
  return ['exec', '--interactive', helperId, FORWARDER_EXECUTABLE, 'forward'];
}

export async function verifyDockerRuntime(
  config,
  runDocker = runDockerCommand,
  expectedBackendIP = '172.26.0.3',
  runtimeRequirements = APPROVED_RUNTIME,
) {
  const expected = validateConfig(config);
  try {
    const [helperOutput, backendOutput, databaseOutput, networkOutput] = await Promise.all([
      runDocker([
        'inspect',
        `--format=${HELPER_INSPECT_FORMAT}`,
        expected.helperId,
      ]),
      runDocker([
        'inspect',
        `--format=${BACKEND_INSPECT_FORMAT}`,
        expected.backendName.slice(1),
      ]),
      runDocker([
        'inspect',
        `--format=${DATABASE_INSPECT_FORMAT}`,
        runtimeRequirements.databaseName,
      ]),
      runDocker([
        'network',
        'inspect',
        `--format=${NETWORK_INSPECT_FORMAT}`,
        expected.networkName,
      ]),
    ]);

    const helper = parseContainerInspect(helperOutput);
    const backend = parseContainerInspect(backendOutput);
    const database = parseDatabaseInspect(databaseOutput);
    const network = parseNetworkInspect(networkOutput);
    const helperNetworkNames = Object.keys(helper.networks);
    const backendNetworkNames = Object.keys(backend.networks);
    const databaseNetworkNames = Object.keys(database.networks);
    const networkContainers = network.containers;

    return network.id === runtimeRequirements.networkId &&
      network.internal === true &&
      helper.id === expected.helperId &&
      helper.name === expected.helperName &&
      helper.running === true &&
      helper.project === expected.project &&
      helper.service === 'forwarder' &&
      helper.oneoff === 'false' &&
      helperNetworkNames.length === 1 &&
      helperNetworkNames[0] === expected.networkName &&
      hasNoPublishedPorts(helper.portBindings) &&
      backend.name === expected.backendName &&
      backend.running === true &&
      backend.project === expected.project &&
      backend.service === 'backend' &&
      backend.oneoff === 'false' &&
      backendNetworkNames.length === 1 &&
      backendNetworkNames[0] === expected.networkName &&
      hasNoPublishedPorts(backend.portBindings) &&
      backend.networks[expected.networkName]?.IPAddress === expectedBackendIP &&
      backend.networks[expected.networkName]?.MacAddress?.toLowerCase() === BACKEND_MAC &&
      database.id === runtimeRequirements.databaseId &&
      database.name === `/${runtimeRequirements.databaseName}` &&
      database.running === true &&
      databaseNetworkNames.length === 1 &&
      databaseNetworkNames[0] === expected.networkName &&
      database.networks[expected.networkName]?.NetworkID === runtimeRequirements.networkId &&
      database.networks[expected.networkName]?.IPAddress === runtimeRequirements.databaseIP &&
      database.networks[expected.networkName]?.Aliases?.includes(runtimeRequirements.databaseAlias) === true &&
      networkContainers[helper.id]?.Name === expected.helperName.slice(1) &&
      networkContainers[backend.id]?.Name === expected.backendName.slice(1) &&
      networkContainers[backend.id]?.IPv4Address?.startsWith(`${expectedBackendIP}/`) &&
      networkContainers[database.id]?.Name === runtimeRequirements.databaseName &&
      networkContainers[database.id]?.IPv4Address?.startsWith(`${runtimeRequirements.databaseIP}/`);
  } catch {
    return false;
  }
}

function parseContainerInspect(output) {
  const parts = output.trim().split('|');
  if (parts.length !== 8) throw new TypeError('Invalid Docker inspect result');
  const networks = JSON.parse(parts[6]);
  const portBindings = JSON.parse(parts[7]);
  if (!networks || typeof networks !== 'object' || Array.isArray(networks)) {
    throw new TypeError('Invalid Docker network result');
  }
  return {
    id: parts[0],
    name: parts[1],
    running: parts[2] === 'true',
    project: parts[3],
    service: parts[4],
    oneoff: parts[5],
    networks,
    portBindings,
  };
}

function parseDatabaseInspect(output) {
  const parts = output.trim().split('|');
  if (parts.length !== 4) throw new TypeError('Invalid database inspect result');
  const networks = JSON.parse(parts[3]);
  if (!networks || typeof networks !== 'object' || Array.isArray(networks)) {
    throw new TypeError('Invalid database network result');
  }
  return {
    id: parts[0],
    name: parts[1],
    running: parts[2] === 'true',
    networks,
  };
}

function hasNoPublishedPorts(bindings) {
  if (bindings === null) return true;
  if (!bindings || typeof bindings !== 'object' || Array.isArray(bindings)) return false;
  return Object.values(bindings).every((entries) => entries === null ||
    (Array.isArray(entries) && entries.length === 0));
}

function parseNetworkInspect(output) {
  const parts = output.trim().split('|');
  if (parts.length !== 3) throw new TypeError('Invalid Docker network inspection');
  const containers = JSON.parse(parts[2]);
  if (!containers || typeof containers !== 'object' || Array.isArray(containers)) {
    throw new TypeError('Invalid Docker network containers');
  }
  return { id: parts[0], internal: parts[1] === 'true', containers };
}

function runDockerCommand(args) {
  return new Promise((resolve, reject) => {
    execFile(
      'docker',
      args,
      {
        encoding: 'utf8',
        timeout: DOCKER_COMMAND_TIMEOUT_MS,
        maxBuffer: 128 * 1024,
        windowsHide: true,
        shell: false,
      },
      (error, stdout) => {
        if (error) {
          reject(new ForwarderStartupError());
          return;
        }
        resolve(stdout.trim());
      },
    );
  });
}

export async function startHostForwarder(
  config,
  {
    listenPort = HOST_PORT,
    runDocker = runDockerCommand,
    spawnProcess = spawn,
    expectedBackendIP = '172.26.0.3',
    runtimeRequirements = APPROVED_RUNTIME,
  } = {},
) {
  const expected = validateConfig(config);
  if (!Number.isInteger(listenPort) || listenPort < 0 || listenPort > 65535) {
    throw new TypeError('Invalid test listener port');
  }
  let verification = null;
  const verifyRuntime = () => {
    if (!verification) {
      verification = verifyDockerRuntime(
        expected,
        runDocker,
        expectedBackendIP,
        runtimeRequirements,
      )
        .finally(() => { verification = null; });
    }
    return verification;
  };
  if (!await verifyRuntime()) {
    throw new ForwarderStartupError();
  }

  const active = new Map();
  const children = new Set();
  let closePromise;
  const server = net.createServer(
    { allowHalfOpen: true, pauseOnConnect: true },
    (socket) => {
      if (active.size >= MAX_CONNECTIONS) {
        socket.destroy();
        return;
      }

      const state = { child: null, childClosed: null, forceKillTimer: null, finished: false };
      active.set(socket, state);
      socket.setTimeout(IDLE_TIMEOUT_MS, () => {
        socket.destroy();
        stopChild(state);
      });

      void verifyRuntime().then((valid) => {
        if (!valid || socket.destroyed) {
          socket.destroy();
          finish(socket, state, active);
          return;
        }
        startDockerExec(socket, state, active, children, expected.helperId, spawnProcess);
      });
    },
  );

  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(listenPort, HOST_ADDRESS, () => {
      server.removeListener('error', reject);
      resolve();
    });
  });
  return {
    server,
    address: server.address(),
    close: () => {
      closePromise ??= closeServer(server, active, children);
      return closePromise;
    },
  };
}

function startDockerExec(socket, state, active, children, helperId, spawnProcess) {
  let child;
  try {
    child = spawnProcess('docker', dockerExecArguments(helperId), {
      windowsHide: true,
      shell: false,
      stdio: ['pipe', 'pipe', 'ignore'],
    });
  } catch {
    socket.destroy();
    finish(socket, state, active);
    return;
  }
  state.child = child;
  children.add(state);
  state.childClosed = new Promise((resolve) => {
    child.once('close', () => {
      clearTimeout(state.forceKillTimer);
      children.delete(state);
      resolve();
    });
  });

  const startupTimer = setTimeout(() => {
    socket.destroy();
    stopChild(state);
  }, 15_000);
  const clearStartupTimer = () => clearTimeout(startupTimer);
  const shutDown = () => {
    if (!child.stdin.destroyed) child.stdin.end();
  };

  socket.on('error', () => stopChild(state));
  socket.on('end', shutDown);
  socket.on('close', () => {
    shutDown();
    stopChild(state);
  });
  child.once('error', () => {
    clearStartupTimer();
    socket.destroy();
    finish(socket, state, active);
  });
  child.stdin.on('error', () => {
    socket.destroy();
    stopChild(state);
  });
  child.stdout.on('error', () => {
    socket.destroy();
    stopChild(state);
  });
  socket.once('data', clearStartupTimer);
  child.once('close', () => {
    clearStartupTimer();
    if (!socket.destroyed) socket.end();
    finish(socket, state, active);
  });

  socket.pipe(child.stdin);
  child.stdout.pipe(socket);
}

function stopChild(state) {
  const child = state.child;
  if (child && !state.terminationRequested &&
      child.exitCode === null && child.signalCode === null) {
    state.terminationRequested = true;
    if (!child.stdin.destroyed) child.stdin.destroy();
    try {
      child.kill('SIGTERM');
    } catch {
      state.terminationRequested = false;
    }
    if (!state.forceKillTimer) {
      state.forceKillTimer = setTimeout(() => {
        if (child.exitCode === null && child.signalCode === null) child.kill('SIGKILL');
      }, CHILD_TERMINATION_GRACE_MS);
      state.forceKillTimer.unref();
    }
  }
}

function finish(socket, state, active) {
  if (state.finished) return;
  state.finished = true;
  active.delete(socket);
}

async function closeServer(server, active, children) {
  const serverClosed = new Promise((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  });
  const childrenToReap = [...children];
  for (const [socket, state] of active) {
    socket.destroy();
  }
  for (const state of childrenToReap) {
    stopChild(state);
  }
  await Promise.all([
    serverClosed,
    ...childrenToReap.map((state) => state.childClosed),
  ]);
}

function parseCliArguments(args) {
  const values = new Map();
  for (let index = 0; index < args.length; index += 2) {
    const flag = args[index];
    if (!['--project', '--network', '--helper-id'].includes(flag) ||
        index + 1 >= args.length || values.has(flag)) {
      throw new TypeError('Expected --project, --network, and --helper-id');
    }
    values.set(flag, args[index + 1]);
  }
  if (values.size !== 3) {
    throw new TypeError('Expected --project, --network, and --helper-id');
  }
  return {
    project: values.get('--project'),
    networkName: values.get('--network'),
    helperId: values.get('--helper-id'),
  };
}

async function main() {
  let config;
  try {
    config = parseCliArguments(process.argv.slice(2));
    const running = await startHostForwarder(config);
    process.stdout.write(`E2E host forwarder listening on ${HOST_ADDRESS}:${HOST_PORT}\n`);
    const stop = () => {
      void running.close().then(() => process.exit(0), () => process.exit(1));
    };
    process.once('SIGINT', stop);
    process.once('SIGTERM', stop);
  } catch {
    process.stderr.write('Could not verify or start the E2E host forwarder\n');
    process.exitCode = 1;
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  void main();
}
