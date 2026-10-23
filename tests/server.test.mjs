import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, symlink, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createStaticServer } from '../server.mjs';

test('static preview serves public files and rejects canonical paths into private folders', async t => {
 const root = await mkdtemp(join(tmpdir(), 'racingclub-static-'));
 for (const dir of ['src', 'public', 'design', 'private']) await mkdir(join(root, dir));
 await writeFile(join(root, 'index.html'), '<p>Preview</p>');
 await writeFile(join(root, 'src', 'sample.js'), 'export const sample = true;');
 await writeFile(join(root, 'public', 'sample.txt'), 'public fixture');
 await writeFile(join(root, 'private', 'notes.txt'), 'private fixture');
 await writeFile(join(root, 'index.html.backup'), 'private backup fixture');
 await symlink(join(root, 'private', 'notes.txt'), join(root, 'public', 'alias.txt'));
 const server = createStaticServer(root);
 await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
 t.after(async () => {
  await new Promise(resolve => server.close(resolve));
  await rm(root, { recursive: true, force: true });
 });
 const url = `http://127.0.0.1:${server.address().port}`;
 for (const path of ['/', '/index.html', '/src/sample.js', '/public/sample.txt']) {
  const res = await fetch(url + path);
  assert.equal(res.status, 200, path);
  assert.equal(res.headers.get('x-content-type-options'), 'nosniff');
  await res.text();
 }
 for (const path of ['/private/notes.txt', '/src/..%2fprivate%2fnotes.txt', '/src/%2e%2e%2fprivate%2fnotes.txt', '/public/alias.txt', '/index.html.backup', '/%E0%A4%A']) {
  const res = await fetch(url + path);
  assert.equal(res.status, 404, path);
  assert.equal(await res.text(), 'Not found');
 }
});
