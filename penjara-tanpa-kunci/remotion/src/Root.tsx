import React from 'react';
import {Composition} from 'remotion';
import {Main} from './Main';
import {RigTest} from './v5/RigTest';
import {Main5} from './v5/Main5';

export const Root: React.FC = () => (
	<>
		<Composition id="PenjaraV4" component={Main} durationInFrames={5724} fps={30} width={1080} height={1920} />
		<Composition id="RigTest" component={RigTest} durationInFrames={60} fps={30} width={1080} height={1920} />
		<Composition id="PenjaraV5" component={Main5} durationInFrames={5724} fps={30} width={1080} height={1920} />
	</>
);
