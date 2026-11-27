import React from 'react';
import {useCurrentFrame} from 'remotion';
import cal from '../data/calendar.json';
import {FONT} from '../fonts';

// v3 bug: during the "time passes" montage each torn-off calendar page was BLANK and the number only
// peeked from the page underneath, so the calendar strobed blank/number ~5x per second.
// Fix: the page that is torn off now carries the previous day's number, following its exact pose
// (position + rotation fitted per frame from the original frames).
const data = cal as unknown as Record<string, [number, number, number, number, number, number]>;

// Nunito metrics: cap height 0.705em, cap centre sits 0.0235em above the line-box centre (line-height 1)
export const CalendarFix: React.FC = () => {
	const frame = useCurrentFrame();
	const d = data[String(frame)];
	if (!d) return null;
	const [cx, cy, theta, w, h, val] = d;
	const digits = String(val).length;
	// measured on the original pages: 1-2 digits cap = 0.39w, 3 digits cap = 0.32w (fit to page width)
	const fontSize = (digits >= 3 ? 0.32 : 0.39) * w / 0.705;
	// cap centre below the page top (header bottom): 0.435w (1-2 digits) / 0.472w (3 digits); page centre is h/2
	const capCentreFromTop = (digits >= 3 ? 0.472 : 0.435) * w;
	const dy = capCentreFromTop - h / 2 + 0.0235 * fontSize;
	return (
		<div
			style={{
				position: 'absolute',
				left: cx,
				top: cy,
				width: w,
				height: h,
				transform: `translate(-50%, -50%) rotate(${theta}deg)`,
			}}
		>
			<div
				style={{
					position: 'absolute',
					left: 0,
					right: 0,
					top: h / 2 + dy,
					transform: 'translateY(-50%)',
					textAlign: 'center',
					fontFamily: FONT,
					fontWeight: 900,
					fontSize,
					lineHeight: 1,
					color: '#242735',
				}}
			>
				{val}
			</div>
		</div>
	);
};
