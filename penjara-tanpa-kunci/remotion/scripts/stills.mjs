// Render many single frames with one bundle: node scripts/stills.mjs <outDir> <frame> [frame...]
import {bundle} from '@remotion/bundler';
import {renderStill, selectComposition} from '@remotion/renderer';
import path from 'node:path';

const args = process.argv.slice(2);
const compId = args[0].startsWith('--comp=') ? args.shift().slice(7) : 'PenjaraV4';
const [outDir, ...frames] = args;
const serveUrl = await bundle({entryPoint: path.resolve('src/index.ts')});
const browserExecutable = '/opt/pw-browsers/chromium_headless_shell';
const composition = await selectComposition({serveUrl, id: compId, browserExecutable});
for (const f of frames) {
	await renderStill({
		composition,
		serveUrl,
		frame: Number(f),
		output: path.join(outDir, `f_${String(f).padStart(5, '0')}.png`),
		browserExecutable,
	});
	console.log('rendered', f);
}
