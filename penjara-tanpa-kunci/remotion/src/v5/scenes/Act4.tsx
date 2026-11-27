import React from 'react';
import {AbsoluteFill} from 'remotion';
import {FONT} from '../../fonts';
import {Bench, Buildings, CUBE, Cube, Ground, Lamp, PLAZA_TOP, Sky, Tree, WALK} from '../components/City';
import {FaceClose} from '../components/FaceClose';
import {Hand} from '../components/Hand';
import {Hud} from '../components/Hud';
import {BED, CAL, DOOR, FLOOR, Room, StickyNote, VENT} from '../components/Room';
import {P0, Pose, STAND_HIP, Stickman, idle, mix, sit, walk} from '../components/Stickman';
import {blink, clamp, ease, env, keys, lerp, rand, tween} from '../lib/anim';
import {C} from '../theme';
import {Cam, RS, Svg} from './Act1';
import {Icon, ThoughtBubble} from './Act2';

const SITH = {x: 330, y: 1186}; // sitting at the head end of the bed
const STAND_Y = FLOOR - STAND_HIP * RS;
const night = {day: 0, notes: 170, calValue: 412};

// ---------------- S31 looking around: bed, wall, vent, door ----------------
const SPOTS: [number, {x: number; y: number; r: number}][] = [
	[3300, {x: 420, y: 1180, r: 280}], // ranjang
	[3342, {x: 420, y: 1180, r: 280}],
	[3356, {x: CAL.x + 60, y: 720, r: 330}], // dinding yang sunyi
	[3387, {x: CAL.x + 60, y: 720, r: 330}],
	[3398, {x: VENT.x + VENT.w / 2, y: VENT.y + VENT.h / 2, r: 170}], // ventilasi kecil
	[3426, {x: VENT.x + VENT.w / 2, y: VENT.y + VENT.h / 2, r: 170}],
	[3440, {x: (DOOR.x0 + DOOR.x1) / 2, y: 990, r: 320}], // dan sebuah pintu
];
export const LookAround: React.FC<{f: number; F: number}> = ({f, F}) => {
	const sx = keys(F, SPOTS.map(([k, v]) => [k, v.x]), ease.inOut);
	const sy = keys(F, SPOTS.map(([k, v]) => [k, v.y]), ease.inOut);
	const sr = keys(F, SPOTS.map(([k, v]) => [k, v.r]), ease.inOut);
	const sa = Math.min(tween(F, 3290, 3306, 0, 1), 1 - tween(F, 3486, 3498, 0, 1));
	const lookX = clamp((sx - SITH.x) / 400, -1, 1), lookY = clamp((sy - (SITH.y - 150)) / 400, -1, 1);
	const pose = sit(F, {torso: 6, head: lookY * 14 - 4, eyes: blink(F, 13), lid: 0.15, look: lookX, lookY, brow: 0.3, armN: [40, 40]});
	return (
		<Svg>
			<Cam x={520} y={950} z={tween(f, 0, 276, 1.28, 1.34, ease.inOutSine)}>
				<Room f={F} {...night} bars={1} beam={0.55} phoneOnTable={false} spot={sa > 0 ? {x: sx, y: sy, r: sr, a: sa} : null}>
					<Stickman x={SITH.x} y={SITH.y} pose={pose} scale={RS} shadow={FLOOR}
						handProp={<g transform="rotate(90)"><rect x={-30} y={-5} width={60} height={10} rx={3} fill="#05080F" /></g>} />
				</Room>
			</Cam>
		</Svg>
	);
};

// ---------------- S32 "Untuk pertama kalinya, ia mempertanyakan sesuatu" ----------------
export const Question: React.FC<{f: number; F: number}> = ({f, F}) => {
	const q = tween(F, 3530, 3575, 0, 1, ease.out); // "?" drawn
	const pose = sit(F, {torso: 4, head: -6, eyes: blink(F, 17), brow: 0.6, look: 0.6, lookY: -0.3, armN: [40, 40]});
	const hx = SITH.x + 20, hy = SITH.y - 300;
	return (
		<Svg>
			<Cam x={tween(f, 0, 178, 520, 420)} y={tween(f, 0, 178, 950, 1000)} z={tween(f, 0, 178, 1.34, 1.7, ease.inOut)}>
				<Room f={F} {...night} bars={1} beam={0.55}>
					<Stickman x={SITH.x} y={SITH.y} pose={pose} scale={RS} shadow={FLOOR} />
				</Room>
				{q > 0 ? (
					<g transform={`translate(${hx} ${hy - 40 * q})`} opacity={clamp(q * 2)}>
						<circle r={70} fill="#F2C14E" opacity={0.12 + 0.06 * Math.sin(F / 8)} />
						<path d="M -26 -30 Q -24 -62 4 -62 Q 34 -60 32 -30 Q 30 -10 6 2 L 4 22" stroke="#F2C14E" strokeWidth={14} fill="none" strokeLinecap="round" strokeDasharray={200} strokeDashoffset={200 * (1 - q)} />
						<circle cx={4} cy={50} r={9} fill="#F2C14E" opacity={clamp((q - 0.8) * 5)} />
					</g>
				) : null}
			</Cam>
		</Svg>
	);
};

