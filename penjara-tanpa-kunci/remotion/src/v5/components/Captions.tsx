import React from 'react';
import {useCurrentFrame} from 'remotion';
import caps from '../data/captions.json';
import {FONT} from '../../fonts';
import {clamp} from '../lib/anim';

// Text-only captions (no background box): white words with a soft dark outline for legibility on any
// background, karaoke reveal (spoken words full white, keywords turn gold), small pop on each spoken word.
type Word = {t: string; s: number; hl: boolean};
type Page = {text: string; words: Word[]; in: number; out: number};
const PAGES = caps as Page[];

const outline = (px: number, a: number) => {
	const sh: string[] = [];
	for (let i = 0; i < 16; i++) {
		const ang = (i / 16) * Math.PI * 2;
		sh.push(`${(Math.cos(ang) * px).toFixed(2)}px ${(Math.sin(ang) * px).toFixed(2)}px 0 rgba(8,12,20,${a})`);
	}
	sh.push(`0 4px 14px rgba(0,0,0,${a * 0.6})`);
	return sh.join(',');
};
const OUTLINE = outline(3.2, 0.92);

export const Captions: React.FC<{y?: number; hidden?: (t: number) => boolean}> = ({y = 1500, hidden}) => {
	const f = useCurrentFrame();
	const t = f / 30;
	if (hidden && hidden(t)) return null;
	const page = PAGES.find((p) => t >= p.in && t < p.out);
	if (!page) return null;
	const fadeIn = clamp((t - page.in) / 0.13);
	const fadeOut = clamp((page.out - t) / 0.13);
	const op = Math.min(fadeIn, fadeOut);
	return (
		<div
			style={{
				position: 'absolute', left: 70, right: 70, top: y, transform: `translateY(-50%) translateY(${(1 - fadeIn) * 10}px)`,
				display: 'flex', flexWrap: 'wrap', justifyContent: 'center', alignItems: 'baseline', gap: '0 18px',
				fontFamily: FONT, fontWeight: 900, fontSize: 60, lineHeight: 1.18, opacity: op, textAlign: 'center',
			}}
		>
			{page.words.map((w, i) => {
				const spoken = t >= w.s - 0.02;
				const k = clamp((t - w.s) / 0.16);
				// pop never grows a word into its neighbour: at most ~4 px per side, whatever the word length
				const amp = Math.min(0.07, 8 / (w.t.length * 30));
				const pop = spoken ? 1 + amp * (1 - k) : 1;
				const color = spoken ? (w.hl ? '#F2C14E' : '#FFFFFF') : 'rgba(255,255,255,0.58)';
				return (
					<span key={i} style={{display: 'inline-block', color, textShadow: OUTLINE, transform: `scale(${pop})`, transformOrigin: '50% 70%'}}>
						{w.t}
					</span>
				);
			})}
		</div>
	);
};
