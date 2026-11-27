import React from 'react';
import {FONT} from '../../fonts';
import {Bench, Bicycle, Bird, Box, Buildings, CUBE, Car, Cube, Flower, Fog, Ground, Lamp, Laptop, PLAZA_TOP, RainCloud, Signpost, Sky, Stairs, Tree, Umbrella, WALK, CURB} from '../components/City';
import {FaceClose} from '../components/FaceClose';
import {Phone} from '../components/Phone';
import {P0, Pose, STAND_HIP, Stickman, idle, limbEnd, mix, sit, walk} from '../components/Stickman';
import {Icon, ThoughtBubble} from './Act2';
import {blink, clamp, ease, env, keys, lerp, tween} from '../lib/anim';
import {C} from '../theme';
import {Cam, Svg, scrollFrom, thumbFrom} from './Act1';

const S = 1.0; // people scale on the sidewalk
const FEET = 1392; // sidewalk front line where people walk
const standY = (feet: number, s = S) => feet - STAND_HIP * s;

const squat = (f: number, extra: Partial<Pose> = {}): Pose => ({...P0, torso: 34, head: 10, legN: [96, 150], legF: [86, 140], armN: [70, 40], armF: [60, 50], bob: 0, ...extra});

// stairs + upper platform
const ST = {x0: 3150, y0: WALK, x1: 3430, y1: 1010, n: 12};
const PLAZA_FEET = 1290; // people standing on the plaza (behind the sidewalk)
/** hip height so the lowest foot touches the ground (no floating squats) */
const grounded = (pose: Pose, feetY: number, s: number) => {
	const a = limbEnd(pose, 'legN'), b = limbEnd(pose, 'legF');
	return feetY - (Math.max(a.y, b.y) + 10.5) * s;
};
const stairPos = (u: number) => {
	// u: 0..12 steps climbed (fractional)
	const run = (ST.x1 - ST.x0) / ST.n, rise = (ST.y0 - ST.y1) / ST.n;
	const k = Math.floor(clamp(u, 0, ST.n));
	const fr = u - k;
	const x = ST.x0 - 30 + (k + ease.inOut(clamp(fr))) * run;
	const y = ST.y0 - (k + (fr > 0.4 ? 1 : fr / 0.4)) * rise;
	return {x, y};
};

