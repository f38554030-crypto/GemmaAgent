import React from 'react';
import {AbsoluteFill} from 'remotion';
import {FONT} from '../../fonts';
import {AlarmClock, BED, Blanket, FLOOR, NOTES, Room, StickyNote, TABLE} from '../components/Room';
import {Phone} from '../components/Phone';
import {Hand} from '../components/Hand';
import {P0, Pose, Stickman, mix} from '../components/Stickman';
import {blink, clamp, ease, env, keys, lerp, tween} from '../lib/anim';
import {calendarAt, notesAt} from '../lib/story';
import {C} from '../theme';

export const Cam: React.FC<{x: number; y: number; z: number; children: React.ReactNode}> = ({x, y, z, children}) => (
	<g transform={`translate(540 960) scale(${z}) translate(${-x} ${-y})`}>{children}</g>
);

export const Svg: React.FC<{children: React.ReactNode; bg?: string}> = ({children, bg = '#000'}) => (
	<AbsoluteFill style={{background: bg}}>
		<svg width={1080} height={1920} viewBox="0 0 1080 1920">{children}</svg>
	</AbsoluteFill>
);

/** momentum scroll from swipe impulses */
export const scrollFrom = (f: number, swipes: number[], amp = 330, tau = 7) =>
	swipes.reduce((acc, s) => (f > s ? acc + amp * (1 - Math.exp(-(f - s) / tau)) : acc), 0);
export const thumbFrom = (f: number, swipes: number[]) => {
	let y = 70;
	for (const s of swipes) {
		const d = f - s;
		if (d >= -3 && d < 10) y = d < 0 ? lerp(70, 90, (d + 3) / 3) : d < 5 ? lerp(90, -90, ease.out(d / 5)) : lerp(-90, 70, ease.inOut((d - 5) / 5));
	}
	return y;
};

/** lying on the back on the bed holding the phone above the face */
export const lieScroll = (f: number, extra: Partial<Pose> = {}): Pose => ({
	...P0, torso: 0, head: -6 + Math.sin(f / 40) * 1.5, legN: [6, 10], legF: [2, 6],
	armN: [118 + Math.sin(f / 13) * 1.2, -52], armF: [112, -44], eyes: blink(f, 3) * 0.85, lid: 0.35, look: 1, lookY: -0.5, ...extra,
});
export const LIE = {x: 392, y: BED.top - 18};
export const RS = 1.1; // character scale in the room

const PhoneInHand: React.FC<{lit: number}> = ({lit}) => (
	<g transform="translate(6 0) rotate(90)">
		<rect x={-30} y={-5} width={60} height={10} rx={3} fill="#05080F" />
		<rect x={-26} y={4} width={52} height={3} rx={1.5} fill={C.phoneGlow} opacity={0.9 * lit} />
	</g>
);

const FaceGlow: React.FC<{x: number; y: number; a: number}> = ({x, y, a}) => (
	<g opacity={a}>
		<defs>
			<radialGradient id="fglow">
				<stop offset="0%" stopColor={C.phoneGlow} stopOpacity={0.5} />
				<stop offset="100%" stopColor={C.phoneGlow} stopOpacity={0} />
			</radialGradient>
		</defs>
		<ellipse cx={x} cy={y} rx={230} ry={150} fill="url(#fglow)" />
	</g>
);

// ---------------- S01 hook: phone close-up (no title) ----------------
const HOOK_SWIPES = [2, 14, 26, 44, 58, 70];
export const Hook: React.FC<{f: number}> = ({f}) => {
	const hookOp = Math.min(clamp(f / 6), 1 - clamp((f - 66) / 10));
	return (
		<AbsoluteFill>
			<Svg bg="#060B15">
				<defs>
					<radialGradient id="hookbg" cx="55%" cy="58%" r="75%">
						<stop offset="0%" stopColor="#22384E" />
						<stop offset="45%" stopColor="#13243A" />
						<stop offset="100%" stopColor="#050A13" />
					</radialGradient>
				</defs>
				<rect width={1080} height={1920} fill="url(#hookbg)" />
				<Phone cx={560} cy={1130 - f * 0.4} w={560} rot={6 - f * 0.01} scroll={scrollFrom(f, HOOK_SWIPES)} thumbY={thumbFrom(f, HOOK_SWIPES)} />
			</Svg>
			<div style={{position: 'absolute', left: 0, right: 0, top: 300, textAlign: 'center', fontFamily: FONT, fontWeight: 900, opacity: hookOp, transform: `translateY(${(1 - clamp(f / 8)) * 14}px)`}}>
				<div style={{fontSize: 80, lineHeight: 1.08, color: '#fff', textShadow: '0 4px 24px rgba(0,0,0,0.6), 0 0 2px rgba(0,0,0,0.9)'}}>412 kali ia berkata</div>
				<div style={{fontSize: 108, lineHeight: 1.12, color: C.gold, textShadow: '0 0 30px rgba(242,193,78,0.45), 0 4px 24px rgba(0,0,0,0.6)'}}>“besok”.</div>
			</div>
		</AbsoluteFill>
	);
};

