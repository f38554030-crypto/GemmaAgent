import React from 'react';
import {FONT} from '../fonts';

// Same look as the original HUD (fill #0B101A, 2px border #3C4560, gold label, white number),
// but with a label that is readable on a phone (30px instead of ~21px) and a bigger number.
export const HUD_RECT = {x: 666, y: 204, w: 348, h: 174};

export const HudBox: React.FC<{
	label: string;
	value: number | string;
	x: number;
	y: number;
	w?: number;
	h?: number;
	opacity?: number;
	struck?: boolean;
	dim?: boolean;
	highlight?: number; // 0..1 gold border glow
}> = ({label, value, x, y, w = HUD_RECT.w, h = HUD_RECT.h, opacity = 1, struck = false, dim = false, highlight = 0}) => {
	const gold = '#E4BF79';
	return (
		<div
			style={{
				position: 'absolute',
				left: x,
				top: y,
				width: w,
				height: h,
				boxSizing: 'border-box',
				borderRadius: 18,
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
					top: 24,
					left: 0,
					right: 0,
					textAlign: 'center',
					fontWeight: 900,
					fontSize: 30,
					lineHeight: 1,
					letterSpacing: '0.08em',
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
					right: 30,
					bottom: 20,
					fontWeight: 900,
					fontSize: 76,
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
