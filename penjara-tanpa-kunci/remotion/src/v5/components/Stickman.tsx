import React, {useId} from 'react';
import {C} from '../theme';
import {lerp} from '../lib/anim';

// Jointed stickman rig. Local space: hip at (0,0), +x = facing direction, +y = down.
// Angles in degrees. Limb angle 0 = pointing straight down, +90 = pointing forward.
export type Pose = {
	torso: number; // lean of torso (+ forward)
	head: number; // head tilt relative to torso (+ forward/down)
	armN: [number, number]; // near arm: shoulder angle, elbow bend (+ forearm swings forward/up)
	armF: [number, number]; // far arm
	legN: [number, number]; // near leg: hip angle, knee bend (+ shin swings back)
	legF: [number, number];
	bob: number; // vertical offset of the hip (px, - = up)
	eyes: number; // 1 open .. 0 closed
	lid: number; // 0..1 tired upper lid
	look: number; // eye shift (-1 back .. 1 forward)
	lookY: number; // -1 up .. 1 down
	brow: number; // -1 angry .. 0 none .. 1 worried
	mouth: number; // -1 sad .. 0 none .. 1 smile
};

export const P0: Pose = {
	torso: 0, head: 0, armN: [6, 8], armF: [-6, 8], legN: [2, 2], legF: [-2, 2], bob: 0,
	eyes: 1, lid: 0, look: 0, lookY: 0, brow: 0, mouth: 0,
};

const L = {torso: 92, neck: 8, head: 36, upper: 54, fore: 50, thigh: 66, shin: 64};
export const STAND_HIP = L.thigh + L.shin + 6; // hip height above the ground when standing straight

export const mix = (a: Pose, b: Pose, t: number): Pose => ({
	torso: lerp(a.torso, b.torso, t), head: lerp(a.head, b.head, t),
	armN: [lerp(a.armN[0], b.armN[0], t), lerp(a.armN[1], b.armN[1], t)],
	armF: [lerp(a.armF[0], b.armF[0], t), lerp(a.armF[1], b.armF[1], t)],
	legN: [lerp(a.legN[0], b.legN[0], t), lerp(a.legN[1], b.legN[1], t)],
	legF: [lerp(a.legF[0], b.legF[0], t), lerp(a.legF[1], b.legF[1], t)],
	bob: lerp(a.bob, b.bob, t), eyes: lerp(a.eyes, b.eyes, t), lid: lerp(a.lid, b.lid, t),
	look: lerp(a.look, b.look, t), lookY: lerp(a.lookY, b.lookY, t), brow: lerp(a.brow, b.brow, t),
	mouth: lerp(a.mouth, b.mouth, t),
});

/** idle standing with breathing; f = frame */
export const idle = (f: number, extra: Partial<Pose> = {}): Pose => {
	const br = Math.sin(f / 22);
	return {...P0, bob: br * 1.2, armN: [6 + br * 1.5, 10], armF: [-6 - br * 1.5, 10], head: br * 1.2, ...extra};
};

/** walk cycle: phase in radians (advance ~0.25-0.32 rad/frame for a normal walk) */
export const walk = (phase: number, amt = 1, extra: Partial<Pose> = {}): Pose => {
	const s = Math.sin(phase), c = Math.cos(phase);
	const sw = 26 * amt;
	const kneeN = Math.max(0, Math.sin(phase + 1.9)) * 40 * amt + 4;
	const kneeF = Math.max(0, Math.sin(phase + 1.9 + Math.PI)) * 40 * amt + 4;
	return {
		...P0,
		torso: 4 * amt,
		head: -2 * amt + c * 1.2,
		legN: [sw * s, kneeN],
		legF: [-sw * s, kneeF],
		armN: [-22 * amt * s, 18 + 10 * Math.max(0, -s) * amt],
		armF: [22 * amt * s, 18 + 10 * Math.max(0, s) * amt],
		bob: -Math.abs(c) * 5 * amt + 2.5 * amt,
		...extra,
	};
};

/** sitting on a seat (hip at seat height); feet on the floor */
export const sit = (f: number, extra: Partial<Pose> = {}): Pose => {
	const br = Math.sin(f / 24);
	return {...P0, torso: 6 + br, head: 4, legN: [88, 92], legF: [84, 88], armN: [24, 40], armF: [18, 44], bob: 0, ...extra};
};

