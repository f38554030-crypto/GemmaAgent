import React from 'react';
import {AbsoluteFill, useCurrentFrame} from 'remotion';
import {Stickman, idle, walk, sit, P0, STAND_HIP} from './components/Stickman';
import {C} from './theme';

export const RigTest: React.FC = () => {
	const f = useCurrentFrame();
	const ground = 700;
	const items = [
		{label: 'idle', pose: idle(f)},
		{label: 'walk 0', pose: walk(0)},
		{label: 'walk 1.2', pose: walk(1.2)},
		{label: 'walk 2.4', pose: walk(2.4)},
		{label: 'walk 3.6', pose: walk(3.6)},
	];
	return (
		<AbsoluteFill style={{background: '#22304D'}}>
			<svg width={1080} height={1920}>
				<rect x={0} y={ground} width={1080} height={8} fill="#556" />
				{items.map((it, i) => (
					<g key={i}>
						<Stickman x={110 + i * 210} y={ground - STAND_HIP + it.pose.bob} pose={it.pose} scale={0.9} shadow={ground} />
						<text x={110 + i * 210} y={ground + 50} fill="#fff" fontSize={26} textAnchor="middle">{it.label}</text>
					</g>
				))}
				{/* sit on a stool */}
				<rect x={120} y={1150} width={160} height={18} fill="#666" />
				<Stickman x={190} y={1150} pose={sit(f, {eyes: 1, lid: 0.5, brow: 1})} scale={0.9} />
				{/* lying (on back) */}
				<rect x={380} y={1180} width={420} height={30} fill="#666" />
				<Stickman x={600} y={1155} rot={-90} facing={1} pose={{...P0, torso: 0, legN: [4, 4], legF: [0, 6], armN: [150, -30], armF: [140, -20], eyes: 1}} scale={0.9} />
				{/* front face big */}
				<Stickman x={540} y={1800} pose={{...P0, eyes: 0.9, lid: 0.45, brow: 1, mouth: -0.4}} scale={2.2} front />
				{/* hunched sitting */}
				<rect x={820} y={1150} width={160} height={18} fill="#666" />
				<Stickman x={890} y={1150} pose={sit(f, {torso: 28, head: 24, armN: [60, 70], armF: [55, 75], eyes: 0.4, lid: 0.6})} scale={0.9} />
			</svg>
		</AbsoluteFill>
	);
};
