import React from 'react';
import {Composition} from 'remotion';
import {Main} from './Main';

export const Root: React.FC = () => (
	<Composition id="PenjaraV4" component={Main} durationInFrames={5724} fps={30} width={1080} height={1920} />
);
