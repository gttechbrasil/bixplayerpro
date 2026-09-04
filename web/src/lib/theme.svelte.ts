import { browser } from '$app/environment';

export type Theme = 'light' | 'dark';

function initial(): Theme {
	if (!browser) return 'light';
	const stored = localStorage.getItem('theme');
	if (stored === 'light' || stored === 'dark') return stored;
	return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export const theme = $state<{ value: Theme }>({ value: initial() });

export function setTheme(value: Theme) {
	theme.value = value;
	if (browser) {
		localStorage.setItem('theme', value);
		document.documentElement.dataset.theme = value;
	}
}

export function toggleTheme() {
	setTheme(theme.value === 'dark' ? 'light' : 'dark');
}
