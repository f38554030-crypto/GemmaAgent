import React from 'react';
import {C} from '../theme';

// Cartoon hand for close-ups. Local space: wrist at (0, 0), fingers point to -y (up). `side` mirrors.
export type HandPose = 'open' | 'fist' | 'pen' | 'press' | 'grip';

export const Hand: React.FC<{
	x: number; y: number; rot?: number; s?: number; side?: 1 | -1; pose?: HandPose; arm?: number; curl?: number;
}> = ({x, y, rot = 0, s = 1, side = 1, pose = 'open', arm = 700, curl = 0}) => {
	const skin = C.skin, line = '#C3CBDB';
	const fingers = [
		{x: -33, len: 70}, {x: -11, len: 82}, {x: 11, len: 78}, {x: 32, len: 64},
	];
	const c = pose === 'fist' || pose === 'pen' || pose === 'grip' ? 1 : pose === 'press' ? 0.15 : curl;
	return (
		<g transform={`translate(${x} ${y}) rotate(${rot}) scale(${side * s} ${s})`}>
			{/* forearm */}
			<rect x={-38} y={-10} width={76} height={arm} rx={36} fill={skin} />
			<rect x={22} y={-10} width={14} height={arm} rx={7} fill={line} opacity={0.35} />
			{/* palm */}
			<rect x={-50} y={-118} width={100} height={118} rx={42} fill={skin} />
			{/* fingers */}
			{fingers.map((fi, i) => {
				const len = fi.len * (1 - 0.62 * c);
				return (
					<g key={i}>
						<rect x={fi.x - 11.5} y={-108 - len} width={23} height={len + 20} rx={11.5} fill={skin} stroke={line} strokeWidth={2.2} />
						{c > 0.5 ? <path d={`M ${fi.x - 10} ${-110 - len + 14} q 10 8 20 0`} stroke={line} strokeWidth={2.2} fill="none" /> : null}
					</g>
				);
			})}
			<rect x={-50} y={-118} width={100} height={60} rx={30} fill={skin} />
			{/* pen */}
			{pose === 'pen' ? (
				<g transform="translate(-6 -120) rotate(-28)">
					<rect x={-9} y={-20} width={18} height={180} rx={7} fill="#2B3350" />
					<rect x={-9} y={-20} width={18} height={26} rx={7} fill="#4A5578" />
					<path d="M -9 160 L 9 160 L 0 192 Z" fill="#11151F" />
				</g>
			) : null}
			{/* thumb */}
			<g transform={`translate(-46 -60) rotate(${pose === 'press' ? -12 : pose === 'pen' ? -58 : -38 - c * 20})`}>
				<rect x={-12} y={-74} width={25} height={80} rx={12.5} fill={skin} stroke={line} strokeWidth={2.2} />
			</g>
		</g>
	);
};