/** the whole city: everything lives in one world so nobody pops in; F = global frame */
const CityWorld: React.FC<{F: number; camX: number}> = ({F, camX}) => {
	// ---- reader (belajar) ----
	const rWalk = clamp((F - 2395) / 70);
	const rx = lerp(1180, 905, ease.inOut(rWalk));
	const rSit = tween(F, 2466, 2480, 0, 1, ease.inOut);
	let rPose: Pose = F < 2466 ? walk(F * 0.28, rWalk > 0 && rWalk < 1 ? 1 : 0.0, {}) : idle(F);
	rPose = mix(rPose, sit(F, {torso: 16, head: F > 2494 ? 22 : 6, armN: [58, 62], armF: [52, 66], lookY: F > 2494 ? 0.8 : 0, eyes: blink(F, 21)}), rSit);
	const bookOpen = tween(F, 2486, 2494, 0, 1);
	// ---- worker (bekerja) ----
	const typing = Math.sin(F * 1.3) * 6;
	const wPose = sit(F, {torso: 10, head: 6, armN: [64, 56 + typing], armF: [58, 60 - typing], lookY: 0.4, eyes: blink(F, 23)});
	// ---- friends (bertemu) ----
	const meet = clamp((F - 2520) / 46);
	const ax = lerp(1385, 1515, ease.out(meet)), bx = lerp(1760, 1650, ease.out(meet));
	const wave = env(F, 2562, 2576, 3, 3), five = env(F, 2576, 2596, 3, 6);
	const fA = mix(mix(meet < 1 ? walk(F * 0.3, 1 - meet * 0.3) : idle(F, {mouth: 0.6}), {...idle(F), armN: [160 + Math.sin(F) * 14, 20], mouth: 0.7}, wave), {...idle(F), armN: [150, 10], torso: 6, mouth: 0.8}, five);
	const fB = mix(mix(meet < 1 ? walk(F * 0.3 + 1, 1 - meet * 0.3) : idle(F + 9, {mouth: 0.6}), {...idle(F), armN: [160 + Math.sin(F + 1) * 14, 20], mouth: 0.7}, wave), {...idle(F), armN: [150, 10], torso: 6, mouth: 0.8}, five);
	// ---- cyclist (gagal -> mencoba kembali) ----
	const ROCK = 2075;
	const ride1 = clamp((F - 2556) / 62); // to the rock
	const fall = clamp((F - 2618) / 14);
	const lie = F >= 2632 && F < 2678;
	const getUp = clamp((F - 2678) / 18);
	const lift = clamp((F - 2700) / 12);
	const t2 = Math.max(0, F - 2714);
	const ride2X = t2 < 25 ? 0.44 * t2 * t2 : 275 + 22 * (t2 - 25); // accelerates to ~22 px/frame
	let bx1 = lerp(1860, ROCK - 58, ease.linear(ride1));
	const wob = F < 2618 ? Math.sin(F * 0.5) * 7 * (1 - ride1 * 0.3) : 0;
	let bikeRot = wob + fall * 78 - lift * 78;
	if (F >= 2714) bx1 = ROCK - 40 + ride2X;
	if (F >= 2714) bikeRot = Math.sin(F * 0.3) * 1.2;
	const pedal = F * 0.4;
	const riding = F < 2618 || F >= 2714;
	const ridePose: Pose = {...P0, torso: 24, head: -6, legN: [70 + Math.sin(pedal) * 26, 70 + Math.cos(pedal) * 24], legF: [70 - Math.sin(pedal) * 26, 70 - Math.cos(pedal) * 24], armN: [96, 6], armF: [92, 8], brow: F < 2618 ? 0.9 : -0.2, mouth: F >= 2714 ? 0.8 : -0.2, eyes: 1};
	// fallen rider: thrown forward, lands on his front, then gets up
	const flyX = lerp(bx1 - 14, ROCK + 120, ease.out(fall)), flyY = lerp(FEET - 128, FEET - 22, ease.in(fall));
	const lyingPose: Pose = {...P0, torso: 0, legN: [4, 20], legF: [-6, 30], armN: [170, 0], armF: [160, 10], eyes: F > 2660 ? 1 : 0.1, brow: 1};
	const kneel: Pose = {...P0, torso: 20, legN: [90, 150], legF: [10, 100], armN: [40, 20], armF: [30, 30], eyes: 1, brow: 0.6};
	// ---- discoverer (menemukan hal baru) ----
	const bloom = tween(F, 2640, 2664, 0, 1, ease.out);
	const wow = env(F, 2652, 2700, 6, 12);
	const dPose = mix(squat(F, {eyes: 1, look: 0.6, lookY: 0.5}), squat(F, {torso: 10, head: -12, eyes: 1, look: 0.3, lookY: 0, mouth: 0.9, brow: 0.3, armF: [120, 20]}), wow);
	const DX = 2380, DS = 0.85; // discoverer kneels in the grass of the plaza, away from the cyclist's fall
	// ---- rain + umbrella (tidak selalu indah) ----
	const cloudX = lerp(2660, 2850, clamp((F - 2690) / 110));
	const rain = env(F, 2732, 2830, 8, 14);
	const umb = tween(F, 2744, 2754, 0, 1, ease.outBack);
	// ---- stairs climber with a box (tidak selalu mudah) + fork in the fog (ketidakpastian) ----
	const climbU = keys(F, [[2750, 0], [2790, 3.2], [2812, 6.2], [2817, 5.4], [2826, 5.4], [2850, 12]], ease.inOut);
	const slip = env(F, 2812, 2826, 2, 6);
	const atTop = F >= 2850;
	const lookAround = F >= 2852 && F < 2876 ? (F < 2864 ? -1 : 1) : 1;
	const intoFog = clamp((F - 2876) / 20);
	let cPose: Pose = mix(walk(F * 0.22, 0.7, {torso: 18, armN: [84, 64], armF: [80, 70], brow: 0.9, eyes: 1, head: 4}), {...P0, torso: -14, armN: [120, 40], armF: [110, 60], brow: 1, eyes: 1}, slip);
	if (atTop) cPose = intoFog > 0 ? walk(F * 0.25, 0.8, {armN: [84, 64], armF: [80, 70], brow: 0.4}) : idle(F, {armN: [84, 64], armF: [80, 70], brow: 0.6, look: 0.8});
	const cp = atTop ? {x: lerp(3480, 3630, intoFog), y: ST.y1} : F < 2750 ? {x: lerp(2950, ST.x0 - 30, clamp((F - 2712) / 38)), y: ST.y0} : stairPos(climbU);
	return (
		<g>
			<Sky camX={camX} F={F} />
			<Buildings camX={camX} />
			{/* birds */}
			{F > 2330 && F < 2500 ? [0, 1, 2, 3, 4].map((i) => <Bird key={i} x={300 + (F - 2330) * 7 + i * 40} y={420 - i * 18 + Math.sin(F / 8 + i) * 6} F={F + i * 5} s={1.1} />) : null}
			{/* back trees and lamps on the plaza */}
			{[700, 1060, 1450, 1820, 2230, 2560, 2860].map((x, i) => <Tree key={x} x={x} F={F} seed={i} s={0.95 + (i % 2) * 0.12} />)}
			{[1240, 2010].map((x) => <Lamp key={x} x={x} y={WALK + 6} />)}
			<Ground camX={camX} />
			{/* upper platform for the stairs */}
			<rect x={ST.x1 - 4} y={ST.y1} width={1400} height={ST.y0 - ST.y1} fill="#C9B79B" />
			<rect x={ST.x1 - 4} y={ST.y1} width={1400} height={14} fill="#A9967A" />
			<Stairs x0={ST.x0} y0={ST.y0} x1={ST.x1} y1={ST.y1} n={ST.n} />
			<Signpost x={3560} y={ST.y1} wob={Math.sin(F / 20) * 2} />
			{/* background walker on the plaza passes BEHIND the cube */}
			<Stickman x={lerp(300, 1500, clamp((F - 2330) / 300))} y={standY(PLAZA_TOP + 88, 0.78)} pose={walk(F * 0.3, 1)} scale={0.78} shirt="#5A8DEE" shadow={PLAZA_TOP + 88} />
			{/* discoverer + flower in the grass of the plaza */}
			<Flower x={DX + 70} y={PLAZA_FEET - 2} bloom={bloom} />
			<Stickman x={DX} y={grounded(dPose, PLAZA_FEET, DS)} pose={dPose} scale={DS} shirt="#F2C94C" shadow={PLAZA_FEET}
				handProp={<g transform="translate(10 0) rotate(-90)"><circle cx={0} cy={-34} r={22} fill="#CFE6FA" fillOpacity={0.4} stroke="#3A4256" strokeWidth={5} /><rect x={-4} y={-12} width={8} height={30} rx={3} fill="#3A4256" /></g>} />
			{wow > 0.3 ? <text x={DX + 10} y={PLAZA_FEET - 250} fontFamily={FONT} fontWeight={900} fontSize={54} fill="#F2C14E" opacity={wow} textAnchor="middle">!</text> : null}
			{/* umbrella person on the plaza: looks up at the cloud, opens the umbrella when the rain comes */}
			{(() => {
				const US = 0.9, UX = 2850;
				const up = idle(F, {armN: [60 + umb * 70, 70 - umb * 50], brow: 0.3 + umb * 0.3, lookY: -0.6 * env(F, 2700, 2760, 10, 10), look: 0.3});
				const h = limbEnd(up, 'armN');
				const hy = standY(PLAZA_FEET, US);
				return (
					<g>
						<Stickman x={UX} y={hy} pose={up} scale={US} shirt="#5A8DEE" shadow={PLAZA_FEET} />
						<g transform={`translate(${UX + h.x * US} ${hy + (h.y + 8) * US}) rotate(-6) scale(${US})`}><Umbrella open={umb} /></g>
					</g>
				);
			})()}
			{/* the cube (his room) stands on the sidewalk, grounded */}
			<Cube />
			{/* reader on the bench */}
			<Bench x={905} y={WALK - 2} />
			<Stickman x={F < 2466 ? rx : lerp(rx, 905, rSit)} y={lerp(standY(WALK + 2) + (F < 2466 ? rPose.bob : 0), WALK - 60, rSit)} pose={{...rPose, bob: 0}} facing={-1} scale={0.92} shirt="#F08A3E" shadow={WALK + 2}
				handProp={bookOpen > 0 ? <g transform="rotate(90) scale(0.55)"><Icon k="book" s={1} /></g> : undefined} />
			{/* worker at the cafe table */}
			<g>
				<rect x={1290} y={WALK - 74} width={150} height={10} rx={4} fill="#8A6A4C" />
				<rect x={1360} y={WALK - 64} width={10} height={66} fill="#5E4A38" />
				<g transform={`translate(1395 ${WALK - 74})`}><Laptop glow={0.5 + 0.5 * Math.sin(F / 9)} /></g>
				<rect x={1310} y={WALK - 92} width={18} height={18} rx={4} fill="#F7F2E8" />
				{[0, 1].map((i) => <path key={i} d={`M ${1314 + i * 8} ${WALK - 98} q 6 -12 0 -24`} stroke="#fff" strokeWidth={3} fill="none" opacity={0.5 + 0.3 * Math.sin(F / 6 + i)} />)}
				<rect x={1222} y={WALK - 50} width={56} height={10} rx={4} fill="#5E4A38" />
				<rect x={1244} y={WALK - 40} width={10} height={42} fill="#5E4A38" />
				<Stickman x={1250} y={WALK - 54} pose={wPose} scale={0.9} shirt="#46B26B" shadow={WALK + 2} />
			</g>
			{/* friends meeting */}
			<Stickman x={ax} y={standY(FEET) + fA.bob} pose={{...fA, bob: 0}} shirt="#8B6CD6" shadow={FEET} />
			<Stickman x={bx} y={standY(FEET) + fB.bob} pose={{...fB, bob: 0}} facing={-1} shirt="#E5534B" shadow={FEET} />
			{five > 0.6 ? <g transform={`translate(${(ax + bx) / 2} ${standY(FEET) - 205})`} opacity={(five - 0.6) * 2.5}>{[0, 1, 2, 3, 4, 5].map((i) => <line key={i} x1={Math.cos(i) * 18} y1={Math.sin(i) * 18} x2={Math.cos(i) * 34} y2={Math.sin(i) * 34} stroke="#F2C14E" strokeWidth={5} strokeLinecap="round" />)}</g> : null}
			{/* rock that causes the fall */}
			<ellipse cx={ROCK + 4} cy={FEET + 2} rx={34} ry={6} fill="#000" opacity={0.18} />
			<path d={`M ${ROCK - 28} ${FEET} Q ${ROCK - 26} ${FEET - 34} ${ROCK - 2} ${FEET - 38} Q ${ROCK + 26} ${FEET - 34} ${ROCK + 28} ${FEET} Z`} fill="#5E5A54" />
			<path d={`M ${ROCK - 16} ${FEET - 26} Q ${ROCK - 4} ${FEET - 34} ${ROCK + 10} ${FEET - 30}`} stroke="#8A857C" strokeWidth={4} fill="none" strokeLinecap="round" />
			{/* cyclist */}
			<Bicycle x={bx1} y={FEET} rot={bikeRot} wheel={riding ? F * 14 : 0} />
			{riding ? (
				<Stickman x={bx1 - 18} y={FEET - 128 + Math.sin(pedal * 2) * 1.5} pose={ridePose} scale={0.95} shirt="#2FB3B3" rot={bikeRot * 0.6} />
			) : F < 2632 ? (
				<Stickman x={flyX} y={flyY} pose={mix(ridePose, lyingPose, fall)} scale={0.95} shirt="#2FB3B3" rot={fall * 92} />
			) : lie ? (
				<Stickman x={ROCK + 120} y={FEET - 22} pose={{...lyingPose, head: F > 2660 ? -14 : 0}} scale={0.95} shirt="#2FB3B3" rot={92} />
			) : (
				<Stickman x={lerp(ROCK + 120, ROCK + 40, lift)} y={lerp(FEET - 22, standY(FEET, 0.95), getUp)} pose={mix(mix(lyingPose, kneel, clamp(getUp * 2)), idle(F, {eyes: 1, brow: -0.6, mouth: 0.3}), clamp(getUp * 2 - 1))} scale={0.95} shirt="#2FB3B3" rot={lerp(92, 0, clamp(getUp * 1.6))} facing={lift > 0 ? -1 : 1} shadow={FEET} />
			)}
			<RainCloud x={cloudX} y={760} F={F} rain={rain} />
			{/* stairs climber with a box */}
			{(() => {
				const fc = (atTop && intoFog === 0 ? lookAround : 1) as 1 | -1;
				const hy = cp.y - STAND_HIP * 0.95 + (atTop ? 0 : cPose.bob);
				const tilt = slip * 18 * Math.sin(F * 0.9);
				return (
					<g>
						<Stickman x={cp.x} y={hy} pose={{...cPose, bob: 0}} scale={0.95} facing={fc} shirt="#C2617E" shadow={cp.y} />
						<g transform={`translate(${cp.x + fc * 58} ${hy - 60}) rotate(${tilt})`}><Box w={96} h={76} /></g>
					</g>
				);
			})()}
			<Fog x={3450} y={930} w={640} F={F} a={0.9} />
			{/* traffic */}
			<Car x={-200 + (F - 2323) * 13} F={F} color="#E5534B" />
			<Car x={3400 - (F - 2323) * 11} F={F} color="#5A8DEE" dir={-1} y={1560} />
			<Car x={-900 + (F - 2323) * 15} F={F} color="#F2C94C" />
		</g>
	);
};

