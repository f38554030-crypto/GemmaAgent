import React from 'react';
import {FONT} from '../../fonts';
import {C, SHIRTS} from '../theme';
import {clamp, ease, lerp, rand} from '../lib/anim';

// Day city (side view with a ground band that has depth, so objects can stand at different depths).
export const PLAZA_TOP = 1170; // back edge: trees / buildings stand here
export const WALK = 1300; // back of the sidewalk
export const CURB = 1410;
export const ROAD0 = 1424, ROAD1 = 1612;
export const CUBE = {x0: 120, x1: 560, top: 880, bottom: 1400, dx: 120, dy: -130};

export const Sky: React.FC<{camX: number; F: number; dim?: number}> = ({camX, F, dim = 0}) => (
	<g>
		<defs>
			<linearGradient id="skyG" x1="0" y1="0" x2="0" y2="1">
				<stop offset="0%" stopColor={C.skyTop} />
				<stop offset="100%" stopColor={C.skyBottom} />
			</linearGradient>
			<radialGradient id="sunG">
				<stop offset="0%" stopColor="#FFFBEA" />
				<stop offset="35%" stopColor={C.sun} stopOpacity={0.9} />
				<stop offset="100%" stopColor={C.sun} stopOpacity={0} />
			</radialGradient>
		</defs>
		<rect x={camX - 2000} y={-400} width={4000} height={PLAZA_TOP + 400} fill="url(#skyG)" />
		<circle cx={camX * 0.92 + 300} cy={300} r={260} fill="url(#sunG)" />
		{Array.from({length: 7}).map((_, i) => {
			const x = ((i * 560 + F * (0.6 + rand(i) * 0.5)) % 4200) - 600 + camX * 0.8;
			const y = 160 + rand(i * 3) * 380;
			const s = 0.7 + rand(i * 5) * 0.8;
			return (
				<g key={i} transform={`translate(${x} ${y}) scale(${s})`} opacity={0.85 - dim * 0.5}>
					<ellipse cx={0} cy={0} rx={110} ry={34} fill="#fff" opacity={0.85} />
					<ellipse cx={-40} cy={-18} rx={60} ry={34} fill="#fff" opacity={0.85} />
					<ellipse cx={40} cy={-24} rx={52} ry={30} fill="#fff" opacity={0.85} />
				</g>
			);
		})}
	</g>
);

const building = (x: number, w: number, h: number, base: number, fill: string, win: string, seed: number) => {
	const cols = Math.max(2, Math.floor(w / 46));
	const rows = Math.floor(h / 70);
	return (
		<g key={`${x}-${seed}`}>
			<rect x={x} y={base - h} width={w} height={h} fill={fill} />
			<rect x={x} y={base - h} width={w} height={10} fill="#fff" opacity={0.08} />
			{Array.from({length: rows}).map((_, r) =>
				Array.from({length: cols}).map((_, c) => (
					<rect key={`${r}-${c}`} x={x + 16 + c * ((w - 32) / cols)} y={base - h + 26 + r * 70} width={(w - 32) / cols - 16} height={34} rx={4} fill={win} opacity={rand(seed + r * 7 + c) > 0.25 ? 0.9 : 0.45} />
				)),
			)}
		</g>
	);
};

export const Buildings: React.FC<{camX: number}> = ({camX}) => (
	<g>
		<g transform={`translate(${camX * 0.7} 0)`}>
			{Array.from({length: 16}).map((_, i) => building(-1200 + i * 300 + rand(i) * 60, 220 + rand(i * 2) * 90, 420 + rand(i * 3) * 380, PLAZA_TOP - 20, C.bldFar, '#C9DDF4', i * 11))}
		</g>
		<g transform={`translate(${camX * 0.45} 0)`}>
			{Array.from({length: 16}).map((_, i) => building(-1000 + i * 330 + rand(i * 9) * 80, 200 + rand(i * 4) * 120, 300 + rand(i * 6) * 360, PLAZA_TOP, C.bldMid, C.window, i * 13 + 5))}
		</g>
	</g>
);

