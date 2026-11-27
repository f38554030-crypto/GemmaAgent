import React, {useId} from 'react';
import {FONT} from '../../fonts';
import {C} from '../theme';
import {clamp, lerp, mixColor, rand} from '../lib/anim';

// Bedroom (side view). Room space = 1080 x 1920, floor line at y = FLOOR.
export const FLOOR = 1262;
export const BED = {x0: 170, x1: 640, top: 1172, mattressTop: 1178};
export const DOOR = {x0: 700, x1: 880, y0: 700, y1: FLOOR};
export const CAL = {x: 440, y: 520, w: 124, h: 150};
export const VENT = {x: 170, y: 420, w: 180, h: 90};
export const TABLE = {x0: 56, x1: 164, top: 1160};
export const GUITAR = {x: 112, y: 1012, rot: -6}; // hung on the left wall above the bedside table (never behind his head)

/** true if a 60x60 note centred at (x, y) would overlap the hanging guitar (tested in the guitar's rotated frame) */
const guitarHit = (x: number, y: number) => {
	const r = (-GUITAR.rot * Math.PI) / 180;
	const dx = x - GUITAR.x, dy = y - GUITAR.y;
	const lx = dx * Math.cos(r) - dy * Math.sin(r), ly = dx * Math.sin(r) + dy * Math.cos(r);
	return Math.abs(lx) < 60 + 42 && ly > -430 - 42 && ly < 10 + 42;
};
// sticky notes: first one right next to the pillow (within arm's reach), then spreading over the wall
const WORDS = ['besok', 'nanti', 'lusa', 'besok!', 'senin', 'minggu', 'besok', 'nanti aja', 'lusa', 'besok'];
const avoid = (x: number, y: number) =>
	(x > CAL.x - 70 && x < CAL.x + CAL.w + 30 && y > CAL.y - 60 && y < CAL.y + CAL.h + 40) ||
	(x > DOOR.x0 - 60 && x < DOOR.x1 + 60 && y > DOOR.y0 - 70) ||
	(x > VENT.x - 70 && x < VENT.x + VENT.w + 30 && y > VENT.y - 60 && y < VENT.y + VENT.h + 40) ||
	guitarHit(x, y) ||
	y > BED.top - 100 || y < 150 || x < 30 || x > 1040;
export const NOTES: {x: number; y: number; r: number; w: string}[] = (() => {
	const first = {x: 262, y: 1036, r: -4, w: 'BESOK'};
	const out = [first];
	for (let k = 1; k < 40000 && out.length < 170; k++) {
		const x = 30 + rand(k * 3.17) * 1010;
		const y = 150 + rand(k * 7.71) * 880;
		if (avoid(x, y)) continue;
		if (out.some((o) => Math.abs(o.x - x) < 60 && Math.abs(o.y - y) < 60)) continue;
		out.push({x, y, r: (rand(k * 1.3) - 0.5) * 18, w: WORDS[out.length % WORDS.length]});
	}
	// spread outward from the first note (next to the pillow)
	const rest = out.slice(1).sort((a, b) => Math.hypot(a.x - first.x, (a.y - first.y) * 1.3) - Math.hypot(b.x - first.x, (b.y - first.y) * 1.3));
	return [first, ...rest];
})();

export const StickyNote: React.FC<{x: number; y: number; r?: number; w?: string; s?: number; op?: number; big?: boolean}> = ({x, y, r = 0, w = 'besok', s = 1, op = 1, big = false}) => (
	<g transform={`translate(${x} ${y}) rotate(${r}) scale(${s})`} opacity={op}>
		<rect x={-30} y={-30} width={60} height={60} rx={3} fill={C.note} />
		<path d="M -30 -30 L 30 -30 L 30 -22 L -30 -22 Z" fill={C.noteDark} opacity={0.6} />
		<text y={big ? 9 : 6} textAnchor="middle" fontFamily={FONT} fontWeight={900} fontSize={big ? 17 : 13.5} fill={C.noteText}>{w}</text>
	</g>
);

