import React from 'react';
import {FONT} from '../../fonts';
import {C} from '../theme';

// "JANJI “BESOK”" counter (same device as v3, readable on a phone).
export const Hud: React.FC<{
	label?: string; value: number | string; x?: number; y?: number; w?: number; h?: number; scale?: number;
	opacity?: number; struck?: number; dim?: boolean; glow?: number; sub?: string; subOpacity?: number;
}> = ({label = 'JANJI “BESOK”', value, x = 700, y = 200, w = 314, h = 170, scale = 1, opacity = 1, struck = 0, dim = false, glow = 0, sub, subOpacity = 1}) => (
	<div style={{position: 'absolute', left: x, top: y, width: w, height: h, transform: `scale(${scale})`, transformOrigin: '50% 50%', opacity, fontFamily: FONT}}>
		<div
			style={{
				position: 'absolute', inset: 0, borderRadius: 20, background: C.hudFill,
				border: `2px solid ${glow > 0 ? `rgba(228,191,121,${0.4 + 0.6 * glow})` : C.hudBorder}`,
				boxShadow: `0 10px 30px rgba(0,0,0,0.35)${glow > 0 ? `, 0 0 ${30 * glow}px rgba(228,191,121,${0.4 * glow})` : ''}`,
			}}
		/>
		<div style={{position: 'absolute', top: 24, left: 0, right: 0, textAlign: 'center', fontWeight: 900, fontSize: 29, letterSpacing: '0.08em', color: C.hudGold, opacity: dim ? 0.6 : 1, whiteSpace: 'nowrap'}}>
			{label}
		</div>
		<div style={{position: 'absolute', right: 28, bottom: sub ? 44 : 18, fontWeight: 900, fontSize: 76, lineHeight: 1, color: dim ? '#8C95A8' : '#fff', fontVariantNumeric: 'tabular-nums'}}>
			<span style={{position: 'relative', display: 'inline-block'}}>
				{value}
				{struck > 0 ? (
					<span style={{position: 'absolute', left: -10, top: '50%', height: 8, width: `calc((100% + 20px) * ${struck})`, borderRadius: 4, background: '#E0525A', transform: 'rotate(-8deg)', transformOrigin: '0 50%'}} />
				) : null}
			</span>
		</div>
		{sub ? (
			<div style={{position: 'absolute', bottom: 14, left: 0, right: 0, textAlign: 'center', fontWeight: 900, fontSize: 21, letterSpacing: '0.06em', color: '#C9D1E0', opacity: subOpacity}}>{sub}</div>
		) : null}
	</div>
);