export const Tree: React.FC<{x: number; y?: number; s?: number; F?: number; seed?: number}> = ({x, y = PLAZA_TOP + 14, s = 1, F = 0, seed = 0}) => {
	const sway = Math.sin(F / 30 + seed) * 2;
	return (
		<g transform={`translate(${x} ${y}) scale(${s})`}>
			<rect x={-9} y={-120} width={18} height={124} rx={6} fill={C.trunk} />
			<g transform={`rotate(${sway} 0 -110)`}>
				<circle cx={0} cy={-170} r={70} fill={C.treeA} />
				<circle cx={-40} cy={-140} r={48} fill={C.treeB} />
				<circle cx={36} cy={-130} r={40} fill={C.treeB} opacity={0.8} />
			</g>
		</g>
	);
};

export const Bench: React.FC<{x: number; y: number}> = ({x, y}) => (
	<g transform={`translate(${x} ${y})`}>
		<rect x={-80} y={-46} width={160} height={10} rx={4} fill="#7A5638" />
		<rect x={-80} y={-80} width={160} height={10} rx={4} fill="#7A5638" />
		<rect x={-70} y={-46} width={8} height={46} fill="#4A3524" />
		<rect x={62} y={-46} width={8} height={46} fill="#4A3524" />
	</g>
);

export const Lamp: React.FC<{x: number; y?: number}> = ({x, y = WALK + 8}) => (
	<g transform={`translate(${x} ${y})`}>
		<rect x={-5} y={-330} width={10} height={330} fill="#3B4660" />
		<rect x={-24} y={-350} width={48} height={22} rx={8} fill="#3B4660" />
		<rect x={-16} y={-330} width={32} height={8} rx={3} fill="#FFF1C2" opacity={0.7} />
	</g>
);

export const Ground: React.FC<{camX: number}> = ({camX}) => {
	const x0 = camX - 1600, w = 3200;
	return (
		<g>
			<rect x={x0} y={PLAZA_TOP} width={w} height={WALK - PLAZA_TOP} fill={C.grass} />
			<rect x={x0} y={PLAZA_TOP} width={w} height={14} fill={C.grassDark} />
			<rect x={x0} y={WALK} width={w} height={CURB - WALK} fill={C.sidewalk} />
			{Array.from({length: 60}).map((_, i) => {
				const x = Math.floor(x0 / 90) * 90 + i * 90;
				return <line key={i} x1={x} y1={WALK} x2={x - 30} y2={CURB} stroke={C.tile} strokeWidth={3} />;
			})}
			<rect x={x0} y={CURB} width={w} height={14} fill={C.curb} />
			<rect x={x0} y={ROAD0} width={w} height={ROAD1 - ROAD0} fill={C.road} />
			{Array.from({length: 40}).map((_, i) => {
				const x = Math.floor(x0 / 150) * 150 + i * 150;
				return <rect key={i} x={x} y={(ROAD0 + ROAD1) / 2 - 5} width={80} height={10} rx={5} fill={C.lane} opacity={0.85} />;
			})}
			<rect x={x0} y={ROAD1} width={w} height={14} fill={C.curb} />
			<rect x={x0} y={ROAD1 + 14} width={w} height={2800 - ROAD1} fill="#D6CEBC" />
		</g>
	);
};

