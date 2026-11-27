import React from 'react';
import {AbsoluteFill} from 'remotion';
import {FONT} from '../../fonts';
import {AlarmClock, BED, Blanket, FLOOR, NOTES, Room, StickyNote, TABLE} from '../components/Room';
import {Phone} from '../components/Phone';
import {Hand} from '../components/Hand';
import {P0, Pose, Stickman, limbEnd, mix, reachArm, toLocal, toWorld} from '../components/Stickman';
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
	const hookOp = 1 - clamp((f - 66) / 10); // fully visible from frame 0 (thumbnail)
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
			<div style={{position: 'absolute', left: 0, right: 0, top: 300, textAlign: 'center', fontFamily: FONT, fontWeight: 900, opacity: hookOp}}>
				<div style={{fontSize: 80, lineHeight: 1.08, color: '#fff', textShadow: '0 4px 24px rgba(0,0,0,0.6), 0 0 2px rgba(0,0,0,0.9)'}}>412 kali ia berkata</div>
				<div style={{fontSize: 108, lineHeight: 1.12, color: C.gold, textShadow: '0 0 30px rgba(242,193,78,0.45), 0 4px 24px rgba(0,0,0,0.6)'}}>“besok”.</div>
			</div>
		</AbsoluteFill>
	);
};

// ---------------- S02 bedroom at night: scrolling, phone down on his belly, then he reaches for the note pad ----------------
const lieAt = (dx: number) => ({x: LIE.x + dx, y: LIE.y, scale: RS, rot: -90});
const PAD = {x: 152, y: TABLE.top - 4}; // note pad on the bedside table (world)
const NOTE_SPOT = {x: 258, y: 1058}; // where his hand presses the first note (lower half of NOTES[0])
const BELLY = {x: 404, y: 1139}; // where his hand rests on his belly (world, body at LIE.x)
const armTo = (pose: Pose, wx: number, wy: number, dx: number, flip = false) => {
	const l = toLocal(wx, wy, lieAt(dx));
	return reachArm(pose, l.x, l.y, flip);
};
const mixArm = (a: [number, number], b: [number, number], t: number): [number, number] => [lerp(a[0], b[0], t), lerp(a[1], b[1], t)];

/** the phone's transform when it leaves the hand (same placement as PhoneInHand) */
const phoneWorld = (pose: Pose, dx: number) => {
	const le = limbEnd(pose, 'armN');
	const r = (le.angle * Math.PI) / 180;
	const c = toWorld(le.x + 6 * Math.cos(r), le.y - 6 * Math.sin(r), lieAt(dx));
	return {x: c.x, y: c.y, rot: -le.angle};
};
const PhoneLying: React.FC<{x: number; y: number; rot: number}> = ({x, y, rot}) => (
	<g transform={`translate(${x} ${y}) rotate(${rot}) scale(${RS})`}>
		<rect x={-30} y={-5} width={60} height={10} rx={3} fill="#05080F" />
		<rect x={-27} y={-5} width={54} height={2.2} rx={1} fill="#4A5878" />
	</g>
);
const PadInHand: React.FC = () => (
	<g transform="translate(5 0) rotate(90)">
		<rect x={-21} y={-5} width={42} height={9} rx={2} fill={C.note} />
		<rect x={-21} y={-5} width={42} height={3} rx={1.5} fill={C.noteDark} />
	</g>
);

const RELEASE = 66; // phone left on his belly
const REST_F: [number, number] = [4, 6]; // far arm relaxed along the body
const bedNightPose = (f: number, F: number) => {
	const base = lieScroll(F);
	const down = tween(f, 52, RELEASE, 0, 1, ease.inOut); // both hands come down; the near hand lays the phone on his belly
	const reach = tween(f, RELEASE + 2, 90, 0, 1, ease.inOut); // far hand reaches back over his head for the pad
	const lift = tween(f, 93, 107, 0, 1, ease.inOut); // pad carried up to the wall above the pillow
	const dx = -14 * tween(f, 62, 86, 0, 1, ease.inOut); // scoots toward the table
	const armN = mixArm(base.armN, armTo(base, BELLY.x + dx, BELLY.y, dx), down);
	let armF = mixArm(base.armF, REST_F, down);
	armF = mixArm(armF, armTo(base, PAD.x + 8, PAD.y, dx), reach);
	armF = [armF[0], armF[1] + 46 * Math.sin(Math.PI * reach)]; // elbow bends mid-sweep (no robotic straight arm)
	armF = mixArm(armF, armTo(base, NOTE_SPOT.x, NOTE_SPOT.y, dx), lift);
	const pose: Pose = {...base, armN, armF, head: lerp(base.head, -16, reach), look: lerp(1, -0.4, reach), lookY: lerp(-0.5, lerp(0.4, -0.6, lift), reach), eyes: lerp(base.eyes, 1, down), lid: lerp(0.35, 0.15, reach)};
	return {pose, dx, down, reach, lift};
};

