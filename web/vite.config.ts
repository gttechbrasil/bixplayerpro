import tailwindcss from '@tailwindcss/vite';
import adapter from '@sveltejs/adapter-node';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [
		tailwindcss(),
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},
			adapter: adapter()
		})
	],
	server: {
		// Local development: the API runs on :8000 (make dev). In production Caddy routes /api.
		proxy: {
			'/api': {
				target: process.env.API_INTERNAL_URL ?? 'http://localhost:8000',
				changeOrigin: false
			}
		}
	}
});
