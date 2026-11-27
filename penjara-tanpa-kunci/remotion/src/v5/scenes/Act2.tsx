import React from 'react';
import {FONT} from '../../fonts';
import {FaceClose} from '../components/FaceClose';
import {BED, DOOR, FLOOR, GUITAR, Room, TABLE} from '../components/Room';
import {P0, Pose, STAND_HIP, Stickman, idle, mix, reachArm, sit, walk} from '../components/Stickman';
import {blink, clamp, ease, env, keys, lerp, tween} from '../lib/anim';
import {notesAt, promises} from '../lib/story';
import {C} from '../theme';
import {Cam, RS, Svg} from './Act1';

const SIT = {x: 560, y: 1186};
const STAND_Y = FLOOR - STAND_HIP * RS;

/** slumped on the edge of the bed, hands resting on the thighs (IK) */
const tiredSit = (f: number, extra: Partial<Pose> = {}): Pose => {
	const p = sit(f, {torso: 20 + Math.sin(f / 26) * 1.5, head: 16, eyes: blink(f, 5) * 0.55, lid: 0.55, look: 0.2, lookY: 0.6, brow: 0.6, ...extra});
	return {...p, armN: extra.armN ?? reachArm(p, 50, -15), armF: extra.armF ?? reachArm(p, 40, -15)};
};

// ---------------- icons ----------------
export const Icon: React.FC<{k: string; s?: number; grey?: number}> = ({k, s = 1, grey = 0}) => {
	const g = (c: string) => (grey > 0 ? `rgba(160,170,190,${0.35 + 0.65 * (1 - grey)})` : c);
	const op = 1 - grey * 0.45;
	return (
		<g transform={`scale(${s})`} opacity={op}>
			{k === 'guitar' && (
				<g transform="rotate(-20)">
					<rect x={-5} y={-60} width={10} height={50} rx={3} fill={g('#6B4A2E')} />
					<ellipse cx={0} cy={2} rx={20} ry={22} fill={g('#C07A45')} />
					<ellipse cx={0} cy={26} rx={25} ry={24} fill={g('#C07A45')} />
					<circle cx={0} cy={8} r={6} fill={g('#3A2616')} />
				</g>
			)}
			{k === 'shoe' && (
				<g>
					<path d="M -36 18 L -36 -8 Q -30 -26 -8 -26 L 4 -12 Q 30 -10 40 6 L 40 18 Z" fill={g('#4D7FD1')} />
					<rect x={-38} y={12} width={80} height={10} rx={4} fill={g('#EEF1F6')} />
				</g>
			)}
			{k === 'book' && (
				<g>
					<path d="M -40 -26 Q -20 -34 0 -24 L 0 30 Q -20 20 -40 28 Z" fill={g('#5A8DEE')} />
					<path d="M 40 -26 Q 20 -34 0 -24 L 0 30 Q 20 20 40 28 Z" fill={g('#7FA7F2')} />
				</g>
			)}
			{k === 'plant' && (
				<g>
					<path d="M -18 30 L 18 30 L 13 2 L -13 2 Z" fill={g('#B66E46')} />
					<path d="M 0 2 Q -4 -26 -24 -30" stroke={g('#4FA35E')} strokeWidth={6} fill="none" />
					<path d="M 0 2 Q 6 -30 26 -36" stroke={g('#4FA35E')} strokeWidth={6} fill="none" />
					<ellipse cx={-24} cy={-30} rx={11} ry={6} fill={g('#5FBF6E')} />
					<ellipse cx={26} cy={-36} rx={11} ry={6} fill={g('#5FBF6E')} />
				</g>
			)}
			{k === 'tired' && (
				<g>
					<rect x={-34} y={-18} width={62} height={36} rx={7} fill="none" stroke={g('#3A4256')} strokeWidth={6} />
					<rect x={30} y={-8} width={8} height={16} rx={2} fill={g('#3A4256')} />
					<rect x={-27} y={-11} width={14} height={22} rx={3} fill={g('#E0525A')} />
				</g>
			)}
			{k === 'time' && (
				<g>
					<path d="M -22 -32 L 22 -32 L 22 -26 L 4 0 L 22 26 L 22 32 L -22 32 L -22 26 L -4 0 L -22 -26 Z" fill="none" stroke={g('#3A4256')} strokeWidth={6} strokeLinejoin="round" />
					<path d="M -12 24 L 12 24 L 0 10 Z" fill={g('#D9A441')} />
				</g>
			)}
			{k === 'ready' && (
				<g>
					{[-20, 0, 20].map((y) => (
						<g key={y}>
							<rect x={-30} y={y - 7} width={14} height={14} rx={3} fill="none" stroke={g('#3A4256')} strokeWidth={4} />
							<rect x={-8} y={y - 3} width={38} height={6} rx={3} fill={g('#3A4256')} />
						</g>
					))}
				</g>
			)}
		</g>
	);
};