// ---------------- S33 "Apakah aku benar-benar tidak mampu ...? Atau ... mencari alasan ...?" ----------------
export const QuestionsBubble: React.FC<{f: number; F: number}> = ({f, F}) => {
	const b = tween(F, 3680, 3696, 0, 1);
	const left = tween(F, 3684, 3696, 0, 1), right = tween(F, 3801, 3813, 0, 1);
	const truth = tween(F, 3860, 3890, 0, 1); // the second question rings true
	const pose = sit(F, {torso: 10, head: 4, eyes: blink(F, 19), brow: 0.8, look: 0.2, lookY: -0.4, armN: [40, 40]});
	return (
		<Svg>
			<Cam x={500} y={900} z={1.45}>
				<Room f={F} {...night} bars={1} beam={0.45}>
					<Stickman x={SITH.x} y={SITH.y} pose={pose} scale={RS} shadow={FLOOR} />
				</Room>
				<ThoughtBubble x={540} y={620} w={600} h={360} p={b} from={[SITH.x + 30, SITH.y - 300]}>
					<line x1={0} y1={-120} x2={0} y2={120} stroke="#C5CDDC" strokeWidth={4} strokeDasharray="10 10" opacity={right} />
					<g transform={`translate(${right > 0 ? -140 : 0} 0) scale(${ease.outBack(left)})`} opacity={1 - truth * 0.55}>
						<g transform="translate(0 -58)">
							<rect x={-28} y={-6} width={56} height={46} rx={8} fill="#3A4256" />
							<path d="M -18 -6 L -18 -26 Q -18 -46 0 -46 Q 18 -46 18 -26 L 18 -6" stroke="#3A4256" strokeWidth={9} fill="none" />
						</g>
						<text y={30} textAnchor="middle" fontFamily={FONT} fontWeight={900} fontSize={32} fill="#2B3146">TIDAK</text>
						<text y={68} textAnchor="middle" fontFamily={FONT} fontWeight={900} fontSize={32} fill="#2B3146">MAMPU?</text>
					</g>
					<g transform={`translate(140 0) scale(${ease.outBack(right) * (1 + truth * 0.12)})`}>
						<g transform="translate(-8 -62) rotate(-8)">
							<rect x={-34} y={-24} width={68} height={42} rx={8} fill="#F7F2E8" stroke="#3A4256" strokeWidth={4} />
							<rect x={-22} y={-34} width={68} height={42} rx={8} fill="#F7F2E8" stroke="#3A4256" strokeWidth={4} />
						</g>
						<text y={30} textAnchor="middle" fontFamily={FONT} fontWeight={900} fontSize={32} fill={truth > 0.5 ? '#B8860B' : '#2B3146'}>MENCARI</text>
						<text y={68} textAnchor="middle" fontFamily={FONT} fontWeight={900} fontSize={32} fill={truth > 0.5 ? '#B8860B' : '#2B3146'}>ALASAN?</text>
					</g>
				</ThoughtBubble>
			</Cam>
		</Svg>
	);
};

// ---------------- S34 face: "Ia terdiam." ----------------
export const FaceSilent: React.FC<{f: number}> = ({f}) => (
	<FaceClose f={f} bg="night" glow="moon" expr={{eyes: blink(f + 30, 2, 60), lid: 0.2, brow: 0.5, look: 0, lookY: 0.1}} push={tween(f, 0, 77, 0.0, 0.05)} />
);

// ---------------- S35 door: "Tidak ada jawaban yang langsung mengubah segalanya." ----------------
export const DoorNoAnswer: React.FC<{f: number; F: number}> = ({f, F}) => (
	<Svg>
		<Cam x={790} y={980} z={tween(f, 0, 126, 2.0, 2.35, ease.inOutSine)}>
			<Room f={F} {...night} bars={1} beam={0.4} />
		</Cam>
	</Svg>
);

// ---------------- S36 face: fear + doubt, hand on chin ----------------
export const FaceDoubt: React.FC<{f: number}> = ({f}) => (
	<FaceClose f={f} bg="nightNotes" glow="moon" chinHand={tween(f, 40, 60, 0, 1, ease.out)} expr={{eyes: blink(f, 8) * 0.9, lid: 0.3, brow: 1, look: tween(f, 20, 60, 0, -0.7), lookY: 0.6}} push={tween(f, 0, 114, 0.02, 0.07)} />
);