export const Cube: React.FC<{doorOpen?: number; doorLight?: number; F?: number; ventBubble?: React.ReactNode}> = ({doorOpen = 0, doorLight = 0, ventBubble}) => {
	const {x0, x1, top, bottom, dx, dy} = CUBE;
	const dX0 = x0 + 0.65 * (x1 - x0), dX1 = x0 + 0.81 * (x1 - x0), dTop = bottom - 0.445 * (bottom - top);
	return (
		<g>
			{/* contact shadow on the ground (sidewalk + plaza), never on the background */}
			<polygon points={`${x1},${bottom} ${x1 + dx},${bottom + dy} ${x1 + dx + 90},${bottom + dy} ${x1 + 70},${bottom}`} fill="#000" opacity={0.16} />
			<polygon points={`${x0 - 10},${bottom} ${x1 + 70},${bottom} ${x1 + 50},${bottom + 14} ${x0 - 4},${bottom + 14}`} fill="#000" opacity={0.12} />
			<polygon points={`${x0},${top} ${x0 + dx},${top + dy} ${x1 + dx},${top + dy} ${x1},${top}`} fill={C.cubeTop} />
			<polygon points={`${x1},${top} ${x1 + dx},${top + dy} ${x1 + dx},${bottom + dy} ${x1},${bottom}`} fill={C.cubeSide} />
			<rect x={x0} y={top} width={x1 - x0} height={bottom - top} fill={C.cube} />
			<rect x={x0} y={top} width={x1 - x0} height={8} fill="#fff" opacity={0.05} />
			{/* vent */}
			{[0, 1, 2].map((i) => <rect key={i} x={x0 + 0.157 * (x1 - x0)} y={top + 0.335 * (bottom - top) + i * 13} width={0.167 * (x1 - x0)} height={7} rx={3} fill="#2B3552" />)}
			{/* door (opens inward: dark gap -> light) */}
			<rect x={dX0} y={dTop} width={dX1 - dX0} height={bottom - dTop} fill="#FFF1C7" opacity={doorOpen > 0 ? 1 : 0} />
			<g transform={`translate(${dX1} 0) scale(${1 - doorOpen * 0.85} 1) translate(${-dX1} 0)`}>
				<rect x={dX0} y={dTop} width={dX1 - dX0} height={bottom - dTop} fill="#141B2B" stroke="#2C3757" strokeWidth={4} />
				<circle cx={dX0 + 12} cy={dTop + 0.6 * (bottom - dTop)} r={5} fill="#5A5038" />
			</g>
			{doorLight > 0 ? <polygon points={`${dX0},${bottom} ${dX1},${bottom} ${dX1 + 150},${bottom + 40} ${dX0 - 60},${bottom + 40}`} fill="#FFF1C7" opacity={0.5 * doorLight} /> : null}
			{ventBubble}
		</g>
	);
};

export const Car: React.FC<{x: number; y?: number; color?: string; dir?: 1 | -1; F: number}> = ({x, y = ROAD0 + 120, color = '#E5534B', dir = 1, F}) => (
	<g transform={`translate(${x} ${y}) scale(${dir} 1)`}>
		<rect x={-110} y={-62} width={220} height={52} rx={18} fill={color} />
		<path d="M -60 -62 L -40 -100 L 40 -100 L 66 -62 Z" fill={color} />
		<path d="M -48 -66 L -32 -94 L -2 -94 L -2 -66 Z" fill="#CFE3F7" />
		<path d="M 6 -66 L 6 -94 L 36 -94 L 56 -66 Z" fill="#CFE3F7" />
		{[-62, 62].map((wx) => (
			<g key={wx} transform={`translate(${wx} -10) rotate(${(F * 18) % 360})`}>
				<circle r={22} fill="#1F2430" />
				<circle r={9} fill="#9AA3B5" />
				<rect x={-2} y={-20} width={4} height={14} fill="#3A4256" />
			</g>
		))}
		<rect x={92} y={-50} width={14} height={10} rx={4} fill="#FFF3B0" />
	</g>
);

export const Bird: React.FC<{x: number; y: number; F: number; s?: number}> = ({x, y, F, s = 1}) => {
	const flap = Math.sin(F * 0.6 + x) * 10;
	return <path transform={`translate(${x} ${y}) scale(${s})`} d={`M -16 ${flap * 0.4} Q -8 ${-6 - flap} 0 0 Q 8 ${-6 - flap} 16 ${flap * 0.4}`} stroke="#2E3C5C" strokeWidth={3.5} fill="none" strokeLinecap="round" />;
};