export const ThoughtBubble: React.FC<{x: number; y: number; w: number; h: number; p: number; from: [number, number]; children?: React.ReactNode}> = ({x, y, w, h, p, from, children}) => {
	if (p <= 0) return null;
	const sc = ease.outBack(clamp(p));
	const puffs = 9;
	return (
		<g opacity={clamp(p * 3)}>
			{[0.25, 0.5, 0.72].map((t, i) => (
				<circle key={i} cx={lerp(from[0], x - w * 0.2, t)} cy={lerp(from[1], y + h * 0.5, t)} r={(6 + i * 7) * clamp(p * 2 - i * 0.2)} fill="#E8EDF7" />
			))}
			<g transform={`translate(${x} ${y}) scale(${sc})`}>
				{Array.from({length: puffs}).map((_, i) => {
					const a = (i / puffs) * Math.PI * 2;
					return <circle key={i} cx={Math.cos(a) * w * 0.42} cy={Math.sin(a) * h * 0.38} r={Math.min(w, h) * 0.26} fill="#E8EDF7" />;
				})}
				<ellipse rx={w * 0.46} ry={h * 0.42} fill="#E8EDF7" />
				{children}
			</g>
		</g>
	);
};

const ReasonCard: React.FC<{x: number; y: number; label: string; icon: string; p: number; check: number; morph?: number; tx?: number; rot?: number}> = ({x, y, label, icon, p, check, morph = 0, tx = 0, rot = 0}) => {
	if (p <= 0) return null;
	const s = ease.outBack(clamp(p));
	const W = Math.max(300, 132 + label.length * 18), H = 120; // fits the label
	const mx = lerp(x, tx, ease.inOut(morph));
	const sx = lerp(1, 0.06, ease.inOut(morph)), sy = lerp(1, 4.6, ease.inOut(morph));
	const col = mixHex('#F7F2E8', '#0B0F18', morph);
	return (
		<g transform={`translate(${mx} ${y}) rotate(${rot * (1 - morph)}) scale(${s * sx} ${s * sy})`} opacity={clamp(p * 2)}>
			<rect x={-W / 2 + 6} y={-H / 2 + 8} width={W} height={H} rx={18} fill="rgba(0,0,0,0.3)" />
			<rect x={-W / 2} y={-H / 2} width={W} height={H} rx={18} fill={col} />
			<g opacity={1 - morph * 3}>
				<g transform={`translate(${-W / 2 + 58} 0)`}><Icon k={icon} s={0.95} /></g>
				<text x={-W / 2 + 112} y={11} fontFamily={FONT} fontWeight={900} fontSize={30} fill="#2B3146">{label}</text>
				{check > 0 ? (
					<g transform={`translate(${W / 2 - 4} ${-H / 2 + 4}) scale(${ease.outBack(clamp(check))})`}>
						<circle r={30} fill="#3DBB6C" />
						<path d="M -13 1 L -3 11 L 15 -9" stroke="#fff" strokeWidth={7} fill="none" strokeLinecap="round" strokeLinejoin="round" />
					</g>
				) : null}
			</g>
		</g>
	);
};
const mixHex = (a: string, b: string, t: number) => {
	const p = (c: string) => [1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16));
	const x = p(a), y = p(b);
	return `rgb(${x.map((v, i) => Math.round(v + (y[i] - v) * clamp(t))).join(',')})`;
};

