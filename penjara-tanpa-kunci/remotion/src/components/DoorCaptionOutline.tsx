import React from 'react';
import {interpolate, useCurrentFrame} from 'remotion';
import door from '../data/doorCaption.json';

// v3 issue: in the door-handle close-up (2:33.8-2:36.4) a black prison bar runs exactly behind the
// caption, so the dark caption pill visually merges with the bar. A light outline separates them.
const data = door as unknown as Record<string, [number, number, number, number, number]>;

export const DoorCaptionOutline: React.FC = () => {
	const frame = useCurrentFrame();
	const d = data[String(frame)];
	if (!d) return null;
	const [x0, x1, y0, y1, age] = d;
	const opacity = interpolate(age, [0, 3], [0, 1], {extrapolateRight: 'clamp'});
	const g = 3; // outline sits just outside the pill
	return (
		<div
			style={{
				position: 'absolute',
				left: x0 - g,
				top: y0 - g,
				width: x1 - x0 + 2 * g,
				height: y1 - y0 + 2 * g,
				boxSizing: 'border-box',
				borderRadius: 24 + g,
				border: '3px solid rgba(150,164,198,0.85)',
				boxShadow: '0 0 14px rgba(150,164,198,0.35)',
				opacity,
			}}
		/>
	);
};