export const BedNight: React.FC<{f: number; F: number}> = ({f, F}) => {
	// f: local frame (0..109), F: global frame
	const {pose, dx, down} = bedNightPose(f, F);
	const held = f < RELEASE;
	const rel = bedNightPose(RELEASE, 77 + RELEASE); // S02 starts at global frame 77
	const ph = phoneWorld(rel.pose, rel.dx);
	const phoneDx = dx - rel.dx; // the phone rides along when he scoots
	const grabbed = f >= 91;
	const z = tween(f, 0, 109, 1.3, 1.42, ease.inOutSine);
	return (
		<Svg>
			<Cam x={460} y={1010} z={z}>
				<Room f={F} day={0} notes={0} calValue={1} phoneOnTable={false} beam={0.8}
					front={<Blanket />}>
					{/* note pad on the bedside table */}
					{!grabbed ? <rect x={PAD.x - 20} y={PAD.y - 4} width={40} height={8} rx={2} fill={C.note} /> : null}
					<FaceGlow x={LIE.x - 150} y={LIE.y - 90} a={1 - down * 0.85} />
					{!held ? <PhoneLying x={ph.x + phoneDx} y={ph.y} rot={ph.rot} /> : null}
					<Stickman x={LIE.x + dx} y={LIE.y} rot={-90} pose={pose} scale={RS}
						handProp={held ? <PhoneInHand lit={1 - down} /> : undefined} farHandProp={grabbed ? <PadInHand /> : undefined} />
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
	// local 0..180 (8.0s..14.0s). Starts where S02 ended: hand on the new note, phone on his belly.
	const end = bedNightPose(109, 186); // pose at the end of S02 (global 186): same hand/phone placement
	const back = tween(f, 0, 16, 0, 1, ease.inOut); // hand comes down from the note, scoots back
	const dx = lerp(end.dx, 0, back);
	const sleep = tween(f, 22, 60, 0, 1);
	const day = tween(f, 70, 140, 0, 1, ease.inOutSine);
	const ring = f >= 168 ? 1 : 0;
	const wake = tween(f, 170, 180, 0, 1);
	const base = lieScroll(F, {eyes: 1, lid: 0.15, look: -0.2, lookY: -0.3});
	const onPhone = armTo(base, BELLY.x + dx, BELLY.y, dx);
	const asleep: Pose = {...base, armN: onPhone, armF: REST_F, head: 2, eyes: 0.05, lid: 0, look: 0.3, lookY: 0};
	let pose: Pose = {...base, armN: onPhone, armF: mixArm(end.pose.armF, REST_F, back)};
	pose = mix(pose, asleep, sleep);
	pose = mix(pose, {...asleep, eyes: 0.5, brow: 1, head: -6}, wake);
	const rel = bedNightPose(RELEASE, 77 + RELEASE);
	const ph = phoneWorld(rel.pose, rel.dx);
	const z = tween(f, 0, 180, 1.42, 1.3, ease.inOutSine);
	return (
		<Svg>
			<Cam x={470} y={1010} z={z}>
				<Room f={F} day={day} notes={1} calValue={1} alarm={{ring, hour: 7}} beam={0.8 + day * 0.4} front={<Blanket />}>
					<PhoneLying x={ph.x + (dx - rel.dx)} y={ph.y} rot={ph.rot} />
					<Stickman x={LIE.x + dx} y={LIE.y} rot={-90} pose={pose} scale={RS} />
				</Room>
				{/* zzz while asleep */}
				{['z', 'z', 'Z'].map((s, i) => {
					const t = ((f - 40 - i * 14) % 60) / 60;
					const op = f > 40 && f < 165 ? Math.sin(Math.PI * clamp(t)) * sleep : 0;
					return <text key={i} x={300 + i * 20 + t * 40} y={1100 - i * 8 - t * 90} fill="#C8D4F0" opacity={op * 0.8} fontFamily={FONT} fontWeight={900} fontSize={26 + i * 6}>{s}</text>;
				})}
			</Cam>
		</Svg>
	);
};

// ---------------- S05 alarm close-up: the last beep ends at 15.22 s, exactly when the hand hits it ----------------
export const AlarmClose: React.FC<{f: number; F: number}> = ({f, F}) => {
	// local 0..69 (14.0s..16.3s); slap at local 37 (15.22s, end of the last beep), second press at 51 (click at 15.69s)
	const slap = 37, click = 51;
	const ring = f < slap ? 1 : 0;
	let d: number;
	if (f < 30) d = tween(f, 10, 30, 0, 0.8, ease.out); // sleepy hand gropes in from the top right
	else if (f < slap) d = tween(f, 30, slap, 0.8, 1, ease.in); // ...and slaps the button
	else if (f < click) d = 1 - 0.035 * Math.sin(Math.PI * clamp((f - slap) / (click - slap)));
	else d = tween(f, 58, 69, 1, 0.62, ease.in); // leaves for the phone
	const hit = (at: number, k: number) => (f >= at ? k * Math.exp(-(f - at) / 2.5) : 0);
	const squash = 1 - hit(slap, 0.1) - hit(click, 0.04);
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
			<Hand x={lerp(1300, 660, d)} y={lerp(-300, 700, d)} rot={208} s={1.7} pose={f >= slap - 2 && f < 60 ? 'press' : 'open'} arm={1200} />
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