const Glow: React.FC<{x: number; y: number; r: number; a: number}> = ({x, y, r, a}) =>
	a > 0 ? (
		<g opacity={a}>
			<circle cx={x} cy={y} r={r} fill="#FFE7A0" opacity={0.25} />
			<circle cx={x} cy={y} r={r * 0.6} fill="#FFE7A0" opacity={0.25} />
		</g>
	) : null;

// ---------------- S08 face: "Sebenarnya, ia sadar." ----------------
export const FaceAware: React.FC<{f: number}> = ({f}) => {
	const open = tween(f, 64, 76, 0.45, 0.95); // "sadar" at ~33.2s
	return <FaceClose f={f} bg="nightNotes" glow="phone" push={tween(f, 0, 102, 0, 0.08)} expr={{eyes: blink(f, 7) * open, lid: lerp(0.6, 0.25, (open - 0.45) / 0.5), lookY: 0.3, look: tween(f, 70, 90, 0, -0.8), brow: 0.4}} />;
};

// ---------------- S09 tired, plans only in his head ----------------
const PLANS = [
	{k: 'guitar', at: 1158, obj: {x: GUITAR.x - 12, y: GUITAR.y - 150, r: 130}},
	{k: 'shoe', at: 1176, obj: {x: 595, y: FLOOR - 16, r: 70}},
	{k: 'book', at: 1194, obj: {x: TABLE.x0 + 52, y: TABLE.top + 40, r: 70}},
	{k: 'plant', at: 1212, obj: {x: TABLE.x1 - 24, y: TABLE.top - 30, r: 60}},
];
export const Tired: React.FC<{f: number; F: number}> = ({f, F}) => {
	// local 0..273 (34.4s..43.5s)
	const rub = env(f, 10, 70, 10, 12); // rubbing eyes ("berantakan")
	const yawn = env(f, 70, 120, 8, 10); // stretch/yawn ("kelelahan")
	let pose = tiredSit(F);
	pose = mix(pose, {...pose, armN: [150, 118], head: 6, eyes: 0.05, lid: 0}, rub);
	pose = mix(pose, {...pose, torso: 2, head: -14, armN: [168, 10], armF: [160, 14], eyes: 0.05, mouth: -0.3}, yawn);
	const bubbleP = tween(F, 1146, 1162, 0, 1) * (1 - tween(F, 1280, 1300, 0, 1));
	const greyAll = tween(F, 1232, 1262, 0, 1); // "kini hanya tersimpan dalam pikirannya"
	const z = tween(f, 0, 273, 1.32, 1.38, ease.inOutSine);
	return (
		<Svg>
			<Cam x={470} y={980} z={z}>
				<Room f={F} day={0} notes={notesAt(F)} calValue={promises(F)} alarm={{ring: 0, hour: 3}} beam={0.7}>
					{PLANS.map((p, i) => (
						<Glow key={i} x={p.obj.x} y={p.obj.y} r={p.obj.r} a={env(F, p.at, 1240, 6, 20) * 0.9} />
					))}
					<Stickman x={SIT.x} y={SIT.y} pose={pose} scale={RS} shadow={FLOOR} />
				</Room>
				<ThoughtBubble x={260} y={760} w={390} h={260} p={bubbleP} from={[SIT.x - 20, SIT.y - 300]}>
					{PLANS.map((p, i) => {
						const a = clamp((F - p.at) / 8);
						const pos = [[-90, -48], [80, -50], [-80, 52], [86, 50]][i];
						return a > 0 ? (
							<g key={i} transform={`translate(${pos[0]} ${pos[1]}) scale(${ease.outBack(a)})`}>
								<Icon k={p.k} s={1.05} grey={greyAll} />
							</g>
						) : null;
					})}
				</ThoughtBubble>
			</Cam>
		</Svg>
	);
};

