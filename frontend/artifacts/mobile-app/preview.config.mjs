import { fileURLToPath } from 'node:url';

// Serve the supplied Flutter web build without requiring the Flutter SDK.
export default {
  root: fileURLToPath(new URL('./', import.meta.url)),
  build: { outDir: 'build/web' },
  preview: { host: '127.0.0.1', port: 8765, strictPort: true },
};