// ---------------- S02 bedroom at night: scrolling, then reaching for the note pad ----------------
export const BedNight: React.FC<{f: number; F: number}> = ({f, F}) => {
	// f: local frame (0..109), F: global frame
	const reach = tween(f, 70, 100, 0, 1, ease.inOut);
	const pose = mix(lieScroll(F), {...lieScroll(F), armN: [178, -10], head: -16, look: -0.4, lookY: 0.4, eyes: 1, lid: 0.15}, reach);
	const z = tween(f, 0, 109, 1.3, 1.42, ease.inOutSine);
	return (
		<Svg>
			<Cam x={460} y={1010} z={z}>
				<Room f={F} day={0} notes={0} calValue={1} phoneOnTable={false} beam={0.8}
					front={<Blanket />}>
					{/* note pad on the bedside table */}
					<rect x={TABLE.x0 + 70} y={TABLE.top - 7} width={44} height={7} rx={2} fill={C.note} opacity={1 - reach} />
					<FaceGlow x={LIE.x - 150} y={LIE.y - 90} a={1 - reach * 0.6} />
					<Stickman x={LIE.x} y={LIE.y} rot={-90} pose={pose} scale={RS}
						handProp={<PhoneInHand lit={1 - reach} />} />
				</Room>
			</Cam>
		</Svg>
	);
};

// ---------------- S03 close-up: note slapped on the wall right above the pillow, then "BESOK" written ----------------
export const NoteClose: React.FC<{f: number}> = ({f}) => {
	// 0..54 local frames (6.2s..8.0s). Note is pressed onto the wall right where his hand is, then written on.
	const slap = tween(f, 0, 7, 0, 1, ease.out);
	const wr = clamp((f - 9) / 33); // writing progress
	const NX = 560, NY = 880, NW = 420;
	const textL = NX - 182, textR = NX + 182;
	const revealX = lerp(textL, textR, wr);
	const penOn = f >= 8 && f < 46;
	const away = tween(f, 44, 54, 0, 1, ease.in);
	const jitter = Math.sin(f * 2.3) * 9 * (penOn ? 1 : 0);
	return (
		<Svg>
			<defs>
				<linearGradient id="ncwall" x1="0" y1="0" x2="0" y2="1">
					<stop offset="0%" stopColor="#22304E" />
					<stop offset="100%" stopColor="#161E33" />
				</linearGradient>
				<clipPath id="ncreveal">
					<rect x={0} y={0} width={revealX} height={1920} />
				</clipPath>
			</defs>
			<rect width={1080} height={1920} fill="url(#ncwall)" />
			{/* wall texture + edge of the pillow and headboard: we are right above his bed */}
			{[200, 420, 640, 860].map((x) => <rect key={x} x={x} y={0} width={3} height={1920} fill="#fff" opacity={0.02} />)}
			<ellipse cx={330} cy={1700} rx={460} ry={130} fill={C.pillow} />
			<ellipse cx={330} cy={1690} rx={420} ry={100} fill="#DCE3F0" />
			<rect x={-60} y={1380} width={140} height={700} rx={34} fill={C.bedFrame} />
			<g transform={`translate(${NX} ${NY}) scale(${lerp(1.12, 1, slap)}) rotate(-4)`}>
				<rect x={-NW / 2 + 10} y={-NW / 2 + 14} width={NW} height={NW} rx={14} fill="rgba(0,0,0,0.3)" />
				<rect x={-NW / 2} y={-NW / 2} width={NW} height={NW} rx={14} fill={C.note} />
				<rect x={-NW / 2} y={-NW / 2} width={NW} height={58} rx={14} fill={C.noteDark} opacity={0.6} />
			</g>
			<g clipPath="url(#ncreveal)">
				<text x={NX} y={NY + 62} textAnchor="middle" fontFamily={FONT} fontWeight={900} fontSize={112} fill={C.noteText} transform={`rotate(-4 ${NX} ${NY})`}>BESOK</text>
			</g>
			{/* left hand holds the note's lower-left corner while writing */}
			<Hand x={NX - 250 - (1 - slap) * 140 - away * 420} y={NY + 330 + (1 - slap) * 200 + away * 380} rot={32} s={1.45} pose="press" arm={900} />
			{/* right hand writes: pen tip exactly at the writing point */}
			{penOn || away > 0 ? (() => {
				const ang = 34; // pen leans down-right
				const tx = revealX + away * 420, ty = NY + 34 + jitter + away * 420;
				const dx = Math.sin((ang * Math.PI) / 180), dy = Math.cos((ang * Math.PI) / 180);
				const bx = tx + dx * 250, by = ty + dy * 250;
				const gx = tx + dx * 150, gy = ty + dy * 150; // grip point
				return (
					<g>
						<line x1={tx} y1={ty} x2={bx} y2={by} stroke="#2B3350" strokeWidth={20} strokeLinecap="round" />
						<line x1={tx} y1={ty} x2={tx + dx * 26} y2={ty + dy * 26} stroke="#11151F" strokeWidth={12} strokeLinecap="round" />
						<Hand x={gx + dx * 150} y={gy + dy * 150} rot={-ang} s={1.35} pose="fist" arm={1000} />
					</g>
				);
			})() : null}
		</Svg>
	);
};