export const Calendar: React.FC<{x?: number; y?: number; value: number; prev?: number; tear?: number; scale?: number}> = ({x = CAL.x, y = CAL.y, value, prev, tear = 0, scale = 1}) => {
	const w = CAL.w, h = CAL.h;
	const fs = String(value).length >= 3 ? 46 : 60;
	const page = (v: number) => (
		<>
			<rect x={0} y={34} width={w} height={h - 34} rx={10} fill={C.calPage} />
			<text x={w / 2} y={34 + (h - 34) / 2 + (String(v).length >= 3 ? 16 : 21)} textAnchor="middle" fontFamily={FONT} fontWeight={900} fontSize={String(v).length >= 3 ? 46 : 60} fill={C.calDigit}>{v}</text>
		</>
	);
	return (
		<g transform={`translate(${x} ${y}) scale(${scale})`}>
			<rect x={4} y={6} width={w} height={h} rx={12} fill="rgba(0,0,0,0.25)" />
			{page(value)}
			{tear > 0 && prev !== undefined ? (
				<g transform={`translate(${tear * 60} ${tear * tear * 520}) rotate(${tear * 75} 0 34)`} opacity={1 - clamp((tear - 0.7) / 0.3)}>
					{page(prev)}
				</g>
			) : null}
			<rect x={0} y={0} width={w} height={40} rx={10} fill={C.calRed} />
			<rect x={0} y={24} width={w} height={16} fill={C.calRed} />
			<text x={w / 2} y={28} textAnchor="middle" fontFamily={FONT} fontWeight={900} fontSize={22} fill="#fff" letterSpacing="2">HARI</text>
			<circle cx={28} cy={6} r={5} fill="#8E2F36" />
			<circle cx={w - 28} cy={6} r={5} fill="#8E2F36" />
			<span style={{display: 'none'}}>{fs}</span>
		</g>
	);
};

export const AlarmClock: React.FC<{x: number; y: number; s?: number; ring?: number; f?: number; hour?: number}> = ({x, y, s = 1, ring = 0, f = 0, hour = 7}) => {
	const shake = ring ? Math.sin(f * 2.1) * 6 * ring : 0;
	const hop = ring ? Math.abs(Math.sin(f * 1.05)) * 5 * ring : 0;
	const ha = (hour % 12) * 30;
	return (
		<g transform={`translate(${x} ${y - hop}) rotate(${shake}) scale(${s})`}>
			<circle cx={-22} cy={-44} r={13} fill={C.alarm} />
			<circle cx={22} cy={-44} r={13} fill={C.alarm} />
			<rect x={-4} y={-52} width={8} height={8} fill="#8E2F36" />
			<circle cx={0} cy={-6} r={40} fill={C.alarm} />
			<circle cx={0} cy={-6} r={32} fill="#FBF6EC" />
			<line x1={0} y1={-6} x2={Math.sin((ha * Math.PI) / 180) * 16} y2={-6 - Math.cos((ha * Math.PI) / 180) * 16} stroke="#2A2F40" strokeWidth={5} strokeLinecap="round" />
			<line x1={0} y1={-6} x2={0} y2={-30} stroke="#2A2F40" strokeWidth={3.5} strokeLinecap="round" />
			<circle cx={0} cy={-6} r={4} fill="#2A2F40" />
			<line x1={-26} y1={30} x2={-34} y2={42} stroke="#8E2F36" strokeWidth={6} strokeLinecap="round" />
			<line x1={26} y1={30} x2={34} y2={42} stroke="#8E2F36" strokeWidth={6} strokeLinecap="round" />
			{ring > 0
				? [0, 1, 2].map((i) => (
					<path key={i} d={`M ${-70 - i * 16} ${-40 - i * 4} q -12 30 0 60 M ${70 + i * 16} ${-40 - i * 4} q 12 30 0 60`} stroke="#F7D36B" strokeWidth={5} fill="none" strokeLinecap="round" opacity={ring * (0.9 - i * 0.25) * (0.6 + 0.4 * Math.sin(f * 0.9 + i))} />
				))
				: null}
		</g>
	);
};