const CAM: [number, number][] = [[2323, 348], [2392, 430], [2470, 880], [2538, 930], [2556, 1330], [2568, 1330], [2590, 1580], [2600, 1600], [2626, 1960], [2650, 2200], [2714, 2180], [2760, 2800], [2790, 2900], [2812, 3200], [2850, 3390], [2895, 3540]];
const CAMY: [number, number][] = [[2323, 1140], [2392, 1000], [2895, 970]];

const rainAt = (F: number) => env(F, 2732, 2830, 8, 14);
export const CityJourney: React.FC<{f: number; F: number}> = ({F}) => {
	const camX = keys(F, CAM, ease.inOut);
	const camY = keys(F, CAMY, ease.inOut);
	const z = keys(F, [[2323, 0.884], [2392, 1.0], [2470, 1.08], [2793, 1.08], [2895, 1.04]], ease.inOut);
	const fromVoid = 1 - clamp((F - 2323) / 18);
	return (
		<Svg>
			<Cam x={camX} y={camY} z={z}>
				<CityWorld F={F} camX={camX} />
			</Cam>
			{rainAt(F) > 0 ? <rect width={1080} height={1920} fill="#28324F" opacity={0.16 * rainAt(F)} /> : null}
			{fromVoid > 0 ? (
				<>
					<rect width={1080} height={1920} fill="#05070D" opacity={fromVoid} />
					{/* the box stays in place while the city appears around it, then sits on the ground */}
					<g opacity={fromVoid}><Cam x={camX} y={camY} z={z}><Cube /></Cam></g>
				</>
			) : null}
		</Svg>
	);
};