type Props = {
	x: number; y: number; // hip position in the scene
	pose: Pose;
	scale?: number;
	facing?: 1 | -1;
	rot?: number; // whole-body rotation around the hip (deg), e.g. lying = -90
	shirt?: string;
	pants?: string;
	skin?: string;
	front?: boolean; // face drawn frontal (eyes centered)
	shadow?: number | null; // ground y for a contact shadow (scene px); null = none
	opacity?: number;
	dim?: number; // 0..1 darken (night scenes)
	children?: React.ReactNode; // drawn in hand-space of the near hand (props like phone)
	handProp?: React.ReactNode; // drawn at near hand, rotated with forearm
	farHandProp?: React.ReactNode;
	glow?: string; // rim light colour on the face side
};

const rad = (d: number) => (d * Math.PI) / 180;
const dirv = (deg: number): [number, number] => [Math.sin(rad(deg)), Math.cos(rad(deg))];

export const limbEnd = (pose: Pose, which: 'armN' | 'armF' | 'legN' | 'legF') => {
	const tor = pose.torso;
	const [sx, sy] = [Math.sin(rad(tor)) * L.torso, -Math.cos(rad(tor)) * L.torso];
	if (which.startsWith('arm')) {
		const [a, e] = pose[which as 'armN'];
		const A = a + tor;
		const [ux, uy] = dirv(A);
		const [fx, fy] = dirv(A + e);
		return {x: sx + ux * L.upper + fx * L.fore, y: sy + uy * L.upper + fy * L.fore + pose.bob, angle: A + e};
	}
	const [h, k] = pose[which as 'legN'];
	const [ux, uy] = dirv(h);
	const [fx, fy] = dirv(h - k);
	return {x: ux * L.thigh + fx * L.shin, y: uy * L.thigh + fy * L.shin + pose.bob, angle: h - k};
};

