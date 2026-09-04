import type { HandleFetch } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';

const API_INTERNAL_URL = (env.API_INTERNAL_URL ?? 'http://localhost:8000').replace(/\/$/, '');

/**
 * During SSR, relative `/api/...` fetches are forwarded to the backend with the
 * browser cookies, so `load` functions can use the same code on server and client.
 */
export const handleFetch: HandleFetch = async ({ event, request, fetch }) => {
	const url = new URL(request.url);
	if (url.pathname.startsWith('/api/')) {
		const target = new URL(url.pathname + url.search, API_INTERNAL_URL);
		const headers = new Headers(request.headers);
		const cookie = event.request.headers.get('cookie');
		if (cookie) headers.set('cookie', cookie);
		const ip = event.getClientAddress?.();
		if (ip) headers.set('x-forwarded-for', ip);
		request = new Request(target, {
			method: request.method,
			headers,
			body: request.body,
			// @ts-expect-error - required by undici when streaming a body
			duplex: 'half'
		});
	}
	return fetch(request);
};