// ---------------- S04 bedroom: note above the pillow -> sleep -> morning -> alarm starts ----------------
export const BedMorning: React.FC<{f: number; F: number}> = ({f, F}) => {
	// local 0..180 (8.0s..14.0s)
	const lower = tween(f, 0, 12, 1, 0); // arm comes down from the note
	const sleep = tween(f, 22, 60, 0, 1);
	const day = tween(f, 70, 140, 0, 1, ease.inOutSine);
	const ring = f >= 168 ? 1 : 0;
	const wake = tween(f, 170, 180, 0, 1);
	const base = lieScroll(F, {armN: [110, -40], armF: [40, 30], eyes: 1});
	const asleep: Pose = {...base, armN: [30, 40], armF: [20, 30], head: 2, eyes: 0.05, lid: 0, look: 0.3, lookY: 0};
	const reachNote: Pose = {...base, armN: [176, -8], armF: [112, -44], eyes: 1, lid: 0.1, look: -0.4, lookY: 0.2};
	let pose = mix(reachNote, base, 1 - lower);
	pose = mix(pose, asleep, sleep);
	pose = mix(pose, {...asleep, eyes: 0.5, brow: 1, head: -6}, wake);
	const z = tween(f, 0, 180, 1.42, 1.3, ease.inOutSine);
	return (
		<Svg>
			<Cam x={470} y={1010} z={z}>
				<Room f={F} day={day} notes={1} calValue={1} alarm={{ring, hour: 7}} beam={0.8 + day * 0.4} front={<Blanket />}>
					<FaceGlow x={LIE.x - 150} y={LIE.y - 90} a={(1 - sleep) * 0.7} />
					<Stickman x={LIE.x} y={LIE.y} rot={-90} pose={pose} scale={RS} handProp={sleep < 0.5 ? <PhoneInHand lit={1 - sleep * 2} /> : undefined} />
					{sleep >= 0.5 ? <rect x={LIE.x - 20} y={LIE.y - 30} width={58} height={10} rx={3} fill="#05080F" transform={`rotate(-8 ${LIE.x} ${LIE.y})`} /> : null}
				</Room>
				{/* zzz while asleep */}
				{['z', 'z', 'Z'].map((s, i) => {
					const t = ((f - 40 - i * 14) % 60) / 60;
					const op = f > 40 && f < 165 ? Math.sin(Math.PI * clamp(t)) * sleep : 0;
					return <text key={i} x={LIE.x - 160 + i * 18 + t * 30} y={LIE.y - 120 - t * 90} fill="#C8D4F0" opacity={op * 0.8} fontFamily={FONT} fontWeight={900} fontSize={26 + i * 6}>{s}</text>;
				})}
			</Cam>
		</Svg>
	);
};

