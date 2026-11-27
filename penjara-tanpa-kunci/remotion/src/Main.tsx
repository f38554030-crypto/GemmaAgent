import React from 'react';
import {AbsoluteFill, OffthreadVideo, Sequence, staticFile} from 'remotion';
import './fonts';
import {CalendarFix} from './components/CalendarFix';
import {DoorCaptionOutline} from './components/DoorCaptionOutline';
import {Hook} from './components/Hook';
import {HudFix} from './components/HudFix';
import {Payoff} from './components/Payoff';

// Base = the v3 render after the pixel-level repairs (tools/repair_base.py):
//   cube shadow spill removed, calendar hidden behind the thought bubble, ghost text removed.
// Everything else is layered on top here.
export const Main: React.FC = () => {
	return (
		<AbsoluteFill style={{backgroundColor: 'black'}}>
			<OffthreadVideo src={staticFile('base_repaired.mp4')} muted />
			<CalendarFix />
			<DoorCaptionOutline />
			<HudFix />
			<Payoff />
			<Sequence from={0} durationInFrames={77} layout="none">
				<Hook />
			</Sequence>
		</AbsoluteFill>
	);
};
