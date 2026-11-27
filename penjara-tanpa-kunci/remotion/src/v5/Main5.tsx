import React from 'react';
import {AbsoluteFill, Sequence, useCurrentFrame} from 'remotion';
import '../fonts';
import {FONT} from '../fonts';
import {Captions} from './components/Captions';
import {Hud} from './components/Hud';
import {clamp, ease, tween} from './lib/anim';
import {promises} from './lib/story';
import {AlarmClose, BedMorning, BedNight, Hook, Montage, NoteClose, PhoneGrab} from './scenes/Act1';
import {BarsAndBox, CardsToBars, FaceAware, FaceExcuse, FaceNod, Reasons, Tired, WantChange} from './scenes/Act2';
import {CityJourney, Experience, FaceImagine, PhoneStop} from './scenes/Act3';
import {DoorNoAnswer, DoorOpens, EndScene, FaceDoubt, FaceSilent, FaceUnderstand, FirstStep, HandleClose, LookAround, NoPromise, Possibilities, Question, QuestionsBubble, Realize, RiseWalk, Split, StepOut, WalkCity} from './scenes/Act4';

type Scene = {from: number; to: number; name: string; xf?: number; render?: (f: number, F: number) => React.ReactNode};
// xf = dissolve frames into this scene (0 = hard cut). Consecutive shots in the same room use a hard cut with a matched camera
// (want-change, bars-box, question, questions-bubble, door-opens, possibilities, walk-city), so the shot simply continues
// instead of showing a double image.
const XF: Record<string, number> = {hook: 0, 'bed-night': 6, 'note-close': 4, 'bed-morning': 4, alarm: 0, 'phone-grab': 3, montage: 6, 'face-aware': 5, tired: 5, 'want-change': 0, 'face-excuse': 3, reasons: 4, 'face-nod': 3, 'cards-to-bars': 4, 'bars-box': 0, 'city-journey': 0, experience: 6, 'face-imagine': 5, 'phone-stop': 6, 'look-around': 6, question: 0, 'questions-bubble': 0, 'face-silent': 5, 'door-no-answer': 6, 'face-doubt': 5, 'no-promise': 5, 'rise-walk': 6, handle: 4, realize: 4, 'door-opens': 0, 'step-out': 0, possibilities: 0, 'walk-city': 0, 'face-understand': 5, split: 6, 'first-step': 5, end: 6};

