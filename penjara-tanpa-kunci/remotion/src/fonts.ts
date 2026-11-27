import {continueRender, delayRender, staticFile} from 'remotion';

// Nunito is the typeface used throughout the original render (captions, HUD, calendar).
const handle = delayRender('Loading Nunito');
const faces = [
	new FontFace('Nunito', `url(${staticFile('fonts/nunito-latin-800-normal.woff2')})`, {weight: '800'}),
	new FontFace('Nunito', `url(${staticFile('fonts/nunito-latin-900-normal.woff2')})`, {weight: '900'}),
];

Promise.all(faces.map((f) => f.load()))
	.then((loaded) => {
		loaded.forEach((f) => document.fonts.add(f));
		continueRender(handle);
	})
	.catch((err) => {
		console.error(err);
		continueRender(handle);
	});

export const FONT = 'Nunito, sans-serif';