// ---------------- S37 no promise this time: pen stops, 412 struck at 144.2s ----------------
export const NoPromise: React.FC<{f: number; F: number}> = ({f, F}) => {
	// local 0..150 (141.4s..146.4s)
	const NX = 560, NY = 820;
	const approach = tween(F, 4242, 4275, 0, 1, ease.out);
	const hover = F >= 4275 && F < 4300 ? Math.sin(F * 1.4) * 5 : 0;
	const retreat = tween(F, 4296, 4312, 0, 1, ease.inOut);
	const hudT = tween(F, 4311, 4323, 0, 1, ease.inOut);
	const strike = tween(F, 4325, 4328, 0, 1);
	const punch = F >= 4326 ? 1 + 0.08 * Math.exp(-(F - 4326) / 4) : 1;
	const sub = tween(F, 4334, 4344, 0, 1);
	const tx = NX + 40 - (1 - approach) * 260 + retreat * 360, ty = NY + 40 + hover + (1 - approach) * 320 + retreat * 420;
	const ang = 34, dx = Math.sin((ang * Math.PI) / 180), dy = Math.cos((ang * Math.PI) / 180);
	return (
		<AbsoluteFill>
			<Svg>
				<defs>
					<linearGradient id="npwall" x1="0" y1="0" x2="0" y2="1">
						<stop offset="0%" stopColor="#1C2640" />
						<stop offset="100%" stopColor="#121A2D" />
					</linearGradient>
				</defs>
				<rect width={1080} height={1920} fill="url(#npwall)" />
				{/* old notes all around, one new blank note in the middle */}
				{Array.from({length: 22}).map((_, i) => (
					<StickyNote key={i} x={rand(i * 4.1) * 1080} y={200 + rand(i * 2.7) * 1300} r={(rand(i) - 0.5) * 20} s={2.6} w={['besok', 'nanti', 'lusa'][i % 3]} op={0.45} />
				))}
				<g transform={`translate(${NX} ${NY}) rotate(-3)`}>
					<rect x={-200} y={-196} width={410} height={410} rx={14} fill="rgba(0,0,0,0.3)" />
					<rect x={-210} y={-210} width={420} height={420} rx={14} fill={C.note} />
					<rect x={-210} y={-210} width={420} height={56} rx={14} fill={C.noteDark} opacity={0.6} />
				</g>
				<line x1={tx} y1={ty} x2={tx + dx * 250} y2={ty + dy * 250} stroke="#2B3350" strokeWidth={20} strokeLinecap="round" />
				<line x1={tx} y1={ty} x2={tx + dx * 26} y2={ty + dy * 26} stroke="#11151F" strokeWidth={12} strokeLinecap="round" />
				<Hand x={tx + dx * 300} y={ty + dy * 300} rot={-ang} s={1.35} pose="fist" arm={1000} />
				<rect width={1080} height={1920} fill="#05070D" opacity={hudT * 0.55} />
			</Svg>
			<Hud value={412} x={lerp(700, 383, hudT)} y={lerp(200, 700, hudT)} scale={lerp(1, 1.55, hudT) * punch} struck={strike} sub="TIDAK ADA JANJI BESOK" subOpacity={sub} />
		</AbsoluteFill>
	);
};

// ---------------- S38 phone down, get up, walk slowly to the door ----------------
export const RiseWalk: React.FC<{f: number; F: number}> = ({f, F}) => {
	// local 0..219 (146.4s..153.7s)
	const put = env(F, 4398, 4444, 12, 10); // lean to the table
	const phoneDown = F >= 4426;
	const up = tween(F, 4446, 4478, 0, 1, ease.inOut);
	const antic = env(F, 4442, 4460, 6, 8);
	const wT = clamp((F - 4488) / 96);
	const x = lerp(SITH.x + 10, 640, ease.inOut(wT));
	let pose: Pose = sit(F, {torso: 8, head: 2, eyes: blink(F, 3), brow: 0.4, look: 0.4});
	pose = mix(pose, {...pose, torso: -30, head: -10, armN: [160, 8], look: -0.8, lookY: 0.6}, put);
	pose = mix(pose, {...pose, torso: 32, head: 12}, antic * 0.8);
	pose = mix(pose, idle(F, {eyes: 1, brow: 0.2, look: 0.8}), up);
	if (wT > 0 && wT < 1) pose = mix(pose, walk(F * 0.19, 0.65, {eyes: 1, brow: 0.2, look: 0.9}), Math.min(1, Math.sin(Math.PI * wT) * 3));
	const y = lerp(SITH.y, STAND_Y, up) + (wT > 0 && wT < 1 ? pose.bob * RS : 0);
	const cx = keys(F, [[4392, 420], [4488, 440], [4584, 640], [4611, 690]], ease.inOut);
	const z = keys(F, [[4392, 1.32], [4488, 1.32], [4611, 1.62]], ease.inOut);
	return (
		<Svg>
			<Cam x={cx} y={keys(F, [[4392, 980], [4611, 1000]])} z={z}>
				<Room f={F} {...night} bars={1} beam={0.6} phoneOnTable={phoneDown}>
					<Stickman x={x} y={y} pose={{...pose, bob: 0}} scale={RS} shadow={FLOOR}
						handProp={!phoneDown ? <g transform="rotate(90)"><rect x={-30} y={-5} width={60} height={10} rx={3} fill="#05080F" /></g> : undefined} />
				</Room>
			</Cam>
		</Svg>
	);
};

