import React from 'react';
import {AbsoluteFill, Easing, interpolate, useCurrentFrame} from 'remotion';
import {FONT} from '../fonts';

// New opening (frames 0-76, before the first cut to the bedroom).
// v3 problems: 1.7 s with no voice and only a small title pill that collided with the phone and the
// head outline. Now: title + a hook line readable from the very first frame (also the thumbnail),
// kept below y=250 so the TikTok/Reels top interface does not cover it,
// in the same visual language (night, phone scroll). From frame 52 the ORIGINAL caption pill
// ("Setiap malam ia membuat janji", karaoke) shows through a hole, so the cut at frame 77 is seamless.

const W = 1080;
const H = 1920;
const PILL = {x0: 82, x1: 998, y0: 1458, y1: 1560, r: 22};
const GOLD = '#E4BF79';

const CARD_COLORS = [
	['#4C74C6', '#6A8CD4'],
	['#6A59BE', '#8070CB'],
	['#2C8A95', '#4B9FA9'],
	['#7A69C8', '#9283D3'],
];

function roundRect(x0: number, y0: number, x1: number, y1: number, r: number) {
	return `M${x0 + r} ${y0} H${x1 - r} A${r} ${r} 0 0 1 ${x1} ${y0 + r} V${y1 - r} A${r} ${r} 0 0 1 ${x1 - r} ${y1} H${x0 + r} A${r} ${r} 0 0 1 ${x0} ${y1 - r} V${y0 + r} A${r} ${r} 0 0 1 ${x0 + r} ${y0} Z`;
}

const Card: React.FC<{i: number; y: number}> = ({i, y}) => {
	const [outer, inner] = CARD_COLORS[((i % 4) + 4) % 4];
	return (
		<div style={{position: 'absolute', left: 22, top: y, width: 552, height: 262, borderRadius: 30, background: outer}}>
			<div style={{position: 'absolute', left: 24, top: 22, width: 34, height: 34, borderRadius: 17, background: 'rgba(255,255,255,0.72)'}} />
			<div style={{position: 'absolute', left: 72, top: 33, width: 190, height: 12, borderRadius: 6, background: 'rgba(255,255,255,0.55)'}} />
			<div style={{position: 'absolute', left: 24, top: 70, width: 504, height: 170, borderRadius: 22, background: inner}}>
				<div
					style={{
						position: 'absolute',
						left: 236,
						top: 62,
						width: 0,
						height: 0,
						borderTop: '24px solid transparent',
						borderBottom: '24px solid transparent',
						borderLeft: '40px solid rgba(255,255,255,0.88)',
					}}
				/>
			</div>
		</div>
	);
};

const Phone: React.FC<{frame: number}> = ({frame}) => {
	const pitch = 262 + 24;
	const scroll = frame * 13.5; // px per frame, ~1.4 cards per second like the original
	const first = Math.floor(scroll / pitch);
	const off = scroll - first * pitch;
	// thumb swipe cycle synced to the scroll
	const cyc = (frame % 21) / 21;
	const thumbY = interpolate(cyc, [0, 0.75, 1], [70, -60, 70], {easing: Easing.inOut(Easing.quad)});
	return (
		<div style={{position: 'absolute', left: 585 - 320, top: 1250 - 590, width: 640, height: 1180, transform: 'rotate(6deg)'}}>
			<div style={{position: 'absolute', inset: 0, borderRadius: 78, background: '#05080F', boxShadow: '0 40px 90px rgba(0,0,0,0.55)'}} />
			<div style={{position: 'absolute', left: 16, top: 16, right: 16, bottom: 16, borderRadius: 64, background: '#0E1729', overflow: 'hidden'}}>
				{Array.from({length: 6}).map((_, k) => (
					<Card key={first + k} i={first + k} y={18 + k * pitch - off} />
				))}
			</div>
			{/* thumb */}
			<div style={{position: 'absolute', left: 150, top: 640 + thumbY, width: 230, height: 116, transform: 'rotate(-22deg)'}}>
				<div style={{position: 'absolute', inset: 0, borderRadius: '50%', background: '#EEF1F7', boxShadow: '0 10px 24px rgba(0,0,0,0.25)'}} />
				<div style={{position: 'absolute', left: 120, top: 22, width: 86, height: 56, borderRadius: '50%', background: '#D4DAE6'}} />
			</div>
			{/* fingers holding the phone */}
			<div style={{position: 'absolute', left: 596, top: 470, width: 92, height: 180, borderRadius: '50%', background: '#D9DDE6'}} />
		</div>
	);
};

export const Hook: React.FC = () => {
	const frame = useCurrentFrame();
	const hole = frame >= 52;
	const inset = 4;
	const clip = hole
		? `path(evenodd, '${`M0 0 H${W} V${H} H0 Z`} ${roundRect(PILL.x0 + inset, PILL.y0 + inset, PILL.x1 - inset, PILL.y1 - inset, PILL.r - inset)}')`
		: undefined;
	const grow = interpolate(frame, [0, 76], [1, 1.035], {easing: Easing.out(Easing.quad)});
	const glow = interpolate(frame, [0, 20, 76], [0.35, 0.6, 0.45]);
	return (
		<AbsoluteFill style={{clipPath: clip}}>
			<AbsoluteFill
				style={{
					background: 'radial-gradient(ellipse 80% 60% at 55% 56%, #21384C 0%, #13243A 40%, #0A1424 72%, #060B15 100%)',
				}}
			/>
			<Phone frame={frame} />
			{/* soft dark band behind the text so it never fights the phone */}
			<AbsoluteFill style={{background: 'linear-gradient(180deg, rgba(6,11,21,0.92) 0%, rgba(6,11,21,0.78) 26%, rgba(6,11,21,0) 38%)'}} />
			<div style={{position: 'absolute', left: 0, right: 0, top: 262, display: 'flex', justifyContent: 'center', fontFamily: FONT}}>
				<div
					style={{
						padding: '14px 34px',
						borderRadius: 999,
						border: `3px solid ${GOLD}`,
						background: 'rgba(11,16,26,0.9)',
						color: GOLD,
						fontWeight: 900,
						fontSize: 38,
						letterSpacing: '0.12em',
						lineHeight: 1,
					}}
				>
					PENJARA TANPA KUNCI
				</div>
			</div>
			<div
				style={{
					position: 'absolute',
					left: 0,
					right: 0,
					top: 372,
					textAlign: 'center',
					fontFamily: FONT,
					fontWeight: 900,
					transform: `scale(${grow})`,
					transformOrigin: '50% 30%',
				}}
			>
				<div style={{fontSize: 82, lineHeight: 1.08, color: '#FFFFFF', textShadow: '0 6px 26px rgba(0,0,0,0.55)'}}>412 kali ia berkata</div>
				<div
					style={{
						fontSize: 112,
						lineHeight: 1.12,
						color: GOLD,
						textShadow: `0 0 34px rgba(228,191,121,${glow}), 0 6px 26px rgba(0,0,0,0.55)`,
					}}
				>
					“besok”.
				</div>
			</div>
			{hole ? (
				<svg width={W} height={H} style={{position: 'absolute', inset: 0}}>
					<path d={roundRect(PILL.x0, PILL.y0, PILL.x1, PILL.y1, PILL.r)} fill="rgba(10,15,25,0.92)" />
				</svg>
			) : null}
		</AbsoluteFill>
	);
};