export const Bicycle: React.FC<{x: number; y: number; rot?: number; wheel?: number; s?: number; color?: string}> = ({x, y, rot = 0, wheel = 0, s = 1, color = '#2FB3B3'}) => (
	<g transform={`translate(${x} ${y}) rotate(${rot}) scale(${s})`}>
		{[-58, 58].map((wx) => (
			<g key={wx} transform={`translate(${wx} -34)`}>
				<circle r={34} fill="none" stroke="#1F2430" strokeWidth={6} />
				{[0, 60, 120].map((a) => <line key={a} x1={0} y1={0} x2={Math.cos(((a + wheel) * Math.PI) / 180) * 30} y2={Math.sin(((a + wheel) * Math.PI) / 180) * 30} stroke="#8A93A8" strokeWidth={2} />)}
			</g>
		))}
		<path d="M -58 -34 L -14 -34 L 20 -84 L -26 -84 Z M -14 -34 L -34 -98 M 20 -84 L 58 -34 M 20 -84 L 26 -106" stroke={color} strokeWidth={7} fill="none" strokeLinejoin="round" strokeLinecap="round" />
		<rect x={-48} y={-106} width={30} height={9} rx={4} fill="#1F2430" />
		<rect x={18} y={-112} width={26} height={7} rx={3} fill="#1F2430" />
	</g>
);

export const Flower: React.FC<{x: number; y: number; bloom: number}> = ({x, y, bloom}) => (
	<g transform={`translate(${x} ${y})`}>
		<path d={`M 0 0 Q -3 -20 0 ${-30 - bloom * 14}`} stroke="#4FA35E" strokeWidth={4} fill="none" />
		<ellipse cx={-8} cy={-14} rx={8} ry={4} fill="#5FBF6E" transform="rotate(-30 -8 -14)" />
		<g transform={`translate(0 ${-30 - bloom * 14}) scale(${0.25 + bloom * 0.75})`}>
			{Array.from({length: 6}).map((_, i) => (
				<ellipse key={i} cx={Math.cos((i * Math.PI) / 3) * 12} cy={Math.sin((i * Math.PI) / 3) * 12} rx={10} ry={7} transform={`rotate(${i * 60} ${Math.cos((i * Math.PI) / 3) * 12} ${Math.sin((i * Math.PI) / 3) * 12})`} fill="#F28BB0" />
			))}
			<circle r={8} fill="#F2C14E" />
		</g>
		{bloom > 0.2 && bloom < 1
			? [0, 1, 2, 3].map((i) => <circle key={i} cx={Math.cos(i * 1.7) * (20 + bloom * 30)} cy={-40 - Math.sin(i * 1.7) * (20 + bloom * 30)} r={3} fill="#FFE9A0" opacity={1 - bloom} />)
			: null}
	</g>
);

export const Stairs: React.FC<{x0: number; y0: number; x1: number; y1: number; n: number}> = ({x0, y0, x1, y1, n}) => {
	const run = (x1 - x0) / n, rise = (y0 - y1) / n;
	const pts: string[] = [`${x0},${y0}`];
	for (let i = 0; i < n; i++) {
		pts.push(`${x0 + i * run},${y0 - (i + 1) * rise}`);
		pts.push(`${x0 + (i + 1) * run},${y0 - (i + 1) * rise}`);
	}
	pts.push(`${x1},${y0}`);
	return (
		<g>
			<polygon points={pts.join(' ')} fill="#B9A58A" />
			{Array.from({length: n}).map((_, i) => <rect key={i} x={x0 + i * run} y={y0 - (i + 1) * rise} width={run} height={5} fill="#D8C7AC" />)}
			<line x1={x0 + 10} y1={y0 - 70} x2={x1 + 10} y2={y1 - 70} stroke="#6B5A44" strokeWidth={6} />
			{Array.from({length: Math.floor(n / 2)}).map((_, i) => (
				<line key={i} x1={x0 + 10 + i * 2 * run} y1={y0 - 70 - i * 2 * rise} x2={x0 + 10 + i * 2 * run} y2={y0 - i * 2 * rise} stroke="#6B5A44" strokeWidth={4} />
			))}
		</g>
	);
};