// ---------------- S39 hand through the bars turns the handle ----------------
export const HandleClose: React.FC<{f: number}> = ({f}) => {
	// local 0..78 (153.7s..156.3s)
	const reach = tween(f, 2, 22, 0, 1, ease.out);
	const turn = tween(f, 36, 57, 0, 1, ease.inOut);
	const HX = 520, HY = 980;
	return (
		<Svg>
			<defs>
				<linearGradient id="hcDoor" x1="0" y1="0" x2="1" y2="0">
					<stop offset="0%" stopColor="#2B3654" />
					<stop offset="100%" stopColor="#222B44" />
				</linearGradient>
			</defs>
			<rect width={1080} height={1920} fill="url(#hcDoor)" />
			<rect x={140} y={260} width={800} height={520} rx={26} fill={C.doorPanel} />
			<rect x={140} y={1180} width={800} height={600} rx={26} fill={C.doorPanel} />
			{/* handle */}
			<g transform={`translate(${HX} ${HY})`}>
				<circle r={58} fill="#9C7F3F" />
				<g transform={`rotate(${turn * 42})`}>
					<rect x={-24} y={-22} width={250} height={44} rx={22} fill={C.handle} />
					<rect x={-24} y={-22} width={250} height={12} rx={6} fill="#E6C77E" />
				</g>
				<circle r={40} fill={C.handle} />
			</g>
			{/* hand comes between the bars and grips */}
			<Hand x={lerp(1260, 760, reach) + turn * 10} y={lerp(1500, 1150, reach) + turn * 90} rot={-58 + turn * 34} s={1.55} pose={f > 26 ? 'fist' : 'open'} arm={1100} />
			{/* bars in front */}
			{[60, 300, 640, 880].map((x, i) => <rect key={i} x={x} y={-20} width={66} height={1960} rx={18} fill={C.bars} />)}
			<rect x={-20} y={560} width={1120} height={60} rx={18} fill={C.bars} />
			<rect x={-20} y={1420} width={1120} height={60} rx={18} fill={C.bars} />
			{turn > 0.95 ? [0, 1, 2].map((i) => <line key={i} x1={HX + 120 + i * 30} y1={HY - 120 - i * 10} x2={HX + 150 + i * 36} y2={HY - 160 - i * 14} stroke="#F2C14E" strokeWidth={6} strokeLinecap="round" opacity={1 - (f - 57) / 21} />) : null}
		</Svg>
	);
};

// ---------------- S40 the bars were never locks ----------------
export const Realize: React.FC<{f: number; F: number}> = ({f, F}) => {
	const fall = tween(F, 4700, 4770, 0, 1, ease.in);
	const surprise = tween(F, 4706, 4716, 0, 1, ease.out);
	const pose = idle(F, {armN: [96, 20], eyes: 1, look: lerp(0.8, -0.2, surprise), lookY: lerp(0, 0.5, surprise), brow: -0.2 + surprise * 1.0, mouth: surprise * -0.2, head: surprise * 8});
	return (
		<Svg>
			<Cam x={tween(f, 0, 120, 700, 680)} y={1000} z={tween(f, 0, 120, 1.62, 1.5)}>
				<Room f={F} {...night} bars={1} barsDissolve={fall} doorGlow={tween(F, 4740, 4800, 0, 0.9)} beam={0.6}>
					<Stickman x={640} y={STAND_Y} pose={pose} scale={RS} shadow={FLOOR} />
				</Room>
			</Cam>
		</Svg>
	);
};

// ---------------- S41 "Pintu tersebut tidak terkunci." door swings open, light floods in ----------------
export const DoorOpens: React.FC<{f: number; F: number}> = ({f, F}) => {
	const open = tween(F, 4836, 4884, 0, 1, ease.inOut);
	const flash = tween(F, 4906, 4929, 0, 1, ease.in);
	const shield = tween(F, 4900, 4912, 0, 1, ease.out);
	const pose = idle(F, {armN: [96 - shield * 0 + shield * 70, 20 + shield * 60], eyes: 1 - shield * 0.6, look: 0.8, brow: shield * 0.4, mouth: open * 0.3});
	return (
		<Svg>
			<Cam x={tween(f, 0, 120, 700, 720)} y={990} z={tween(f, 0, 120, 1.5, 1.7, ease.inOut)}>
				<Room f={F} {...night} bars={1} barsDissolve={1} doorOpen={open} doorGlow={0.9} beam={0.6}>
					<Stickman x={640} y={STAND_Y} pose={pose} scale={RS} shadow={FLOOR} glow={open > 0.2 ? '#FFF1C7' : undefined} />
				</Room>
			</Cam>
			<rect width={1080} height={1920} fill="#FFF7DE" opacity={flash * 0.85} />
		</Svg>
	);
};