// ---------------- S28: "Namun, ada pengalaman yang tidak mungkin diperoleh" (wide, back at the cube) ----------------
export const Experience: React.FC<{f: number; F: number}> = ({f, F}) => {
	const camX = 560, camY = 980;
	const z = tween(f, 0, 114, 1.0, 1.08, ease.inOutSine);
	const bub = tween(f, 18, 34, 0, 1);
	const {x0, x1, top} = CUBE;
	return (
		<Svg>
			<Cam x={camX} y={camY} z={z}>
				<CityWorld F={F} camX={camX} />
				<ThoughtBubble x={(x0 + x1) / 2 + 40} y={top - 250} w={420} h={300} p={bub} from={[(x0 + x1) / 2, top - 20]}>
					{/* imagined experience: flat, grey, distant */}
					<g opacity={0.9}>
						<path d="M -150 70 L -60 -60 L -10 10 L 40 -70 L 150 70 Z" fill="#AEB6C6" />
						<path d="M -60 -60 L -40 -30 L -78 -34 Z M 40 -70 L 62 -36 L 20 -40 Z" fill="#E9EDF3" />
						<circle cx={90} cy={-70} r={26} fill="#C9CED8" />
						<path d="M -150 70 L 150 70" stroke="#9AA3B5" strokeWidth={4} />
					</g>
				</ThoughtBubble>
			</Cam>
		</Svg>
	);
};

