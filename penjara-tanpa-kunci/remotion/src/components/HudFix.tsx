import React from 'react';
import {Easing, interpolate, useCurrentFrame} from 'remotion';
import hud from '../data/hud.json';
import {HUD_RECT, HudBox} from './HudBox';

// Replaces the original "JANJI 'BESOK'" counter (label ~21px, unreadable on a phone) with a larger,
// identically styled box that fully covers it. Values/visibility are read per frame from the v3 render.
const data = hud as unknown as Record<string, [number, number | null]>;

// At 143.97s (frame 4319) the original small HUD starts flying to the centre for the climax.
// Before that, our larger HUD shrinks onto the original's footprint (bottom-right anchored),
// so the hand-over is invisible.
const SHRINK_FROM = 4310;
const HANDOVER = 4318;
const S_END = 0.765; // 348x174 -> ~266x133 covers the original 263x127 box

export const HudFix: React.FC = () => {
	const frame = useCurrentFrame();
	const d = data[String(frame)];
	if (!d || frame > HANDOVER) return null;
	const [alpha, value] = d;
	let opacity = 1;
	let scale = 1;
	if (frame < 4300) {
		// fade-in at 8.3s: run ahead of the original so its smaller box never shows through
		opacity = Math.min(1, alpha * 2.5);
	} else {
		scale = interpolate(frame, [SHRINK_FROM, HANDOVER], [1, S_END], {
			extrapolateLeft: 'clamp',
			extrapolateRight: 'clamp',
			easing: Easing.inOut(Easing.cubic),
		});
	}
	if (opacity <= 0.001) return null;
	const ox = HUD_RECT.x + HUD_RECT.w; // bottom-right anchor
	const oy = HUD_RECT.y + HUD_RECT.h;
	const shift = interpolate(scale, [S_END, 1], [-3, 0]);
	return (
		<div
			style={{
				position: 'absolute',
				inset: 0,
				transform: `translate(${shift}px, ${shift}px) scale(${scale})`,
				transformOrigin: `${ox}px ${oy}px`,
			}}
		>
			<HudBox label={'JANJI “BESOK”'} value={value ?? 1} x={HUD_RECT.x} y={HUD_RECT.y} opacity={opacity} />
		</div>
	);
};