// ---------------- shared outdoor world for the ending ----------------
const OutWorld: React.FC<{F: number; camX: number; heroX: number; heroPose: Pose; heroFacing?: 1 | -1; children?: React.ReactNode; notesOut?: number}> = ({F, camX, heroX, heroPose, heroFacing = 1, children, notesOut = 0}) => {
	const FEET = 1392;
	const dX0 = CUBE.x0 + 0.65 * (CUBE.x1 - CUBE.x0), dX1 = CUBE.x0 + 0.81 * (CUBE.x1 - CUBE.x0);
	return (
		<g>
			<Sky camX={camX} F={F} />
			<Buildings camX={camX} />
			{[700, 1060, 1450, 1820, 2230].map((x, i) => <Tree key={x} x={x} F={F} seed={i} s={0.95 + (i % 2) * 0.12} />)}
			<Lamp x={1240} y={WALK + 6} />
			<Ground camX={camX} />
			<Cube doorOpen={1} doorLight={1} />
			<Bench x={905} y={WALK - 2} />
			<Stickman x={905} y={WALK - 60} pose={sit(F, {torso: 16, head: 10, armN: [58, 62], armF: [52, 66], lookY: 0.4})} facing={-1} scale={0.92} shirt="#F08A3E" />
			<Stickman x={1515} y={FEET - STAND_HIP} pose={idle(F, {mouth: 0.6, look: 0.6})} shirt="#8B6CD6" shadow={FEET} />
			<Stickman x={1650} y={FEET - STAND_HIP} pose={idle(F + 7, {mouth: 0.6, look: 0.6})} facing={-1} shirt="#E5534B" shadow={FEET} />
			{/* notes blowing out of the door and away */}
			{notesOut > 0
				? Array.from({length: 26}).map((_, i) => {
					const t = clamp(notesOut * 1.4 - rand(i) * 0.4);
					if (t <= 0) return null;
					const x = lerp((dX0 + dX1) / 2, (dX0 + dX1) / 2 + 300 + rand(i * 3) * 1300, ease.out(t));
					const y = lerp(1290, 200 + rand(i * 5) * 600, ease.out(t)) + Math.sin(t * 9 + i) * 30;
					return <StickyNote key={i} x={x} y={y} r={t * 400 * (rand(i) - 0.5)} s={0.9} op={1 - clamp((t - 0.7) / 0.3)} w={['besok', 'nanti', 'lusa'][i % 3]} />;
				})
				: null}
			<Stickman x={heroX} y={FEET - STAND_HIP + heroPose.bob} pose={{...heroPose, bob: 0}} facing={heroFacing} shadow={FEET} />
			{children}
		</g>
	);
};

// ---------------- S42 step out: "Di hadapannya terbentang dunia yang luas," ----------------
export const StepOut: React.FC<{f: number; F: number}> = ({f, F}) => {
	const w = clamp((F - 4936) / 60);
	const heroX = lerp(440, 640, ease.inOut(w));
	const pose = w > 0 && w < 1 ? walk(F * 0.26, 0.9, {eyes: 1, look: 0.4, lookY: -0.3, mouth: 0.3}) : idle(F, {eyes: 1, look: 0.6, lookY: -0.4, mouth: 0.4, head: -8});
	const camX = tween(f, 0, 111, 470, 640, ease.inOut);
	return (
		<Svg>
			<Cam x={camX} y={tween(f, 0, 111, 1060, 960, ease.inOut)} z={tween(f, 0, 111, 1.3, 1.0, ease.inOut)}>
				<OutWorld F={F} camX={camX} heroX={heroX} heroPose={pose} notesOut={tween(F, 4944, 5040, 0, 1)} />
			</Cam>
			<rect width={1080} height={1920} fill="#FFF7DE" opacity={0.85 * (1 - tween(F, 4929, 4948, 0, 1, ease.out))} />
		</Svg>
	);
};

