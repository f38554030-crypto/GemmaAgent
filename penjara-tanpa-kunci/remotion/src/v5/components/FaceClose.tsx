import React from 'react';
import {Pose, P0} from './Stickman';
import {Hand} from './Hand';
import {C} from '../theme';
import {rand} from '../lib/anim';

// Face close-up (bust): big head + neck + shoulders, same face language as the rig (eyes, lids, brows, mouth).
export const BigFace: React.FC<{p: Pose; tilt?: number; light?: 'phone' | 'moon' | 'sun' | 'none'; cx?: number; cy?: number; R?: number}> = ({p, tilt = 0, light = 'none', cx = 540, cy = 820, R = 290}) => {
	const eyeX = R * 0.36, eyeY = -R * 0.03, rx = R * 0.085, ry = R * 0.135;
	const lx = p.look * R * 0.07, ly = p.lookY * R * 0.06;
	const open = Math.max(0.05, p.eyes);
	const rim = light === 'phone' ? {c: C.phoneGlow, x: 0, y: 1} : light === 'moon' ? {c: C.moon, x: -1, y: -0.3} : light === 'sun' ? {c: '#FFE3A0', x: 1, y: -0.4} : null;
	return (
		<g>
			<defs>
				<radialGradient id="bfHead" cx="40%" cy="34%" r="72%">
					<stop offset="0%" stopColor="#FFFFFF" />
					<stop offset="62%" stopColor={C.skin} />
					<stop offset="100%" stopColor={C.skinShade} />
				</radialGradient>
				<linearGradient id="bfBody" x1="0" y1="0" x2="0" y2="1">
					<stop offset="0%" stopColor="#EEF1F7" />
					<stop offset="35%" stopColor="#B9C3D6" />
					<stop offset="70%" stopColor="#5E6A86" />
					<stop offset="100%" stopColor="#2A3248" />
				</linearGradient>
			</defs>
			{/* shoulders + neck */}
			<path d={`M ${cx - R * 1.35} 1920 L ${cx - R * 1.35} ${cy + R * 1.55} Q ${cx - R * 1.3} ${cy + R * 1.05} ${cx - R * 0.55} ${cy + R * 1.0} L ${cx + R * 0.55} ${cy + R * 1.0} Q ${cx + R * 1.3} ${cy + R * 1.05} ${cx + R * 1.35} ${cy + R * 1.55} L ${cx + R * 1.35} 1920 Z`} fill="url(#bfBody)" />
			<rect x={cx - R * 0.2} y={cy + R * 0.7} width={R * 0.4} height={R * 0.45} rx={R * 0.12} fill={C.skinShade} />
			<g transform={`rotate(${tilt} ${cx} ${cy + R * 1.1})`}>
				<circle cx={cx} cy={cy} r={R} fill="url(#bfHead)" />
				{rim ? <circle cx={cx + rim.x * 6} cy={cy + rim.y * 6} r={R - 3} fill="none" stroke={rim.c} strokeWidth={10} strokeOpacity={0.35} /> : null}
				{[-1, 1].map((side) => (
					<g key={side} transform={`translate(${cx + side * eyeX + lx} ${cy + eyeY + ly})`}>
						<ellipse rx={rx} ry={ry * open} fill="#1A2030" />
						{open > 0.3 ? <circle cx={rx * 0.35} cy={-ry * 0.35 * open} r={rx * 0.28} fill="#fff" opacity={0.8} /> : null}
						{p.lid > 0.02 ? <rect x={-rx * 1.7} y={-ry * 1.5} width={rx * 3.4} height={ry * 1.5 * p.lid + ry * 0.2} fill={C.skin} /> : null}
						{p.brow !== 0 ? (
							<line x1={-rx * 1.4} x2={rx * 1.4} y1={-ry * 1.85 - side * p.brow * R * 0.035} y2={-ry * 1.85 + side * p.brow * R * 0.035} stroke="#3A4256" strokeWidth={R * 0.032} strokeLinecap="round" />
						) : null}
					</g>
				))}
				{p.mouth !== 0 ? (
					<path d={`M ${cx - R * 0.16} ${cy + R * 0.42} Q ${cx} ${cy + R * 0.42 + p.mouth * R * 0.14} ${cx + R * 0.16} ${cy + R * 0.42}`} stroke="#3A4256" strokeWidth={R * 0.03} fill="none" strokeLinecap="round" />
				) : null}
			</g>
		</g>
	);
};

export const FaceClose: React.FC<{
	f: number; bg: 'nightNotes' | 'night' | 'day'; expr: Partial<Pose>; tilt?: number; push?: number;
	chinHand?: number; glow?: 'phone' | 'moon' | 'sun' | 'none'; lookDrift?: boolean; children?: React.ReactNode;
}> = ({f, bg, expr, tilt = 0, push = 0, chinHand = 0, glow = 'none', lookDrift = false, children}) => {
	const z = 1 + push;
	const breathe = Math.sin(f / 26) * 5;
	const pose: Pose = {...P0, look: lookDrift ? Math.sin(f / 40) * 0.6 : 0, ...expr};
	return (
		<svg width={1080} height={1920} viewBox="0 0 1080 1920">
			<defs>
				<radialGradient id="fcNight" cx="50%" cy="40%" r="80%">
					<stop offset="0%" stopColor="#24324F" />
					<stop offset="100%" stopColor="#0B101C" />
				</radialGradient>
				<linearGradient id="fcDay" x1="0" y1="0" x2="0" y2="1">
					<stop offset="0%" stopColor={C.skyTop} />
					<stop offset="100%" stopColor={C.skyBottom} />
				</linearGradient>
				<radialGradient id="fcGlow" cx="50%" cy="50%" r="50%">
					<stop offset="0%" stopColor={glow === 'sun' ? '#FFE9B0' : glow === 'moon' ? C.moon : C.phoneGlow} stopOpacity={0.45} />
					<stop offset="100%" stopColor={C.phoneGlow} stopOpacity={0} />
				</radialGradient>
				<filter id="fcBlur"><feGaussianBlur stdDeviation="10" /></filter>
			</defs>
			<rect width={1080} height={1920} fill={bg === 'day' ? 'url(#fcDay)' : 'url(#fcNight)'} />
			{bg === 'nightNotes' ? (
				<g filter="url(#fcBlur)" opacity={0.5}>
					{Array.from({length: 26}).map((_, i) => (
						<rect key={i} x={rand(i * 3.3) * 1080 - 40} y={rand(i * 5.1) * 1300 - 40} width={90} height={90} rx={6} fill={C.note} transform={`rotate(${(rand(i) - 0.5) * 30} ${rand(i * 3.3) * 1080} ${rand(i * 5.1) * 1300})`} />
					))}
				</g>
			) : null}
			{bg === 'day' ? <circle cx={860} cy={300} r={160} fill="#FFF4CC" opacity={0.7} filter="url(#fcBlur)" /> : null}
			<g transform={`translate(540 960) scale(${z}) translate(-540 -960) translate(0 ${breathe})`}>
				{glow !== 'none' ? <ellipse cx={540} cy={glow === 'phone' ? 1300 : 760} rx={560} ry={460} fill="url(#fcGlow)" /> : null}
				<BigFace p={pose} tilt={tilt} light={glow} />
				{children}
				{chinHand > 0 ? <Hand x={640} y={1180 + (1 - chinHand) * 700} rot={-16} s={1.6} pose="fist" arm={900} /> : null}
			</g>
		</svg>
	);
};
