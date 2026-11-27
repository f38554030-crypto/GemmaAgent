import React from 'react';
import {Easing, interpolate, useCurrentFrame} from 'remotion';
import hud from '../data/hud.json';
import {HUD_LAYOUT, HUD_LAYOUT_V3, HudBox, lerpLayout} from './HudBox';

// Replaces the original "JANJI 'BESOK'" counter (label ~21px, unreadable on a phone) with a larger,
// identically styled box that fully covers it. Values/visibility are read per frame from the v3 render.
const data = hud as unknown as Record<string, [number, number | null]>;

// The original HUD starts fading in at frame 247: ours is fully opaque from 247 with a small pop
// (anchored bottom-right so it always covers the original box).
const ENTER = 247;
// At 143.97s (frame 4319) the original small HUD starts flying to the centre for the climax.
// Before that, our HUD morphs (box, fonts, positions) into the original's exact layout,
// so the hand-over is invisible.
const MORPH_FROM = 4310;
const HANDOVER = 4318;
const CL = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

export const HudFix: React.FC = () => {
	const frame = useCurrentFrame();
	const d = data[String(frame)] ?? (frame >= ENTER && frame < 252 ? [1, 1] : undefined);
	if (!d || frame > HANDOVER) return null;
	const value = d[1] ?? 1;
	const pop = interpolate(frame, [ENTER, ENTER + 3], [0.9, 1], {...CL, easing: Easing.out(Easing.cubic)});
	const t = interpolate(frame, [MORPH_FROM, HANDOVER], [0, 1], {...CL, easing: Easing.inOut(Easing.cubic)});
	const layout = frame >= MORPH_FROM ? lerpLayout(HUD_LAYOUT, HUD_LAYOUT_V3, t) : HUD_LAYOUT;
	const ox = HUD_LAYOUT.x + HUD_LAYOUT.w;
	const oy = HUD_LAYOUT.y + HUD_LAYOUT.h;
	return (
		<div style={{position: 'absolute', inset: 0, transform: `scale(${pop})`, transformOrigin: `${ox}px ${oy}px`}}>
			<HudBox label={'JANJI “BESOK”'} value={value} layout={layout} />
		</div>
	);
};