// ---------------- S43 possibilities (and uncertainty) branch out from his feet ----------------
const PATHS = [
	{to: [905, 1250], icon: 'book'},
	{to: [1395, 1180], icon: 'laptop'},
	{to: [1585, 1150], icon: 'people'},
	{to: [1150, 820], icon: 'bike'},
	{to: [380, 700], icon: 'mountain'},
	{to: [1700, 760], icon: '?'},
	{to: [130, 900], icon: '?'},
];
const PathIcon: React.FC<{k: string}> = ({k}) => (
	<g>
		<circle r={38} fill="#FFF7DE" stroke="#F2C14E" strokeWidth={5} />
		{k === 'book' ? <g transform="scale(0.6)"><Icon k="book" /></g> : null}
		{k === 'laptop' ? <g transform="translate(0 16) scale(0.5)"><rect x={-40} y={-70} width={80} height={56} rx={6} fill="#3A4256" /><rect x={-48} y={-14} width={96} height={10} rx={4} fill="#8A93A8" /></g> : null}
		{k === 'people' ? <g>{[-12, 12].map((x) => <g key={x}><circle cx={x} cy={-10} r={8} fill="#3A4256" /><rect x={x - 7} y={0} width={14} height={18} rx={6} fill="#3A4256" /></g>)}</g> : null}
		{k === 'bike' ? <g transform="scale(0.32) translate(0 40)"><circle cx={-58} cy={-34} r={30} fill="none" stroke="#3A4256" strokeWidth={10} /><circle cx={58} cy={-34} r={30} fill="none" stroke="#3A4256" strokeWidth={10} /><path d="M -58 -34 L -14 -34 L 20 -84 L 58 -34" stroke="#3A4256" strokeWidth={10} fill="none" /></g> : null}
		{k === 'mountain' ? <path d="M -24 16 L -6 -14 L 4 0 L 12 -10 L 26 16 Z" fill="#3A4256" /> : null}
		{k === '?' ? <text y={14} textAnchor="middle" fontFamily={FONT} fontWeight={900} fontSize={40} fill="#8C95A8">?</text> : null}
	</g>
);
export const Possibilities: React.FC<{f: number; F: number}> = ({f, F}) => {
	const camX = 760;
	const pose = idle(F, {eyes: 1, look: 0.7, lookY: -0.3, mouth: 0.5, head: -6});
	const from: [number, number] = [640, 1392];
	return (
		<Svg>
			<Cam x={camX} y={1000} z={tween(f, 0, 90, 0.86, 0.82)}>
				<OutWorld F={F} camX={camX} heroX={640} heroPose={pose}>
					{PATHS.map((p, i) => {
						const g = tween(F, 5046 + i * 6, 5076 + i * 6, 0, 1, ease.out);
						if (g <= 0) return null;
						const uncertain = p.icon === '?';
						const fog = uncertain ? tween(F, 5090, 5120, 0, 1) : 0;
						const [x1, y1] = p.to;
						const mx = (from[0] + x1) / 2, my = Math.min(from[1], y1) - 160;
						const d = `M ${from[0]} ${from[1]} Q ${mx} ${my} ${x1} ${y1}`;
						return (
							<g key={i} opacity={1 - fog * 0.6}>
								<path d={d} stroke="#F2C14E" strokeWidth={10} fill="none" strokeLinecap="round" strokeDasharray={uncertain ? '6 22' : '2000'} strokeDashoffset={uncertain ? -F * 2 : 2000 * (1 - g)} opacity={0.85} />
								<g transform={`translate(${x1} ${y1}) scale(${ease.outBack(clamp((g - 0.6) * 2.5))})`}><PathIcon k={p.icon} /></g>
								{uncertain && fog > 0 ? <ellipse cx={x1} cy={y1} rx={140 * fog} ry={70 * fog} fill="#F4F7FB" opacity={0.7} /> : null}
							</g>
						);
					})}
				</OutWorld>
			</Cam>
		</Svg>
	);
};

// ---------------- S44 walking on: "Ia belum tahu apa yang akan terjadi selanjutnya," ----------------
export const WalkCity: React.FC<{f: number; F: number}> = ({f, F}) => {
	const heroX = lerp(660, 1260, f / 92);
	const pose = walk(F * 0.27, 0.9, {eyes: 1, look: Math.sin(f / 14) * 0.8, lookY: -0.2, mouth: 0.4, head: Math.sin(f / 14) * 4});
	const camX = heroX + 60;
	return (
		<Svg>
			<Cam x={camX} y={1010} z={1.12}>
				<OutWorld F={F} camX={camX} heroX={heroX} heroPose={pose} />
			</Cam>
		</Svg>
	);
};

// ---------------- S45 face in daylight: "tetapi kini ia memahami satu hal:" ----------------
export const FaceUnderstand: React.FC<{f: number}> = ({f}) => (
	<FaceClose f={f} bg="day" glow="sun" expr={{eyes: blink(f, 4) * 1, lid: 0, brow: -0.1, mouth: tween(f, 20, 50, 0.2, 0.7), look: 0.2, lookY: -0.2}} push={tween(f, 0, 106, 0, 0.06)} />
);