// ---------------- S10-S11: "Ia ingin berubah, namun setiap kali kesempatan itu datang" ----------------
export const WantChange: React.FC<{f: number; F: number}> = ({f, F}) => {
	// local 0..146 (43.5s..48.37s)
	const up = tween(f, 4, 26, 0, 1, ease.inOut);
	const antic = env(f, 2, 18, 6, 8);
	const stepT = clamp((f - 60) / 50);
	const x = lerp(SIT.x, 616, ease.inOut(stepT));
	const sitting = mix(tiredSit(F), tiredSit(F, {eyes: 1, lid: 0.2, brow: -0.4, look: 0.6, lookY: 0}), tween(f, 0, 6, 0, 1)); // wakes up from S09's slump
	let pose = mix(sitting, idle(F, {eyes: 1, lid: 0.05, brow: -0.6, look: 0.8}), up);
	pose = mix(pose, {...pose, torso: 30, head: 10}, antic * 0.7);
	if (stepT > 0 && stepT < 1) pose = mix(pose, walk(f * 0.27, 0.8, {eyes: 1, brow: -0.5, look: 0.9}), Math.sin(Math.PI * stepT));
	const y = lerp(SIT.y, STAND_Y, up) + pose.bob * RS;
	const glow = tween(f, 40, 70, 0, 1);
	const z = tween(f, 30, 146, 1.38, 1.6, ease.inOut); // starts exactly where S09 ended (hard cut = continuous shot)
	const cx = tween(f, 30, 146, 470, 660, ease.inOut);
	return (
		<Svg>
			<Cam x={cx} y={tween(f, 30, 146, 980, 1010)} z={z}>
				<Room f={F} day={0} notes={notesAt(F)} calValue={promises(F)} alarm={{ring: 0, hour: 3}} doorGlow={glow * (0.8 + 0.2 * Math.sin(F / 6))} beam={0.7}>
					<Stickman x={x} y={y} pose={{...pose, bob: 0}} scale={RS} shadow={FLOOR} />
				</Room>
			</Cam>
		</Svg>
	);
};

// ---------------- S12 face: "selalu muncul alasan." ----------------
export const FaceExcuse: React.FC<{f: number}> = ({f}) => (
	<FaceClose f={f} bg="nightNotes" glow="moon" expr={{eyes: 1, lid: 0.2, look: tween(f, 4, 14, 0.8, -0.9), lookY: 0.2, brow: tween(f, 6, 16, -0.4, 0.8)}} push={tween(f, 0, 31, 0.04, 0.1)} />
);

