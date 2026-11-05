import net from "node:net";
import { fileURLToPath } from "node:url";

const viteModule = await import(new URL(
  "../frontend/artifacts/freight-link/node_modules/vite/dist/node/index.js",
  import.meta.url,
).href);
const { createServer, preview } = viteModule;

const [role, pipeName] = process.argv.slice(2);
if (!["web", "flutter"].includes(role) || !/^EthioloadAI-dev-[a-f0-9]{32}$/.test(pipeName ?? "")) {
  throw new Error("Invalid development server mode.");
}

const configFile = role === "web"
  ? fileURLToPath(new URL("../frontend/artifacts/freight-link/vite.config.ts", import.meta.url))
  : fileURLToPath(new URL("../frontend/artifacts/mobile-app/preview.config.mjs", import.meta.url));

let server;
if (role === "web") {
  server = await createServer({ configFile, clearScreen: false });
  await server.listen();
} else {
  server = await preview({ configFile });
}

const controlServer = net.createServer((socket) => {
  let command = "";
  socket.setEncoding("utf8");
  socket.on("data", (chunk) => {
    command += chunk;
    if (!command.includes("\n")) return;
    if (command.trim() !== "STOP") {
      socket.end("ERROR\n");
      return;
    }
    socket.end("STOPPING\n");
    controlServer.close();
    const close = role === "web"
      ? server.close()
      : new Promise((resolve, reject) => {
          server.httpServer.close((error) => error ? reject(error) : resolve());
        });
    close.then(() => process.exit(0), (error) => {
      console.error("Graceful Vite shutdown failed:", error.message);
      process.exit(1);
    });
  });
});

controlServer.listen(`\\\\.\\pipe\\${pipeName}`);
server.printUrls();
