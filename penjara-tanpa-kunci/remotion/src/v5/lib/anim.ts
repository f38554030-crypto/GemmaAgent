// Small animation toolkit for v5 (pure functions of the frame number -> deterministic renders).
export const FPS = 30;
export const sec = (s: number) => Math.round(s * FPS);

export const clamp = (v: number, a = 0, b = 1) => Math.min(b, Math.max(a, v));
export const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
export const inv = (a: number, b: number, v: number) => clamp((v - a) / (b - a));

export const ease = {
	linear: (t: number) => t,
	inOut: (t: number) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2),
	out: (t: number) => 1 - Math.pow(1 - t, 3),
	in: (t: number) => t * t * t,
	inOutSine: (t: number) => -(Math.cos(Math.PI * t) - 1) / 2,
	outBack: (t: number) => {
		const c1 = 1.70158, c3 = c1 + 1;
		return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
	},
	outElastic: (t: number) =>
		t === 0 ? 0 : t === 1 ? 1 : Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * ((2 * Math.PI) / 3)) + 1,
};

/** value at frame f between frames a..b (eased), clamped outside */
export const tween = (f: number, a: number, b: number, from: number, to: number, e: (t: number) => number = ease.inOut) =>
	lerp(from, to, e(inv(a, b, f)));

/** Keyframes: [[frame, value], ...] with optional easing per segment. */
export function keys(f: number, kf: [number, number][], e: (t: number) => number = ease.inOut): number {
	if (f <= kf[0][0]) return kf[0][1];
	for (let i = 0; i < kf.length - 1; i++) {
		const [a, va] = kf[i];
		const [b, vb] = kf[i + 1];
		if (f <= b) return lerp(va, vb, e(inv(a, b, f)));
	}
	return kf[kf.length - 1][1];
}

/** Deterministic pseudo-random in [0,1) */
export const rand = (seed: number) => {
	const x = Math.sin(seed * 12.9898 + 78.233) * 43758.5453;
	return x - Math.floor(x);
};

/** smooth 1D value noise, period ~1 at speed 1 */
export const noise = (t: number, seed = 0) => {
	const i = Math.floor(t), fr = t - i;
	const a = rand(i + seed * 101), b = rand(i + 1 + seed * 101);
	const u = fr * fr * (3 - 2 * fr);
	return a + (b - a) * u - 0.5;
};

/** 0..1 visibility envelope: fade in over `fi` frames at a, fade out over `fo` frames before b */
export const env = (f: number, a: number, b: number, fi = 8, fo = 8) =>
	Math.min(inv(a, a + fi, f), 1 - inv(b - fo, b, f));

/** blink: returns eye openness (1 open .. 0 closed) with blinks at pseudo-random times */
export const blink = (f: number, seed = 1, every = 95) => {
	const k = Math.floor(f / every);
	const at = k * every + Math.floor(rand(k + seed * 17) * (every - 12)) + 6;
	const d = Math.abs(f - at);
	return d > 3 ? 1 : [0.05, 0.25, 0.6, 0.9][d];
};

export const mixColor = (c1: string, c2: string, t: number) => {
	const p = (c: string) => [1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16));
	const a = p(c1), b = p(c2);
	const c = a.map((v, i) => Math.round(v + (b[i] - v) * clamp(t)));
	return `rgb(${c[0]},${c[1]},${c[2]})`;
};
