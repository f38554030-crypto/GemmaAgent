import React, {useId} from 'react';
import {C} from '../theme';

// One phone design for every phone shot (hook, "ponsel kembali digenggam", "berhenti menatap layar").
const CARDS = [
	['#4C74C6', '#6A8CD4'],
	['#6A59BE', '#8070CB'],
	['#2C8A95', '#4B9FA9'],
	['#7A69C8', '#9283D3'],
	['#C2617E', '#D27D97'],
];

export const Phone: React.FC<{
	cx: number; cy: number; w?: number; rot?: number; scroll: number; screen?: number; glow?: number;
	thumbY?: number; thumbPress?: number; showHand?: boolean; notif?: number;
}> = ({cx, cy, w = 560, rot = 6, scroll, screen = 1, glow = 1, thumbY = 0, thumbPress = 0, showHand = true, notif = 0}) => {
	const id = useId().replace(/:/g, '');
	const h = w * 1.95;
	const pad = w * 0.035;
	const sw = w - pad * 2, shh = h - pad * 2;
	const cardH = sw * 0.47, gap = sw * 0.045, pitch = cardH + gap;
	const first = Math.floor(scroll / pitch);
	const off = scroll - first * pitch;
	return (
		<g transform={`translate(${cx} ${cy}) rotate(${rot})`}>
			<defs>
				<clipPath id={`scr${id}`}>
					<rect x={-sw / 2} y={-shh / 2} width={sw} height={shh} rx={w * 0.1} />
				</clipPath>
				<radialGradient id={`glow${id}`}>
					<stop offset="0%" stopColor={C.phoneGlow} stopOpacity={0.55} />
					<stop offset="100%" stopColor={C.phoneGlow} stopOpacity={0} />
				</radialGradient>
			</defs>
			{glow > 0 ? <ellipse rx={w * 1.25} ry={h * 0.85} fill={`url(#glow${id})`} opacity={glow * screen} /> : null}
			<rect x={-w / 2} y={-h / 2} width={w} height={h} rx={w * 0.13} fill="#05080F" />
			<rect x={-w / 2 + 3} y={-h / 2 + 3} width={w - 6} height={h - 6} rx={w * 0.125} fill="none" stroke="#1C2436" strokeWidth={3} />
			<g clipPath={`url(#scr${id})`}>
				<rect x={-sw / 2} y={-shh / 2} width={sw} height={shh} fill="#0E1729" />
				<g opacity={screen}>
					{Array.from({length: Math.ceil(shh / pitch) + 2}).map((_, k) => {
						const i = first + k;
						const [outer, inner] = CARDS[((i % CARDS.length) + CARDS.length) % CARDS.length];
						const y = -shh / 2 + gap + k * pitch - off;
						const x = -sw / 2 + gap;
						const cw = sw - gap * 2;
						return (
							<g key={i} transform={`translate(${x} ${y})`}>
								<rect width={cw} height={cardH} rx={cw * 0.06} fill={outer} />
								<circle cx={cw * 0.07} cy={cardH * 0.11} r={cw * 0.032} fill="rgba(255,255,255,0.75)" />
								<rect x={cw * 0.13} y={cardH * 0.085} width={cw * 0.36} height={cardH * 0.05} rx={4} fill="rgba(255,255,255,0.55)" />
								<rect x={cw * 0.04} y={cardH * 0.24} width={cw * 0.92} height={cardH * 0.68} rx={cw * 0.04} fill={inner} />
								<path d={`M ${cw * 0.46} ${cardH * 0.47} L ${cw * 0.46} ${cardH * 0.69} L ${cw * 0.56} ${cardH * 0.58} Z`} fill="rgba(255,255,255,0.9)" />
							</g>
						);
					})}
					{notif > 0 ? (
						<g opacity={notif} transform={`translate(0 ${-shh / 2 + 70 - (1 - notif) * 40})`}>
							<rect x={-sw / 2 + 24} y={-36} width={sw - 48} height={72} rx={20} fill="rgba(255,255,255,0.95)" />
							<circle cx={-sw / 2 + 70} cy={0} r={18} fill={C.alarm} />
							<rect x={-sw / 2 + 104} y={-14} width={sw * 0.5} height={12} rx={6} fill="#9AA3B5" />
							<rect x={-sw / 2 + 104} y={6} width={sw * 0.3} height={10} rx={5} fill="#C5CBD8" />
						</g>
					) : null}
				</g>
				{/* screen reflection */}
				<path d={`M ${-sw / 2} ${-shh / 2} L ${sw * 0.15} ${-shh / 2} L ${-sw / 2} ${shh * 0.15} Z`} fill="#fff" opacity={0.04 + 0.03 * screen} />
			</g>
			<rect x={-w * 0.12} y={-h / 2 + pad * 0.55} width={w * 0.24} height={pad * 0.55} rx={pad * 0.3} fill="#000" />
			{showHand ? (
				<g>
					{/* fingers wrapped around the right edge */}
					{[0.06, 0.15, 0.24].map((k, i) => (
						<rect key={i} x={w / 2 - w * 0.05} y={-h * 0.02 + h * k} width={w * 0.13} height={w * 0.085} rx={w * 0.042} fill={C.skin} stroke="#C3CBDB" strokeWidth={2} />
					))}
					{/* thumb on the screen */}
					<g transform={`translate(${w * 0.12} ${h * 0.34 + thumbY}) rotate(-32) scale(${1 - thumbPress * 0.04})`}>
						<rect x={-w * 0.075} y={-w * 0.36} width={w * 0.15} height={w * 0.52} rx={w * 0.075} fill={C.skin} stroke="#C3CBDB" strokeWidth={2} />
						<rect x={-w * 0.05} y={-w * 0.335} width={w * 0.1} height={w * 0.1} rx={w * 0.04} fill="#E6EAF2" />
					</g>
				</g>
			) : null}
		</g>
	);
};