export type RoomProps = {
	f: number;
	day?: number; // 0 night .. 1 day
	notes?: number; // how many notes are on the wall (0..NOTES.length), fractional = last one popping in
	calValue?: number;
	calPrev?: number;
	calTear?: number;
	doorGlow?: number;
	bars?: number; // 0..1 bars sliding in
	barsDissolve?: number; // 0..1 bars falling away (reasons were never locks)
	doorOpen?: number;
	spot?: {x: number; y: number; r: number; a: number} | null;
	alarm?: {ring: number; hour: number} | null;
	phoneOnTable?: boolean;
	beam?: number; // light shaft intensity
	children?: React.ReactNode; // characters (room coordinates)
	front?: React.ReactNode; // drawn above characters (e.g. blanket front part)
};

export const Room: React.FC<RoomProps> = ({
	f, day = 0, notes = 1, calValue = 1, calPrev, calTear = 0, doorGlow = 0, bars = 0, barsDissolve = 0, doorOpen = 0,
	spot = null, alarm = null, phoneOnTable = false, beam = 1, children, front,
}) => {
	const id = useId().replace(/:/g, '');
	const wallT = mixColor(C.wallTop, '#33476F', day);
	const wallB = mixColor(C.wallBottom, '#26365A', day);
	const beamCol = day > 0.5 ? '#FFE7A8' : C.moon;
	const nCount = Math.floor(notes);
	const nFrac = notes - nCount;
	return (
		<g>
			<defs>
				<linearGradient id={`wall${id}`} x1="0" y1="0" x2="0" y2="1">
					<stop offset="0%" stopColor={wallT} />
					<stop offset="100%" stopColor={wallB} />
				</linearGradient>
				<linearGradient id={`floor${id}`} x1="0" y1="0" x2="0" y2="1">
					<stop offset="0%" stopColor={mixColor('#141A27', '#1E2A42', day)} />
					<stop offset="100%" stopColor={C.floor} />
				</linearGradient>
				<linearGradient id={`beam${id}`} x1="0" y1="0" x2="0" y2="1">
					<stop offset="0%" stopColor={beamCol} stopOpacity={0.28 * beam} />
					<stop offset="100%" stopColor={beamCol} stopOpacity={0.02} />
				</linearGradient>
				<radialGradient id={`dg${id}`} cx="50%" cy="100%" r="70%">
					<stop offset="0%" stopColor="#FFE2A0" stopOpacity={0.9} />
					<stop offset="100%" stopColor="#FFE2A0" stopOpacity={0} />
				</radialGradient>
				<radialGradient id={`spot${id}`} cx="50%" cy="50%" r="50%">
					<stop offset="0%" stopColor="#000" stopOpacity={0} />
					<stop offset="70%" stopColor="#000" stopOpacity={0} />
					<stop offset="100%" stopColor="#000" stopOpacity={1} />
				</radialGradient>
				<linearGradient id={`outside${id}`} x1="0" y1="0" x2="0" y2="1">
					<stop offset="0%" stopColor="#CFE6FA" />
					<stop offset="100%" stopColor="#FFF1C7" />
				</linearGradient>
			</defs>
			{/* wall + subtle panel lines */}
			<rect x={0} y={0} width={1080} height={FLOOR} fill={`url(#wall${id})`} />
			{[180, 360, 540, 720, 900].map((x) => (
				<rect key={x} x={x} y={0} width={2} height={FLOOR} fill="#fff" opacity={0.018} />
			))}
			<rect x={0} y={FLOOR - 22} width={1080} height={22} fill={C.trim} />
			{/* floor with perspective boards */}
			<rect x={0} y={FLOOR} width={1080} height={1920 - FLOOR} fill={`url(#floor${id})`} />
			{Array.from({length: 9}).map((_, i) => {
				const xt = 60 + i * 120;
				const xb = 540 + (xt - 540) * 2.4;
				return <line key={i} x1={xt} y1={FLOOR} x2={xb} y2={1920} stroke={C.floorLine} strokeWidth={3} />;
			})}
			{/* vent + light shaft */}
			<g>
				<rect x={VENT.x} y={VENT.y} width={VENT.w} height={VENT.h} rx={8} fill={C.vent} />
				{Array.from({length: 5}).map((_, i) => (
					<rect key={i} x={VENT.x + 14} y={VENT.y + 14 + i * 16} width={VENT.w - 28} height={8} rx={3} fill={C.ventSlat} />
				))}
				<polygon
					points={`${VENT.x + 10},${VENT.y + VENT.h} ${VENT.x + VENT.w - 10},${VENT.y + VENT.h} ${VENT.x + VENT.w + 260 + day * 120},${FLOOR + 120} ${VENT.x + 120 + day * 120},${FLOOR + 120}`}
					fill={`url(#beam${id})`}
				/>
				{/* dust in the beam */}
				{Array.from({length: 18}).map((_, i) => {
					const t = (f * 0.4 + rand(i) * 600) % 600;
					const px = VENT.x + 40 + rand(i * 3) * 220 + t * 0.35 + Math.sin((f + i * 40) / 30) * 8;
					const py = VENT.y + VENT.h + 30 + ((t * 1.1 + rand(i * 5) * 700) % 780);
					return <circle key={i} cx={px} cy={py} r={1.6 + rand(i * 9) * 1.8} fill={beamCol} opacity={0.35 * beam * (0.5 + 0.5 * Math.sin((f + i * 23) / 17))} />;
				})}
			</g>
			{/* calendar */}
			<Calendar value={calValue} prev={calPrev} tear={calTear} />
			{/* door */}
			<g>
				<rect x={DOOR.x0 - 16} y={DOOR.y0 - 16} width={DOOR.x1 - DOOR.x0 + 32} height={DOOR.y1 - DOOR.y0 + 16} rx={6} fill={C.doorFrame} />
				{/* outside light seen when the door opens */}
				<rect x={DOOR.x0} y={DOOR.y0} width={DOOR.x1 - DOOR.x0} height={DOOR.y1 - DOOR.y0} fill={`url(#outside${id})`} opacity={doorOpen > 0 ? 1 : 0} />
				<g transform={`translate(${DOOR.x1} 0) scale(${1 - doorOpen * 0.86} 1) translate(${-DOOR.x1} 0)`}>
					<rect x={DOOR.x0} y={DOOR.y0} width={DOOR.x1 - DOOR.x0} height={DOOR.y1 - DOOR.y0} fill={mixColor(C.door, '#1C2440', doorOpen)} />
					<rect x={DOOR.x0 + 24} y={DOOR.y0 + 34} width={DOOR.x1 - DOOR.x0 - 48} height={230} rx={8} fill={C.doorPanel} />
					<rect x={DOOR.x0 + 24} y={DOOR.y0 + 300} width={DOOR.x1 - DOOR.x0 - 48} height={260} rx={8} fill={C.doorPanel} />
					<circle cx={DOOR.x0 + 30} cy={DOOR.y0 + 330} r={11} fill={C.handle} />
					<rect x={DOOR.x0 + 26} y={DOOR.y0 + 326} width={30} height={8} rx={4} fill={C.handle} />
				</g>
				{doorGlow > 0 ? (
					<>
						<rect x={DOOR.x0} y={DOOR.y1 - 6} width={DOOR.x1 - DOOR.x0} height={6} fill="#FFE2A0" opacity={doorGlow} />
						<ellipse cx={(DOOR.x0 + DOOR.x1) / 2} cy={DOOR.y1 + 4} rx={170} ry={46} fill={`url(#dg${id})`} opacity={doorGlow * 0.8} />
						<rect x={DOOR.x0 - 3} y={DOOR.y0} width={3} height={DOOR.y1 - DOOR.y0} fill="#FFE2A0" opacity={doorGlow * 0.6} />
					</>
				) : null}
				{doorOpen > 0 ? (
					<polygon points={`${DOOR.x0},${DOOR.y0} ${DOOR.x0 + (DOOR.x1 - DOOR.x0) * (1 - doorOpen * 0.86)},${DOOR.y0} ${DOOR.x0 - 360 * doorOpen},${1920} ${DOOR.x0 - 700 * doorOpen},${1920}`} fill="#FFF1C7" opacity={0.18 * doorOpen} />
				) : null}
				{/* bars (made of the reasons) */}
				{bars > 0
					? Array.from({length: 6}).map((_, i) => {
						const x = DOOR.x0 - 8 + i * ((DOOR.x1 - DOOR.x0 + 16) / 5);
						const delay = i * 0.08;
						const p = clamp((bars - delay) / (1 - 0.4));
						const fall = clamp((barsDissolve - i * 0.06) / 0.5);
						const y0 = DOOR.y0 - 20 - (1 - p) * 700 + fall * fall * 900;
						return (
							<g key={i} opacity={p * (1 - fall)} transform={`rotate(${fall * (i % 2 ? 24 : -20)} ${x} ${y0 + 300})`}>
								<rect x={x - 9} y={y0} width={18} height={DOOR.y1 - DOOR.y0 + 30} rx={6} fill={C.bars} />
								<rect x={x - 9} y={y0} width={5} height={DOOR.y1 - DOOR.y0 + 30} rx={3} fill="#2A3450" opacity={0.6} />
							</g>
						);
					})
					: null}
				{bars > 0.7
					? [DOOR.y0 + 140, DOOR.y0 + 430].map((yy, j) => (
						<rect key={j} x={DOOR.x0 - 22} y={yy} width={DOOR.x1 - DOOR.x0 + 44} height={18} rx={6} fill={C.bars} opacity={clamp((bars - 0.7) / 0.3) * (1 - clamp(barsDissolve / 0.6))} />
					))
					: null}
			</g>
			{/* notes (behind characters) */}
			{NOTES.slice(0, Math.min(NOTES.length, nCount + (nFrac > 0 ? 1 : 0))).map((n, i) => {
				const pop = i === nCount ? nFrac : 1;
				return <StickyNote key={i} x={n.x} y={n.y} r={n.r} w={n.w} s={0.3 + 0.7 * Math.min(1, pop * 1.3)} op={Math.min(1, pop * 2)} big={i === 0} />;
			})}
			{/* bedside table + alarm */}
			<rect x={TABLE.x0} y={TABLE.top} width={TABLE.x1 - TABLE.x0} height={FLOOR - TABLE.top} rx={6} fill={C.table} />
			<rect x={TABLE.x0 + 10} y={TABLE.top + 40} width={TABLE.x1 - TABLE.x0 - 20} height={8} rx={3} fill="#22283B" />
			{alarm ? <AlarmClock x={TABLE.x0 + 34} y={TABLE.top - 34} s={0.62} ring={alarm.ring} f={f} hour={alarm.hour} /> : <AlarmClock x={TABLE.x0 + 34} y={TABLE.top - 34} s={0.62} f={f} hour={day > 0.5 ? 10 : 3} />}
			{phoneOnTable ? <rect x={TABLE.x0 + 60} y={TABLE.top - 9} width={40} height={9} rx={3} fill="#05080F" /> : null}
			{/* forgotten plans: guitar (dusty), running shoes, books, wilting plant */}
			<g transform={`translate(${GUITAR.x} ${GUITAR.y}) rotate(${GUITAR.rot})`}>
				<circle cx={0} cy={-418} r={6} fill="#8A93A8" />
				<rect x={-7} y={-370} width={14} height={240} rx={4} fill="#6B4A2E" />
				<rect x={-13} y={-400} width={26} height={44} rx={7} fill="#4E3622" />
				<ellipse cx={0} cy={-115} rx={44} ry={50} fill="#A8693C" />
				<ellipse cx={0} cy={-52} rx={54} ry={58} fill="#A8693C" />
				<circle cx={0} cy={-100} r={14} fill="#3A2616" />
				<rect x={-2} y={-380} width={4} height={330} fill="#E9D9B8" opacity={0.5} />
				<path d="M -30 -150 q 30 -10 64 4" stroke="#C9CED8" strokeWidth={1.5} fill="none" opacity={0.35} />
			</g>
			<g transform={`translate(560 ${FLOOR - 2})`}>
				{[0, 46].map((dx, i) => (
					<g key={i} transform={`translate(${dx} 0)`}>
						<path d="M -22 0 L -22 -22 Q -18 -34 -2 -34 L 6 -24 Q 24 -22 30 -8 L 30 0 Z" fill="#4D7FD1" />
						<rect x={-24} y={-6} width={56} height={8} rx={3} fill="#E8ECF4" />
					</g>
				))}
			</g>
			<g transform={`translate(${TABLE.x0 + 14} ${TABLE.top + 52})`}>
				{['#B8564B', '#4A7BC0', '#D9A441'].map((c, i) => (
					<rect key={i} x={i * 2} y={-i * 12} width={78} height={11} rx={2} fill={c} />
				))}
			</g>
			<g transform={`translate(${TABLE.x1 - 24} ${TABLE.top})`}>
				<path d="M -14 0 L 14 0 L 10 -26 L -10 -26 Z" fill="#8E5B3C" />
				<path d="M 0 -26 Q -6 -52 -26 -44" stroke="#6E8F55" strokeWidth={4} fill="none" />
				<path d="M 0 -26 Q 8 -50 26 -40" stroke="#6E8F55" strokeWidth={4} fill="none" />
				<ellipse cx={-27} cy={-42} rx={8} ry={4} fill="#7E9A5E" transform="rotate(30 -27 -42)" />
				<ellipse cx={27} cy={-38} rx={8} ry={4} fill="#7E9A5E" transform="rotate(-35 27 -38)" />
			</g>
			{/* bed (back parts) */}
			<rect x={BED.x0 - 14} y={1060} width={30} height={FLOOR - 1060} rx={10} fill={C.bedFrame} />
			<rect x={BED.x1 - 14} y={1150} width={24} height={FLOOR - 1150} rx={8} fill={C.bedFrame} />
			<rect x={BED.x0} y={BED.top} width={BED.x1 - BED.x0} height={46} rx={14} fill={C.mattress} />
			<rect x={BED.x0} y={BED.top + 46} width={BED.x1 - BED.x0} height={FLOOR - BED.top - 46} fill={C.bedFrame} />
			<ellipse cx={BED.x0 + 70} cy={BED.top - 6} rx={70} ry={24} fill={C.pillow} />
			{children}
			{front}
			{spot ? (
				<g opacity={spot.a}>
					<rect x={-200} y={-200} width={1480} height={2320} fill="rgba(4,6,12,0.82)" mask={`url(#m${id})`} />
					<mask id={`m${id}`}>
						<rect x={-200} y={-200} width={1480} height={2320} fill="#fff" />
						<circle cx={spot.x} cy={spot.y} r={spot.r} fill="#000" />
						<circle cx={spot.x} cy={spot.y} r={spot.r * 1.25} fill="#000" opacity={0.5} />
					</mask>
				</g>
			) : null}
		</g>
	);
};

/** blanket drawn in front of a lying character (pass as `front` with the character as child) */
export const Blanket: React.FC<{x0?: number; x1?: number; lift?: number}> = ({x0 = 380, x1 = BED.x1 + 6, lift = 0}) => (
	<g>
		<path d={`M ${x0} ${BED.top + 4 - lift} Q ${(x0 + x1) / 2} ${BED.top - 34 - lift} ${x1} ${BED.top + 2} L ${x1} ${BED.top + 50} L ${x0 - 6} ${BED.top + 50} Z`} fill={C.blanket} />
		<path d={`M ${x0} ${BED.top + 26 - lift * 0.5} Q ${(x0 + x1) / 2} ${BED.top + 8 - lift} ${x1} ${BED.top + 22}`} stroke={C.blanketDark} strokeWidth={6} fill="none" opacity={0.7} />
	</g>
);

export const lerpN = lerp;