// ---------------- S13-S14: reasons appear between him and the door, he retreats; "masuk akal" checks ----------------
const CARDS = [
	{label: 'Terlalu lelah', icon: 'tired', at: 1503, y: 860, rot: -4},
	{label: 'Masih ada waktu', icon: 'time', at: 1581, y: 1000, rot: 3},
	{label: 'Belum siap', icon: 'ready', at: 1635, y: 1140, rot: -2},
];
const CHECKS = [1770, 1782, 1794];
const CARD_X = 836;
const BAR_X = (i: number) => DOOR.x0 - 8 + i * ((DOOR.x1 - DOOR.x0 + 16) / 5);
export const Reasons: React.FC<{f: number; F: number}> = ({f, F}) => {
	// local 0..335 (49.4s..60.57s)
	// walk toward the door, retreat a step at each card, sit back down after the third
	const x = keys(F, [[1482, 600], [1500, 628], [1508, 628], [1520, 606], [1581, 606], [1593, 588], [1635, 588], [1648, 572], [1660, 566]], ease.inOut);
	const walking = (F > 1482 && F < 1500) || (F > 1508 && F < 1520) || (F > 1581 && F < 1593) || (F > 1635 && F < 1648);
	const back = F > 1505;
	const sitT = tween(F, 1660, 1690, 0, 1, ease.inOut);
	let pose: Pose = idle(F, {eyes: blink(F, 2), brow: back ? 0.7 : -0.3, look: 0.6, lid: back ? 0.3 : 0});
	if (walking) pose = mix(pose, walk((back ? -1 : 1) * F * 0.28, 0.6, {brow: 0.6, look: 0.8, eyes: 1}), 1);
	// flinch at each card
	for (const c of CARDS) {
		const fl = env(F, c.at, c.at + 14, 2, 10);
		pose = mix(pose, {...pose, torso: -8, head: -10, armN: [40, 70], eyes: 1, brow: 1}, fl);
	}
	pose = mix(pose, sit(F, {torso: 14, head: 10, eyes: blink(F, 4), lid: 0.4, brow: 0.5, look: 0.5}), sitT);
	const y = lerp(STAND_Y, SIT.y, sitT) + (1 - sitT) * pose.bob * RS;
	const nod = F > 1765 ? Math.sin((F - 1765) / 4) * 6 * env(F, 1765, 1815, 4, 10) : 0;
	pose = {...pose, head: pose.head + nod};
	const z = tween(f, 0, 335, 1.45, 1.36, ease.inOutSine);
	return (
		<Svg>
			<Cam x={700} y={1000} z={z}>
				<Room f={F} day={0} notes={notesAt(F)} calValue={promises(F)} doorGlow={0.85 * (1 - tween(F, 1503, 1640, 0, 0.7))} beam={0.6}>
					<Stickman x={lerp(x, SIT.x, sitT)} y={y} pose={{...pose, bob: 0}} scale={RS} shadow={FLOOR} />
				</Room>
				{CARDS.map((c, i) => (
					<ReasonCard key={i} x={CARD_X} y={c.y} label={c.label} icon={c.icon} rot={c.rot} p={(F - c.at) / 10} check={(F - CHECKS[i]) / 8} />
				))}
			</Cam>
		</Svg>
	);
};

// ---------------- S15 face: "bahkan terkadang memang benar." (nodding) ----------------
export const FaceNod: React.FC<{f: number}> = ({f}) => (
	<FaceClose f={f} bg="nightNotes" glow="moon" tilt={Math.max(0, Math.sin(f / 7)) * 10 * env(f, 4, 50, 6, 8)} expr={{eyes: tween(f, 0, 12, 0.9, 0.1), lid: 0.3, brow: 0.5, mouth: 0}} push={tween(f, 0, 58, 0.04, 0.1)} />
);

// ---------------- S16: reasons become the bars ----------------
export const CardsToBars: React.FC<{f: number; F: number}> = ({f, F}) => {
	// local 0..150 (62.5s..67.5s)
	const morph = (i: number) => tween(F, 1905 + i * 12, 1965 + i * 12, 0, 1, ease.inOut);
	const bars = tween(F, 1975, 2010, 0, 1, ease.out);
	const pose = sit(F, {torso: 22, head: 14, eyes: blink(F, 9) * 0.7, lid: 0.45, brow: 0.8, look: 0.8, lookY: 0.1});
	const z = tween(f, 0, 150, 1.32, 1.5, ease.inOut);
	return (
		<Svg>
			<Cam x={tween(f, 0, 150, 640, 680)} y={1000} z={z}>
				<Room f={F} day={0} notes={notesAt(F)} calValue={promises(F)} bars={bars} doorGlow={0.25 * (1 - bars)} beam={0.5}>
					<Stickman x={SIT.x} y={SIT.y} pose={pose} scale={RS} shadow={FLOOR} />
				</Room>
				{CARDS.map((c, i) => (
					<ReasonCard key={i} x={CARD_X} y={keys(F, [[1875, c.y], [1905 + i * 12, c.y], [1965 + i * 12, 980]])} tx={BAR_X(i * 2 + 1)} label={c.label} icon={c.icon} rot={c.rot}
						p={1 - tween(F, 1968 + i * 12, 1985 + i * 12, 0, 1)} check={1} morph={morph(i)} />
				))}
			</Cam>
		</Svg>
	);
};

