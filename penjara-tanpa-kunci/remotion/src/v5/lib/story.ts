// Story state shared by scenes and global overlays (HUD counter, calendar day, notes on the wall).
import hudV3 from '../../data/hud.json';
import {clamp} from './anim';

const H = hudV3 as unknown as Record<string, [number, number | null]>;

/** "JANJI BESOK" count at global frame f (same progression as v3; jumps 367 -> 412 while he only imagines) */
export function promises(f: number): number {
	if (f < 248) return 1;
	if (f <= 929) {
		for (let k = f; k >= 248; k--) {
			const d = H[String(k)];
			if (d && d[1] !== null && d[1] !== undefined) return d[1] as number;
		}
		return 1;
	}
	if (f < 3075) return 367;
	if (f < 3130) return Math.round(367 + (412 - 367) * clamp((f - 3075) / 55) ** 1.4);
	return 412;
}

/** calendar: torn page by page (one tear every 6 frames during the montage) */
export function calendarAt(f: number): {value: number; prev?: number; tear?: number} {
	const M0 = 553, M1 = 925;
	if (f < M0) return {value: promises(f)};
	if (f >= M1) return {value: promises(f)};
	const c = M0 + Math.floor((f - M0) / 6) * 6;
	const prev = promises(Math.max(M0, c - 1));
	const value = promises(c + 5);
	if (value === prev) return {value};
	return {value, prev, tear: (f - c) / 6};
}

/** notes on the wall: 0 before he sticks the first one (frame 236), then grows with the count */
export function notesAt(f: number): number {
	if (f < 236) return 0;
	const n = promises(f);
	if (n <= 1) return 1;
	return 1 + Math.min(169, Math.pow((n - 1) / 366, 0.7) * 169);
}
