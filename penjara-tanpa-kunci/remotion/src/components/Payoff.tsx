import React from 'react';
import {Easing, interpolate, useCurrentFrame} from 'remotion';
import {HUD_RECT, HudBox} from './HudBox';

// v3 issue: the payoff of the whole counter device ("LANGKAH NYATA 1") was on screen for only ~0.8 s
// (frames 5600-5624) and never shown next to the 412 broken promises.
// Fix: keep it on screen through the legs shot, then bring it under the end card next to the struck
// "JANJI 'BESOK' 412", and fade everything out with the original fade to black.
const FADE: Record<number, number> = {
	5710: 0.99, 5711: 0.959, 5712: 0.9, 5713: 0.796, 5714: 0.635, 5715: 0.465, 5716: 0.319,
	5717: 0.218, 5718: 0.142, 5719: 0.085, 5720: 0.048, 5721: 0.02, 5722: 0.011, 5723: 0.004,
};
const CL = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

const ROW_Y = 640;
const LEFT_X = 178;
const RIGHT_X = 554;

export const Payoff: React.FC = () => {
	const f = useCurrentFrame();
	if (f < 5600) return null;
	const fade = f >= 5710 ? FADE[f] ?? 0 : 1;
	const pop = interpolate(f, [5600, 5606], [1.06, 1], {...CL, easing: Easing.out(Easing.cubic)});
	const p = interpolate(f, [5637, 5648], [0, 1], {...CL, easing: Easing.inOut(Easing.cubic)});
	const rx = interpolate(p, [0, 1], [HUD_RECT.x, RIGHT_X]);
	const ry = interpolate(p, [0, 1], [HUD_RECT.y, ROW_Y]);
	const leftIn = interpolate(f, [5643, 5653], [0, 1], {...CL, easing: Easing.out(Easing.cubic)});
	const glow = interpolate(f, [5648, 5658], [0, 1], CL);
	return (
		<>
			{leftIn > 0 ? (
				<HudBox
					label={'JANJI “BESOK”'}
					value={412}
					x={LEFT_X - 36 * (1 - leftIn)}
					y={ROW_Y}
					opacity={leftIn * fade}
					struck
					dim
				/>
			) : null}
			<div style={{position: 'absolute', inset: 0, transform: `scale(${pop})`, transformOrigin: `${HUD_RECT.x + HUD_RECT.w / 2}px ${HUD_RECT.y + HUD_RECT.h / 2}px`}}>
				<HudBox label="LANGKAH NYATA" value={1} x={rx} y={ry} opacity={fade} highlight={glow} />
			</div>
		</>
	);
};