// ---------------- S05 alarm close-up, slapped off at 16.1s ----------------
export const AlarmClose: React.FC<{f: number; F: number}> = ({f, F}) => {
	// local 0..69 (14.0s..16.3s); slap at local 63 (16.1s)
	const slap = 63;
	const ring = f < slap ? 1 : 0;
	const d = keys(f, [[0, 0], [52, 0], [61, 0.92], [63, 1], [69, 0.97]], ease.in);
	const squash = f >= slap ? 1 - 0.1 * Math.exp(-(f - slap) / 2.5) : 1;
	return (
		<Svg>
			<defs>
				<radialGradient id="acbg" cx="50%" cy="42%" r="72%">
					<stop offset="0%" stopColor="#5A6F9E" />
					<stop offset="55%" stopColor="#2E3D62" />
					<stop offset="100%" stopColor="#141C30" />
				</radialGradient>
			</defs>
			<rect width={1080} height={1920} fill="url(#acbg)" />
			<rect x={0} y={1180} width={1080} height={740} fill={C.table} />
			<rect x={0} y={1180} width={1080} height={14} fill="#3A4260" />
			<ellipse cx={540} cy={1192} rx={230} ry={26} fill="rgba(0,0,0,0.25)" />
			<g transform={`translate(540 1180) scale(${2 - squash} ${squash}) translate(-540 -1180)`}>
				<AlarmClock x={540} y={1060} s={3.2} ring={ring} f={F} hour={7} />
			</g>
			<Hand x={lerp(1300, 660, d)} y={lerp(-300, 700, d)} rot={208} s={1.7} pose={f >= slap - 2 ? 'press' : 'open'} arm={1200} />
		</Svg>
	);
};

// ---------------- S06 phone grabbed again (same phone design as the hook) ----------------
const GRAB_SWIPES = [34, 46, 58];
export const PhoneGrab: React.FC<{f: number}> = ({f}) => {
	// local 0..66 (16.3s..18.5s)
	const rise = tween(f, 0, 16, 1, 0, ease.out);
	const wake = tween(f, 6, 14, 0, 1);
	return (
		<Svg bg="#060B15">
			<defs>
				<radialGradient id="pgbg" cx="55%" cy="58%" r="75%">
					<stop offset="0%" stopColor="#2A4061" />
					<stop offset="50%" stopColor="#182A44" />
					<stop offset="100%" stopColor="#08101C" />
				</radialGradient>
			</defs>
			<rect width={1080} height={1920} fill="url(#pgbg)" />
			<Phone cx={560} cy={1130 + rise * 900} w={560} rot={6 + rise * 12} scroll={scrollFrom(f, GRAB_SWIPES)} screen={wake} notif={tween(f, 10, 18, 0, 1) * (1 - tween(f, 30, 36, 0, 1))} thumbY={thumbFrom(f, GRAB_SWIPES)} />
		</Svg>
	);
};

// ---------------- S07 montage: hours, days, months pass; he does not move ----------------
export const Montage: React.FC<{f: number; F: number}> = ({f, F}) => {
	// local 0..375 (18.5s..31.0s)
	// day/night cycling: speed ramps up then settles (kept below ~2 Hz)
	let phase = 0;
	for (let k = 0; k < f; k++) {
		const sp = k < 40 ? 0.03 : k < 260 ? lerp(0.03, 0.4, clamp((k - 40) / 160)) : lerp(0.4, 0.0, clamp((k - 260) / 90));
		phase += sp;
	}
	const amp = f < 330 ? 0.38 : lerp(0.38, 0, clamp((f - 330) / 30));
	const day = clamp(0.5 + amp * Math.sin(phase) - (f > 340 ? (f - 340) / 70 : 0));
	const cal = calendarAt(F);
	const z = tween(f, 0, 375, 1.36, 1.2, ease.inOutSine);
	const hourSpin = (F * (f < 300 ? 1 + f / 40 : 8 - (f - 300) / 12)) % 12;
	const pose = lieScroll(F, {eyes: 0.8, lid: 0.45});
	return (
		<Svg>
			<Cam x={480} y={960} z={z}>
				<Room f={F} day={day} notes={notesAt(F)} calValue={cal.value} calPrev={cal.prev} calTear={cal.tear ?? 0}
					alarm={{ring: 0, hour: hourSpin}} beam={0.7 + day * 0.5} front={<Blanket />}>
					<FaceGlow x={LIE.x - 150} y={LIE.y - 90} a={0.75} />
					<Stickman x={LIE.x} y={LIE.y} rot={-90} pose={pose} scale={RS} handProp={<PhoneInHand lit={1} />} />
				</Room>
			</Cam>
		</Svg>
	);
};

export const noteFirst = NOTES[0];
export {StickyNote};