// ---------------- S17-S18: bars lock; pull back until the room is a closed box in the dark ----------------
export const BarsAndBox: React.FC<{f: number; F: number}> = ({f, F}) => {
	// local 0..298 (67.5s..77.43s)
	const clang = F >= 2048 ? Math.exp(-(F - 2048) / 5) * Math.sin((F - 2048) * 1.6) * 8 : 0;
	const pull = tween(F, 2135, 2290, 0, 1, ease.inOut); // camera pull-back
	const push = tween(F, 2025, 2050, 0, 1, ease.inOut); // continues S16's framing, pushes in onto the clang (2048)
	const z = pull > 0 ? lerp(2.5, 0.36, pull) : lerp(1.5, 2.5, push);
	const cx = pull > 0 ? lerp(720, 560, pull) : lerp(680, 720, push), cy = lerp(1000, 760, pull);
	const close = tween(F, 2262, 2300, 0, 1, ease.inOut); // front face of the box closes ("batas kehidupannya")
	const D = 300; // box depth (oblique)
	const ox = D * 0.62, oy = -D * 0.62;
	const from = sit(F, {torso: 22, head: 14, eyes: blink(F, 9) * 0.7, lid: 0.45, brow: 0.8, look: 0.8, lookY: 0.1}); // = S16's pose
	const pose = mix(from, sit(F, {torso: 24, head: 18, eyes: blink(F, 11) * 0.5, lid: 0.6, brow: 0.7, lookY: 0.6}), tween(F, 2030, 2060, 0, 1));
	const BOXH = 1400;
	return (
		<Svg bg="#05070D">
			<defs>
				<clipPath id="boxclip"><rect x={0} y={0} width={1080} height={BOXH} /></clipPath>
				<radialGradient id="voidbg" cx="50%" cy="45%" r="70%">
					<stop offset="0%" stopColor="#141C2E" />
					<stop offset="100%" stopColor="#04060B" />
				</radialGradient>
			</defs>
			<rect width={1080} height={1920} fill="url(#voidbg)" opacity={pull} />
			<g transform={`translate(${clang} 0)`}>
				<Cam x={cx} y={cy} z={z}>
					{/* outer faces of the box (only visible once we pull back) */}
					<g opacity={clamp(pull * 3)}>
						<polygon points={`0,0 1080,0 ${1080 + ox},${oy} ${ox},${oy}`} fill={C.cubeTop} />
						<polygon points={`1080,0 ${1080 + ox},${oy} ${1080 + ox},${BOXH + oy} 1080,${BOXH}`} fill={C.cubeSide} />
					</g>
					<g clipPath="url(#boxclip)">
						<Room f={F} day={0} notes={notesAt(F)} calValue={promises(F)} bars={1} beam={0.5}>
							<Stickman x={SIT.x} y={SIT.y} pose={pose} scale={RS} shadow={FLOOR} />
						</Room>
					</g>
					{/* front face closes: same door + vent as seen from outside */}
					<g opacity={close}>
						<rect x={0} y={0} width={1080} height={BOXH} fill={C.cube} />
						<rect x={DOOR.x0} y={DOOR.y0} width={DOOR.x1 - DOOR.x0} height={DOOR.y1 - DOOR.y0} rx={6} fill="#141B2B" stroke="#2C3757" strokeWidth={6} />
						<circle cx={DOOR.x0 + 30} cy={DOOR.y0 + 330} r={11} fill="#5A5038" />
						{[0, 1, 2, 3].map((i) => <rect key={i} x={180} y={430 + i * 22} width={160} height={9} rx={4} fill="#2B3552" />)}
					</g>
					<rect x={0} y={0} width={1080} height={BOXH} fill="none" stroke="#2C3757" strokeWidth={8} opacity={clamp(pull * 3)} />
				</Cam>
			</g>
		</Svg>
	);
};

export {BED};
