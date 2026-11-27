import React from 'react';
import {FONT} from '../fonts';

// Same look as the original HUD (fill #0B101A, 2px border #3C4560, gold label, white number),
// but with a label that is readable on a phone (30px instead of ~21px) and a bigger number.
export type HudLayout = {
	x: number;
	y: number;
	w: number;
	h: number;
	radius: number;
	labelSize: number;
	labelTop: number;
	labelSpacing: number; // em
	labelDx: number;
	numSize: number;
	numRight: number;
	numBottom: number;
};

// New HUD: x>=700 so it never touches the wall calendar (which reaches x~688 at 13-14s and 30-31s);
// still fully covers the original box (746-1009 x 246-373).
export const HUD_LAYOUT: HudLayout = {
	x: 700, y: 204, w: 314, h: 174, radius: 18,
	labelSize: 30, labelTop: 24, labelSpacing: 0.08, labelDx: 0,
	numSize: 76, numRight: 28, numBottom: 20,
};

// The original v3 HUD, measured on the frames (used to morph into it before it flies to the centre).
export const HUD_LAYOUT_V3: HudLayout = {
	x: 746, y: 246, w: 264, h: 128, radius: 16,
	labelSize: 21.3, labelTop: 22, labelSpacing: 0.16, labelDx: 5.5,
	numSize: 56.7, numRight: 32, numBottom: 9.75,
};

export const HUD_RECT = {x: HUD_LAYOUT.x, y: HUD_LAYOUT.y, w: HUD_LAYOUT.w, h: HUD_LAYOUT.h};

export function lerpLayout(a: HudLayout, b: HudLayout, t: number): HudLayout {
	const o = {} as HudLayout;
	(Object.keys(a) as (keyof HudLayout)[]).forEach((k) => {
		o[k] = a[k] + (b[k] - a[k]) * t;
	});
	return o;
}

export const HudBox: React.FC<{
	label: string;
	value: number | string;
	x?: number;
	y?: number;
	layout?: HudLayout;
	opacity?: number;
	struck?: boolean;
	dim?: boolean;
	highlight?: number; // 0..1 gold border glow
}> = ({label, value, x, y, layout = HUD_LAYOUT, opacity = 1, struck = false, dim = false, highlight = 0}) => {
	const gold = '#E4BF79';
	const L = layout;
	return (
		<div
			style={{
				position: 'absolute',
				left: x ?? L.x,
				top: y ?? L.y,
				width: L.w,
				height: L.h,
				boxSizing: 'border-box',
				borderRadius: L.radius,
				background: '#0B101A',
				border: `2px solid ${highlight > 0 ? mix('#3C4560', gold, highlight) : '#3C4560'}`,
				boxShadow: highlight > 0 ? `0 0 ${28 * highlight}px rgba(228,191,121,${0.35 * highlight})` : 'none',
				opacity,
				fontFamily: FONT,
			}}
		>
			<div
				style={{
					position: 'absolute',
					top: L.labelTop,
					left: L.labelDx,
					right: -L.labelDx,
					textAlign: 'center',
					fontWeight: 900,
					fontSize: L.labelSize,
					lineHeight: 1,
					letterSpacing: `${L.labelSpacing}em`,
					color: gold,
					opacity: dim ? 0.55 : 1,
					whiteSpace: 'nowrap',
				}}
			>
				{label}
			</div>
			<div
				style={{
					position: 'absolute',
					right: L.numRight,
					bottom: L.numBottom,
					fontWeight: 900,
					fontSize: L.numSize,
					lineHeight: 1,
					color: dim ? '#8C95A8' : '#FFFFFF',
					fontVariantNumeric: 'tabular-nums',
				}}
			>
				<span style={{position: 'relative', display: 'inline-block'}}>
					{value}
					{struck ? (
						<span
							style={{
								position: 'absolute',
								left: -10,
								right: -10,
								top: '52%',
								height: 7,
								borderRadius: 4,
								background: '#E0525A',
								transform: 'rotate(-8deg)',
							}}
						/>
					) : null}
				</span>
			</div>
		</div>
	);
};

function mix(a: string, b: string, t: number) {
	const pa = [1, 3, 5].map((i) => parseInt(a.slice(i, i + 2), 16));
	const pb = [1, 3, 5].map((i) => parseInt(b.slice(i, i + 2), 16));
	const c = pa.map((v, i) => Math.round(v + (pb[i] - v) * t));
	return `rgb(${c[0]},${c[1]},${c[2]})`;
}
