import React from 'react';
import {Easing, interpolate, useCurrentFrame} from 'remotion';
import {HUD_LAYOUT, HudBox} from './HudBox';

// v3 issue: the payoff of the whole counter device ("LANGKAH NYATA 1") was on screen for only ~0.8 s
// (frames 5600-5624) and never shown next to the 412 broken promises.
// Fix: keep it on screen through the legs shot; at the cut to the end scene (frame 5637) it sits under
// the end card next to the struck "JANJI 'BESOK' 412", and everything fades out with the original
// fade to black. (Re-positioning on the cut avoids crossing the end card while it fades in.)
const FADE: Record<number, number> = {
	5710: 0.99, 5711: 0.959, 5712: 0.9, 5713: 0.796, 5714: 0.635, 5715: 0.465, 5716: 0.319,
	5717: 0.218, 5718: 0.142, 5719: 0.085, 5720: 0.048, 5721: 0.02, 5722: 0.011, 5723: 0.004,
};
const CL = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

const START = 5599; // the original "LANGKAH NYATA" HUD shows from 5599
const CUT = 5637;
const ROW_Y = 640;
const GAP = 28;
const LEFT_X = (1080 - (2 * HUD_LAYOUT.w + GAP)) / 2;
const RIGHT_X = LEFT_X + HUD_LAYOUT.w + GAP;

export const Payoff: React.FC = () => {
	const f = useCurrentFrame();
	if (f < START) return null;
	const fade = f >= 5710 ? FADE[f] ?? 0 : 1;
	const onRow = f >= CUT;
	// small pop on appearance and again when it lands under the end card
	const pop = onRow
		? interpolate(f, [CUT, CUT + 5], [0.9, 1], {...CL, easing: Easing.out(Easing.back(1.6))})
		: interpolate(f, [START, START + 6], [1.06, 1], {...CL, easing: Easing.out(Easing.cubic)});
	const x = onRow ? RIGHT_X : HUD_LAYOUT.x;
	const y = onRow ? ROW_Y : HUD_LAYOUT.y;
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
			<div
				style={{
					position: 'absolute',
					inset: 0,
					transform: `scale(${pop})`,
					transformOrigin: `${x + HUD_LAYOUT.w / 2}px ${y + HUD_LAYOUT.h / 2}px`,
				}}
			>
				<HudBox label="LANGKAH NYATA" value={1} x={x} y={y} opacity={fade} highlight={glow} />
			</div>
		</>
	);
};
