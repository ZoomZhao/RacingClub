import http from 'node:http';
import { realpathSync } from 'node:fs';
import { readFile, realpath } from 'node:fs/promises';
import { extname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)));
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml', '.json': 'application/json; charset=utf-8', '.txt': 'text/plain; charset=utf-8' };
export function createStaticServer(directory = root) {
 const base = realpathSync(directory);
 const folders = ['src', 'public', 'design'].map(name => resolve(base, name) + sep);
 const isPublic = file => file === resolve(base, 'index.html') || folders.some(folder => file.startsWith(folder));
 return http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    const pathname = decodeURIComponent(url.pathname);
    const file = resolve(base, '.' + (pathname === '/' ? '/index.html' : pathname));
    // Check canonical file boundaries, never the still-encoded request prefix.
    // Resolve symlinks as well, so a public alias cannot expose a private file.
    if (!isPublic(file) || !isPublic(await realpath(file))) {
      res.writeHead(404).end('Not found'); return;
    }
    const data = await readFile(file);
    res.writeHead(200, { 'Content-Type': mime[extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache', 'X-Content-Type-Options': 'nosniff' });
    res.end(data);
  } catch {
    res.writeHead(404).end('Not found');
  }
 });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
 const port = Number(process.env.PORT || 4173);
 const host = process.env.HOST || '127.0.0.1';
 createStaticServer().listen(port, host, () => console.log(`RacingClub ready: http://${host}:${port}`));
}