// ---------------- S46 split: SEKADAR SADAR | MELANGKAH ----------------
export const Split: React.FC<{f: number; F: number}> = ({f, F}) => {
	const dimL = tween(f, 40, 200, 0, 0.45);
	const heroX = lerp(500, 1000, f / 201);
	const lieP: Pose = {...P0, torso: 0, head: -6, legN: [6, 10], legF: [2, 6], armN: [118, -52], armF: [112, -44], eyes: blink(F, 3) * 0.8, lid: 0.35, look: 1, lookY: -0.5};
	return (
		<AbsoluteFill>
			<svg width={1080} height={1920}>
				<defs>
					<clipPath id="spL"><rect x={0} y={0} width={538} height={1920} /></clipPath>
					<clipPath id="spR"><rect x={542} y={0} width={538} height={1920} /></clipPath>
				</defs>
				<g clipPath="url(#spL)">
					<g transform="translate(-270 0)">
						<Cam x={410} y={1060} z={1.2}>
							<Room f={F} day={0} notes={170} calValue={412} beam={0.5}>
								<Stickman x={392} y={BED.top - 18} rot={-90} pose={lieP} scale={RS} />
							</Room>
						</Cam>
					</g>
					<rect x={0} y={0} width={540} height={1920} fill="#000" opacity={dimL} />
				</g>
				<g clipPath="url(#spR)">
					<g transform="translate(270 0)">
						<Cam x={heroX} y={1010} z={1.05}>
							<OutWorld F={F} camX={heroX} heroX={heroX} heroPose={walk(F * 0.27, 0.9, {eyes: 1, mouth: 0.5, look: 0.4})} />
						</Cam>
					</g>
				</g>
				<rect x={538} y={0} width={4} height={1920} fill="#fff" opacity={0.85} />
			</svg>
			{[{x: 0, t: 'SEKADAR SADAR', c: '#AFC0E0'}, {x: 540, t: 'MELANGKAH', c: '#FFFFFF'}].map((l) => (
				<div key={l.t} style={{position: 'absolute', left: l.x, width: 540, top: 250, textAlign: 'center', fontFamily: FONT, fontWeight: 900, fontSize: 44, letterSpacing: '0.08em', color: l.c, opacity: tween(f, 6, 18, 0, 1), textShadow: '0 3px 14px rgba(0,0,0,0.6), 0 0 2px rgba(0,0,0,0.9)'}}>{l.t}</div>
			))}
		</AbsoluteFill>
	);
};

// ---------------- S47 first step over the threshold ----------------
const ik = (hx: number, hy: number, fx: number, fy: number, l1: number, l2: number, fwd = 1) => {
	const dx = fx - hx, dy = fy - hy;
	const d = Math.min(Math.hypot(dx, dy), l1 + l2 - 0.5);
	const a = Math.atan2(dy, dx);
	const c = Math.acos(Math.max(-1, Math.min(1, (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d))));
	const ang = a - fwd * c; // knee bends forward (toward +x)
	return {kx: hx + Math.cos(ang) * l1, ky: hy + Math.sin(ang) * l1};
};
const Leg: React.FC<{hx: number; hy: number; fx: number; fy: number; lit: number; toe: number}> = ({hx, hy, fx, fy, lit, toe}) => {
	const L1 = 760, L2 = 720;
	const {kx, ky} = ik(hx, hy, fx, fy - 40, L1, L2);
	const pants = lit > 0 ? `rgb(${Math.round(21 + 40 * lit)},${Math.round(26 + 44 * lit)},${Math.round(38 + 54 * lit)})` : C.pants;
	return (
		<g>
			<line x1={hx} y1={hy} x2={kx} y2={ky} stroke={pants} strokeWidth={128} strokeLinecap="round" />
			<line x1={kx} y1={ky} x2={fx} y2={fy - 40} stroke={pants} strokeWidth={112} strokeLinecap="round" />
			<line x1={kx - 40} y1={ky} x2={fx - 40} y2={fy - 40} stroke="#fff" strokeOpacity={0.05 + 0.08 * lit} strokeWidth={22} strokeLinecap="round" />
			<g transform={`translate(${fx} ${fy}) rotate(${toe})`}>
				<path d="M -70 -86 Q -76 -6 -60 0 L 150 0 Q 182 -4 176 -40 Q 166 -78 96 -84 L 20 -110 Q -40 -118 -70 -86 Z" fill={lit > 0.5 ? '#2F3A57' : '#1C2235'} />
				<rect x={-66} y={-14} width={244} height={20} rx={10} fill="#E9ECF3" />
				{[30, 62, 94].map((x) => <circle key={x} cx={x} cy={-74 + (x - 30) * 0.12} r={6} fill="#E9ECF3" opacity={0.8} />)}
			</g>
		</g>
	);
};
export const FirstStep: React.FC<{f: number; F: number}> = ({f, F}) => {
	// local 0..108 (184.3s..187.9s); front foot lands at 186.6s (F 5598)
	const step = tween(F, 5556, 5598, 0, 1, ease.inOut);
	const ring = tween(F, 5598, 5632, 0, 1, ease.out);
	const hudP = tween(F, 5598, 5606, 0, 1, ease.outBack);
	const GROUND = 1270;
	const fx = lerp(330, 700, step), fy = GROUND - Math.sin(Math.PI * step) * 170;
	const shift = lerp(0, 150, ease.inOut(step));
	const toe = Math.sin(Math.PI * step) * -12 + (step < 0.15 ? step * 60 : 0);
	const lit = clamp((fx - 380) / 120);
	return (
		<AbsoluteFill>
			<Svg>
				<defs>
					<linearGradient id="fsIn" x1="0" y1="0" x2="1" y2="0">
						<stop offset="0%" stopColor="#10151F" />
						<stop offset="100%" stopColor="#1B2337" />
					</linearGradient>
					<linearGradient id="fsSky" x1="0" y1="0" x2="0" y2="1">
						<stop offset="0%" stopColor={C.skyTop} />
						<stop offset="100%" stopColor={C.skyBottom} />
					</linearGradient>
					<linearGradient id="fsSpill" x1="0" y1="0" x2="1" y2="0">
						<stop offset="0%" stopColor="#FFF1C7" stopOpacity={0.35} />
						<stop offset="100%" stopColor="#FFF1C7" stopOpacity={0} />
					</linearGradient>
				</defs>
				{/* outside: sky, trees, sidewalk */}
				<rect width={1080} height={1920} fill="url(#fsSky)" />
				<circle cx={900} cy={260} r={150} fill="#FFF4CC" opacity={0.7} />
				{[620, 880].map((x, i) => (
					<g key={x} transform={`translate(${x} ${GROUND - 30}) scale(1.8)`}>
						<rect x={-9} y={-120} width={18} height={124} rx={6} fill={C.trunk} />
						<circle cx={0} cy={-170} r={70} fill={i ? C.treeB : C.treeA} />
					</g>
				))}
				<rect x={0} y={GROUND} width={1080} height={700} fill={C.sidewalk} />
				{Array.from({length: 10}).map((_, i) => <line key={i} x1={400 + i * 130} y1={GROUND} x2={400 + i * 130 - 90} y2={1920} stroke={C.tile} strokeWidth={6} />)}
				{/* inside the room (left) + door frame + threshold */}
				<rect x={0} y={0} width={410} height={1920} fill="url(#fsIn)" />
				<polygon points={`410,0 1080,0 1080,1920 410,1920`} fill="url(#fsSpill)" opacity={0.6} />
				<rect x={380} y={0} width={56} height={1920} fill={C.doorFrame} />
				<rect x={340} y={GROUND - 14} width={150} height={28} rx={8} fill="#6B5A44" />
				{/* back leg (weight shifts forward), then the stepping leg */}
				<Leg hx={190 + shift} hy={-330} fx={230} fy={GROUND} lit={0} toe={0} />
				<Leg hx={250 + shift} hy={-330} fx={fx} fy={fy} lit={lit} toe={toe} />
				{ring > 0 ? (
					<g opacity={1 - ring}>
						<ellipse cx={760} cy={GROUND + 6} rx={150 + ring * 330} ry={34 + ring * 80} fill="none" stroke="#F2C14E" strokeWidth={12} />
						<ellipse cx={760} cy={GROUND + 6} rx={80 + ring * 220} ry={18 + ring * 50} fill="#F2C14E" opacity={0.28} />
					</g>
				) : null}
			</Svg>
			{hudP > 0 ? <Hud label="LANGKAH NYATA" value={1} x={383} y={300} scale={hudP * 1.2} glow={tween(F, 5606, 5620, 0, 1)} /> : null}
		</AbsoluteFill>
	);
};