// ---------------- S29 face: "hanya dengan terus membayangkannya." ----------------
export const FaceImagine: React.FC<{f: number}> = ({f}) => {
	const b = tween(f, 4, 16, 0, 1) * (1 - tween(f, 44, 52, 0, 1));
	const pop = f >= 44 && f < 54 ? (f - 44) / 10 : 0;
	return (
		<FaceClose f={f} bg="night" glow="moon" expr={{eyes: 0.08, lid: 0, brow: 0.3, mouth: 0.2}} push={tween(f, 0, 60, 0.02, 0.08)}>
			<ThoughtBubble x={800} y={360} w={330} h={240} p={b} from={[700, 600]}>
				<path d="M -110 50 L -40 -40 L 0 10 L 40 -50 L 110 50 Z" fill="#C3CAD8" />
				<circle cx={60} cy={-50} r={20} fill="#D6DBE4" />
			</ThoughtBubble>
			{pop > 0 ? [0, 1, 2, 3, 4, 5].map((i) => <circle key={i} cx={800 + Math.cos(i) * (60 + pop * 140)} cy={360 + Math.sin(i) * (40 + pop * 100)} r={14 * (1 - pop)} fill="#E8EDF7" opacity={1 - pop} />) : null}
		</FaceClose>
	);
};