export const SCENES: Scene[] = [
	{from: 0, to: 77, name: 'hook', render: (f) => <Hook f={f} />},
	{from: 77, to: 186, name: 'bed-night', render: (f, F) => <BedNight f={f} F={F} />},
	{from: 186, to: 240, name: 'note-close', render: (f) => <NoteClose f={f} />},
	{from: 240, to: 420, name: 'bed-morning', render: (f, F) => <BedMorning f={f} F={F} />},
	{from: 420, to: 489, name: 'alarm', render: (f, F) => <AlarmClose f={f} F={F} />},
	{from: 489, to: 555, name: 'phone-grab', render: (f) => <PhoneGrab f={f} />},
	{from: 555, to: 930, name: 'montage', render: (f, F) => <Montage f={f} F={F} />},
	{from: 930, to: 1032, name: 'face-aware', render: (f) => <FaceAware f={f} />},
	{from: 1032, to: 1305, name: 'tired', render: (f, F) => <Tired f={f} F={F} />},
	{from: 1305, to: 1451, name: 'want-change', render: (f, F) => <WantChange f={f} F={F} />},
	{from: 1451, to: 1482, name: 'face-excuse', render: (f) => <FaceExcuse f={f} />},
	{from: 1482, to: 1817, name: 'reasons', render: (f, F) => <Reasons f={f} F={F} />},
	{from: 1817, to: 1875, name: 'face-nod', render: (f) => <FaceNod f={f} />},
	{from: 1875, to: 2025, name: 'cards-to-bars', render: (f, F) => <CardsToBars f={f} F={F} />},
	{from: 2025, to: 2323, name: 'bars-box', render: (f, F) => <BarsAndBox f={f} F={F} />},
	{from: 2323, to: 2895, name: 'city-journey', render: (f, F) => <CityJourney f={f} F={F} />},
	{from: 2895, to: 3009, name: 'experience', render: (f, F) => <Experience f={f} F={F} />},
	{from: 3009, to: 3069, name: 'face-imagine', render: (f) => <FaceImagine f={f} />},
	{from: 3069, to: 3222, name: 'phone-stop', render: (f, F) => <PhoneStop f={f} F={F} />},
	{from: 3222, to: 3498, name: 'look-around', render: (f, F) => <LookAround f={f} F={F} />},
	{from: 3498, to: 3676, name: 'question', render: (f, F) => <Question f={f} F={F} />},
	{from: 3676, to: 3925, name: 'questions-bubble', render: (f, F) => <QuestionsBubble f={f} F={F} />},
	{from: 3925, to: 4002, name: 'face-silent', render: (f) => <FaceSilent f={f} />},
	{from: 4002, to: 4128, name: 'door-no-answer', render: (f, F) => <DoorNoAnswer f={f} F={F} />},
	{from: 4128, to: 4242, name: 'face-doubt', render: (f) => <FaceDoubt f={f} />},
	{from: 4242, to: 4392, name: 'no-promise', render: (f, F) => <NoPromise f={f} F={F} />},
	{from: 4392, to: 4611, name: 'rise-walk', render: (f, F) => <RiseWalk f={f} F={F} />},
	{from: 4611, to: 4689, name: 'handle', render: (f) => <HandleClose f={f} />},
	{from: 4689, to: 4809, name: 'realize', render: (f, F) => <Realize f={f} F={F} />},
	{from: 4809, to: 4929, name: 'door-opens', render: (f, F) => <DoorOpens f={f} F={F} />},
	{from: 4929, to: 5040, name: 'step-out', render: (f, F) => <StepOut f={f} F={F} />},
	{from: 5040, to: 5130, name: 'possibilities', render: (f, F) => <Possibilities f={f} F={F} />},
	{from: 5130, to: 5222, name: 'walk-city', render: (f, F) => <WalkCity f={f} F={F} />},
	{from: 5222, to: 5328, name: 'face-understand', render: (f) => <FaceUnderstand f={f} />},
	{from: 5328, to: 5529, name: 'split', render: (f, F) => <Split f={f} F={F} />},
	{from: 5529, to: 5637, name: 'first-step', render: (f, F) => <FirstStep f={f} F={F} />},
	{from: 5637, to: 5724, name: 'end', render: (f, F) => <EndScene f={f} F={F} />},
];

const SceneSlot: React.FC<{s: Scene; xf: number}> = ({s, xf}) => {
	const fr = useCurrentFrame();
	const f = fr - xf; // local frame relative to the scene start (negative during the dissolve)
	const F = s.from + f;
	const op = xf > 0 ? clamp((fr + 1) / (xf + 1)) : 1;
	if (!s.render) {
		return (
			<AbsoluteFill style={{background: '#111', color: '#666', fontFamily: FONT, fontSize: 40, justifyContent: 'center', alignItems: 'center'}}>
				{s.name}
			</AbsoluteFill>
		);
	}
	return <AbsoluteFill style={{opacity: op}}>{s.render(f, F)}</AbsoluteFill>;
};

// HUD visible in the room / phone scenes (hidden in face close-ups and outside)
const HUD_WINDOWS: [number, number][] = [[247, 930], [1032, 1451], [1482, 1817], [1875, 2300], [3069, 3498], [4002, 4128]];

const GlobalHud: React.FC = () => {
	const F = useCurrentFrame();
	const w = HUD_WINDOWS.find(([a, b]) => F >= a && F < b);
	if (!w) return null;
	const pop = F - w[0] < 8 && w[0] === 247 ? tween(F, 247, 254, 0.85, 1, ease.outBack) : 1;
	// fades in/out at every window edge (no popping across cuts); the box shot fades it out slowly
	const edge = Math.min(clamp((F - w[0] + 1) / 6), clamp((w[1] - F) / 6));
	const op = (w[0] === 247 ? Math.min(clamp((F - 247) / 4), clamp((w[1] - F) / 6)) : edge) * (w[1] === 2300 ? 1 - clamp((F - 2262) / 30) : 1);
	return <Hud value={promises(F)} scale={pop} opacity={op} />;
};

export const Main5: React.FC = () => (
	<AbsoluteFill style={{background: '#000'}}>
		{SCENES.map((s) => {
			const xf = Math.min(XF[s.name] ?? 4, s.from);
			return (
				<Sequence key={s.name + s.from} from={s.from - xf} durationInFrames={s.to - s.from + xf} layout="none">
					<SceneSlot s={s} xf={xf} />
				</Sequence>
			);
		})}
		<GlobalHud />
		<Captions />
	</AbsoluteFill>
);