// ---------------- S48 end: walk into the world; no title ----------------
const FADE = (F: number) => (F < 5710 ? 1 : 1 - tween(F, 5710, 5723, 0, 1, ease.inOut));
export const EndScene: React.FC<{f: number; F: number}> = ({f, F}) => {
	const heroX = lerp(660, 900, f / 87);
	const camX = 700;
	const t = tween(f, 6, 22, 0, 1, ease.out);
	const fade = FADE(F);
	return (
		<AbsoluteFill>
			<Svg>
				<Cam x={camX} y={960} z={0.95}>
					<OutWorld F={F} camX={camX} heroX={heroX} heroPose={walk(F * 0.27, 0.9, {eyes: 1, mouth: 0.5})} />
				</Cam>
				<rect width={1080} height={1920} fill="#000" opacity={1 - fade} />
			</Svg>
			<div style={{position: 'absolute', left: 60, right: 60, top: 300, textAlign: 'center', fontFamily: FONT, fontWeight: 900, opacity: t * fade, transform: `translateY(${(1 - t) * 16}px)`}}>
				<div style={{fontSize: 64, color: '#fff', lineHeight: 1.15, textShadow: '0 4px 20px rgba(10,20,40,0.55), 0 0 2px rgba(0,0,0,0.8)'}}>Perubahan dimulai dari</div>
				<div style={{fontSize: 84, color: C.gold, lineHeight: 1.15, textShadow: '0 4px 20px rgba(10,20,40,0.55), 0 0 2px rgba(0,0,0,0.8)'}}>langkah pertama.</div>
			</div>
			<Hud value={412} x={190} y={560} scale={0.9} opacity={tween(f, 10, 20, 0, 1) * fade} struck={1} dim />
			<Hud label="LANGKAH NYATA" value={1} x={576} y={560} scale={0.9} opacity={tween(f, 14, 24, 0, 1) * fade} glow={1} />
		</AbsoluteFill>
	);
};

export {PLAZA_TOP};