// ---------------- S30 phone: "Suatu malam, ia berhenti menatap layar ponselnya." ----------------
const STOP_SWIPES = [4, 18, 33, 50, 70];
export const PhoneStop: React.FC<{f: number; F: number}> = ({f}) => {
	// local 0..153 (102.3s..107.4s); thumb stops ~105.0s (local 81), lifts 92, screen off 110-122
	const amp = (i: number) => [330, 300, 240, 160, 90][i];
	let scroll = 0;
	STOP_SWIPES.forEach((s, i) => {
		if (f > s) scroll += amp(i) * (1 - Math.exp(-(f - s) / (7 + i * 2)));
	});
	const lift = tween(f, 86, 98, 0, 1, ease.inOut);
	const screen = 1 - tween(f, 108, 122, 0, 1, ease.inOut);
	const reflect = tween(f, 124, 140, 0, 1);
	return (
		<Svg bg="#04070D">
			<defs>
				<radialGradient id="psbg" cx="55%" cy="58%" r="75%">
					<stop offset="0%" stopColor="#22384E" />
					<stop offset="45%" stopColor="#13243A" />
					<stop offset="100%" stopColor="#050A13" />
				</radialGradient>
			</defs>
			<rect width={1080} height={1920} fill="url(#psbg)" opacity={0.35 + 0.65 * screen} />
			<Phone cx={560} cy={1130} w={560} rot={6} scroll={scroll} screen={screen} glow={screen} thumbY={f < 86 ? thumbFrom(f, STOP_SWIPES) : lerp(70, 260, lift)} />
			{/* his face faintly reflected in the dark screen */}
			{reflect > 0 ? (
				<g transform="translate(560 1080) rotate(6)" opacity={reflect * 0.22}>
					<circle r={170} fill="none" stroke="#AFC4FF" strokeWidth={6} />
					<ellipse cx={-55} cy={-10} rx={14} ry={22} fill="#AFC4FF" />
					<ellipse cx={55} cy={-10} rx={14} ry={22} fill="#AFC4FF" />
				</g>
			) : null}
		</Svg>
	);
};

export {CURB};