export const Signpost: React.FC<{x: number; y: number; wob?: number}> = ({x, y, wob = 0}) => (
	<g transform={`translate(${x} ${y})`}>
		<rect x={-6} y={-230} width={12} height={230} fill="#6B4F3A" />
		<g transform={`rotate(${-6 + wob} 0 -190)`}>
			<path d="M 0 -214 L 120 -214 L 146 -192 L 120 -170 L 0 -170 Z" fill="#E8D9B5" />
			<text x={64} y={-180} textAnchor="middle" fontFamily={FONT} fontWeight={900} fontSize={30} fill="#6B4F3A">?</text>
		</g>
		<g transform={`rotate(${8 - wob} 0 -130)`}>
			<path d="M 0 -150 L -120 -150 L -146 -128 L -120 -106 L 0 -106 Z" fill="#E8D9B5" />
			<text x={-64} y={-116} textAnchor="middle" fontFamily={FONT} fontWeight={900} fontSize={30} fill="#6B4F3A">?</text>
		</g>
	</g>
);

export const Fog: React.FC<{x: number; y: number; w: number; F: number; a?: number}> = ({x, y, w, F, a = 1}) => (
	<g opacity={a}>
		{Array.from({length: 9}).map((_, i) => {
			const px = x + ((i * w) / 8 + Math.sin((F + i * 50) / 40) * 30);
			return <ellipse key={i} cx={px} cy={y + Math.sin(i * 1.3) * 30} rx={140} ry={60} fill="#F4F7FB" opacity={0.55} />;
		})}
	</g>
);

export const RainCloud: React.FC<{x: number; y: number; F: number; rain: number}> = ({x, y, F, rain}) => (
	<g>
		{rain > 0
			? Array.from({length: 36}).map((_, i) => {
				const px = x - 170 + rand(i * 3) * 340;
				const py = y + 30 + ((F * 26 + rand(i) * 800) % 700);
				return <line key={i} x1={px} y1={py} x2={px - 6} y2={py + 28} stroke="#7FA4D8" strokeWidth={3} opacity={0.7 * rain} strokeLinecap="round" />;
			})
			: null}
		<g transform={`translate(${x} ${y})`}>
			<ellipse cx={0} cy={0} rx={190} ry={62} fill="#6E7891" />
			<ellipse cx={-70} cy={-34} rx={90} ry={56} fill="#7A84A0" />
			<ellipse cx={60} cy={-44} rx={80} ry={52} fill="#7A84A0" />
		</g>
	</g>
);

export const Umbrella: React.FC<{open: number}> = ({open}) => (
	<g>
		<line x1={0} y1={0} x2={0} y2={-120} stroke="#2E3448" strokeWidth={5} />
		<path d={`M ${-90 * open - 8} -110 Q 0 ${-190 - 20 * open} ${90 * open + 8} -110 Z`} fill="#E5534B" />
		<path d="M 0 0 q 0 14 -12 14" stroke="#2E3448" strokeWidth={5} fill="none" />
	</g>
);

export const Box: React.FC<{w?: number; h?: number}> = ({w = 90, h = 70}) => (
	<g>
		<rect x={-w / 2} y={-h} width={w} height={h} rx={4} fill="#C79A63" />
		<rect x={-w / 2} y={-h} width={w} height={12} fill="#B5874F" />
		<rect x={-6} y={-h} width={12} height={h} fill="#E2C99A" opacity={0.7} />
	</g>
);

export const Laptop: React.FC<{glow: number}> = ({glow}) => (
	<g>
		<rect x={-44} y={-6} width={88} height={8} rx={3} fill="#8A93A8" />
		<path d="M -36 -6 L -30 -62 L 34 -62 L 40 -6 Z" fill="#3A4256" />
		<path d="M -30 -10 L -25 -56 L 29 -56 L 34 -10 Z" fill="#CFE3F7" opacity={0.6 + 0.4 * glow} />
	</g>
);

export const shirt = (i: number) => SHIRTS[i % SHIRTS.length];
export {clamp, ease, lerp};