export const Stickman: React.FC<Props> = ({
	x, y, pose, scale = 1, facing = 1, rot = 0, shirt = C.shirt, pants = C.pants, skin = C.skin, front = false,
	shadow = null, opacity = 1, dim = 0, handProp, farHandProp, glow,
}) => {
	const id = useId().replace(/:/g, '');
	const p = pose;
	const tor = p.torso;
	const hip: [number, number] = [0, p.bob];
	const sh: [number, number] = [Math.sin(rad(tor)) * L.torso, p.bob - Math.cos(rad(tor)) * L.torso];
	const nk: [number, number] = [sh[0] + Math.sin(rad(tor)) * L.neck, sh[1] - Math.cos(rad(tor)) * L.neck];
	const ht = tor + p.head;
	const hc: [number, number] = [nk[0] + Math.sin(rad(ht)) * L.head * 0.92, nk[1] - Math.cos(rad(ht)) * L.head * 0.92];

	const arm = (a: number, e: number) => {
		const A = a + tor;
		const [ux, uy] = dirv(A);
		const el: [number, number] = [sh[0] + ux * L.upper, sh[1] + uy * L.upper];
		const [fx, fy] = dirv(A + e);
		const ha: [number, number] = [el[0] + fx * L.fore, el[1] + fy * L.fore];
		return {el, ha, ang: A + e};
	};
	const leg = (h: number, k: number) => {
		const [ux, uy] = dirv(h);
		const kn: [number, number] = [hip[0] + ux * L.thigh, hip[1] + uy * L.thigh];
		const [fx, fy] = dirv(h - k);
		const an: [number, number] = [kn[0] + fx * L.shin, kn[1] + fy * L.shin];
		return {kn, an};
	};
	const aN = arm(p.armN[0], p.armN[1]);
	const aF = arm(p.armF[0], p.armF[1]);
	const lN = leg(p.legN[0], p.legN[1]);
	const lF = leg(p.legF[0], p.legF[1]);

	const shade = (c: string, k: number) => c; // colours stay flat; depth via darker far limbs below
	const farLimb = '#B9C1D2';
	const farPants = '#0C1019';
	const lw = {arm: 13, leg: 15, torso: 30};
	const seg = (a: [number, number], b: [number, number], c: string, w: number) => (
		<line x1={a[0]} y1={a[1]} x2={b[0]} y2={b[1]} stroke={c} strokeWidth={w} strokeLinecap="round" />
	);
	const foot = (an: [number, number], c: string) => (
		<ellipse cx={an[0] + 8} cy={an[1] + 3} rx={14} ry={7.5} fill={c} />
	);

	// face
	const R = L.head;
	const lookX = p.look * R * 0.07;
	const lookY = p.lookY * R * 0.06;
	const eyesPos: [number, number][] = front
		? [[-R * 0.3, -R * 0.02], [R * 0.3, -R * 0.02]]
		: [[R * 0.12, -R * 0.04], [R * 0.48, -R * 0.04]];
	const eyeRx = R * 0.085, eyeRy = R * 0.13;
	const open = Math.max(0.06, p.eyes);

	return (
		<g opacity={opacity} transform={`translate(${x} ${y}) rotate(${rot}) scale(${facing * scale} ${scale})`}>
			<defs>
				<radialGradient id={`hg${id}`} cx="38%" cy="32%" r="75%">
					<stop offset="0%" stopColor="#FFFFFF" />
					<stop offset="60%" stopColor={skin} />
					<stop offset="100%" stopColor={C.skinShade} />
				</radialGradient>
			</defs>
			{shadow !== null && shadow !== undefined ? (
				<ellipse cx={0} cy={(shadow - y) / scale} rx={46} ry={8} fill="rgba(0,0,0,0.22)" />
			) : null}
			{/* far side */}
			{seg(aF.el, sh, farLimb, lw.arm)}
			{seg(aF.ha, aF.el, farLimb, lw.arm)}
			<g transform={`translate(${aF.ha[0]} ${aF.ha[1]}) rotate(${-aF.ang})`}>{farHandProp}</g>
			<circle cx={aF.ha[0]} cy={aF.ha[1]} r={7.5} fill={farLimb} />
			{seg(hip, lF.kn, farPants, lw.leg)}
			{seg(lF.kn, lF.an, farPants, lw.leg)}
			{foot(lF.an, farPants)}
			{/* torso */}
			<line x1={hip[0]} y1={hip[1]} x2={sh[0]} y2={sh[1]} stroke={shirt} strokeWidth={lw.torso} strokeLinecap="round" />
			<line
				x1={hip[0] - Math.cos(rad(tor)) * 8} y1={hip[1] - Math.sin(rad(tor)) * 8 - 6}
				x2={sh[0] - Math.cos(rad(tor)) * 8} y2={sh[1] - Math.sin(rad(tor)) * 8 + 6}
				stroke="#000" strokeOpacity={shirt === C.shirt ? 0.07 : 0.12} strokeWidth={9} strokeLinecap="round"
			/>
			{/* near leg */}
			{seg(hip, lN.kn, pants, lw.leg)}
			{seg(lN.kn, lN.an, pants, lw.leg)}
			{foot(lN.an, pants)}
			{/* neck + head */}
			{seg(sh, nk, skin, 10)}
			<g transform={`translate(${hc[0]} ${hc[1]}) rotate(${ht * 0.6})`}>
				<circle r={R} fill={`url(#hg${id})`} />
				{glow ? <circle r={R} fill="none" stroke={glow} strokeOpacity={0.55} strokeWidth={3} /> : null}
				{eyesPos.map(([ex, ey], i) => (
					<g key={i} transform={`translate(${ex + lookX} ${ey + lookY})`}>
						<ellipse rx={eyeRx} ry={eyeRy * open} fill="#1A2030" />
						{p.lid > 0.02 ? (
							<rect x={-eyeRx * 1.6} y={-eyeRy * 1.4} width={eyeRx * 3.2} height={eyeRy * 1.4 * p.lid + eyeRy * 0.15} fill={skin} />
						) : null}
						{p.brow !== 0 ? (
							<line
								x1={-eyeRx * 1.3} x2={eyeRx * 1.3}
								y1={-eyeRy * 1.9 + (front ? (i === 0 ? 1 : -1) : i === 0 ? 1 : -1) * p.brow * 3}
								y2={-eyeRy * 1.9 - (front ? (i === 0 ? 1 : -1) : i === 0 ? 1 : -1) * p.brow * 3}
								stroke="#3A4256" strokeWidth={3.2} strokeLinecap="round"
							/>
						) : null}
					</g>
				))}
				{p.mouth !== 0 ? (
					<path
						d={`M ${(front ? -R * 0.16 : R * 0.16)} ${R * 0.36} Q ${front ? 0 : R * 0.32} ${R * 0.36 + p.mouth * R * 0.12} ${front ? R * 0.16 : R * 0.48} ${R * 0.36}`}
						stroke="#3A4256" strokeWidth={3} fill="none" strokeLinecap="round"
					/>
				) : null}
			</g>
			{/* near arm on top */}
			{seg(sh, aN.el, skin, lw.arm)}
			{seg(aN.el, aN.ha, skin, lw.arm)}
			<g transform={`translate(${aN.ha[0]} ${aN.ha[1]}) rotate(${-aN.ang})`}>{handProp}</g>
			<circle cx={aN.ha[0]} cy={aN.ha[1]} r={8} fill={skin} />
			{dim > 0 ? null : null}
		</g>
	);
};
