import { browser } from '$app/environment';

export class ApiError extends Error {
	status: number;
	code: string | undefined;
	constructor(status: number, message: string, code?: string) {
		super(message);
		this.status = status;
		this.code = code;
	}
}

type Fetch = typeof fetch;
export type Params = Record<string, string | number | null | undefined>;

function csrfToken(): string | null {
	if (!browser) return null;
	const match = document.cookie.match(/(?:^|;\s*)csrf_token=([^;]+)/);
	return match ? decodeURIComponent(match[1]) : null;
}

function extractMessage(status: number, body: unknown): { message: string; code?: string } {
	if (body && typeof body === 'object' && 'detail' in body) {
		const detail = (body as { detail: unknown }).detail;
		if (detail && typeof detail === 'object' && 'message' in detail) {
			const d = detail as { message: string; code?: string };
			return { message: d.message, code: d.code };
		}
		if (Array.isArray(detail)) {
			// FastAPI validation error
			const first = detail[0] as { loc?: unknown[]; msg?: string } | undefined;
			const field = first?.loc?.slice(-1)[0];
			return {
				message: `Dados inválidos${field ? ` (${field})` : ''}: ${first?.msg ?? ''}`,
				code: 'validation'
			};
		}
		if (typeof detail === 'string') return { message: detail };
	}
	if (status === 401) {
		return { message: 'Sessão expirada. Faça login novamente.', code: 'unauthorized' };
	}
	return { message: `Erro inesperado (${status}).` };
}

function safeJson(text: string): unknown {
	try {
		return JSON.parse(text);
	} catch {
		return text;
	}
}

export async function api<T>(
	path: string,
	options: { method?: string; body?: unknown; fetch?: Fetch; params?: Params } = {}
): Promise<T> {
	const method = options.method ?? 'GET';
	const doFetch = options.fetch ?? fetch;
	const headers: Record<string, string> = { Accept: 'application/json' };
	let url = path.startsWith('/') ? path : `/api/v1/${path}`;
	if (options.params) {
		const qs = new URLSearchParams();
		for (const [k, v] of Object.entries(options.params)) {
			if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
		}
		const s = qs.toString();
		if (s) url += (url.includes('?') ? '&' : '?') + s;
	}
	let body: string | undefined;
	if (options.body !== undefined) {
		headers['Content-Type'] = 'application/json';
		body = JSON.stringify(options.body);
	}
	if (method !== 'GET' && method !== 'HEAD') {
		const csrf = csrfToken();
		if (csrf) headers['X-CSRF-Token'] = csrf;
	}
	const res = await doFetch(url, { method, headers, body, credentials: 'same-origin' });
	const text = await res.text();
	const data = text ? safeJson(text) : null;
	if (!res.ok) {
		const { message, code } = extractMessage(res.status, data);
		throw new ApiError(res.status, message, code);
	}
	return data as T;
}

export const get = <T>(path: string, params?: Params, f?: Fetch) =>
	api<T>(path, { params, fetch: f });
export const post = <T>(path: string, body?: unknown) => api<T>(path, { method: 'POST', body });
export const put = <T>(path: string, body?: unknown) => api<T>(path, { method: 'PUT', body });
export const patch = <T>(path: string, body?: unknown) => api<T>(path, { method: 'PATCH', body });
export const del = <T>(path: string) => api<T>(path, { method: 'DELETE' });

/** Message for the user from any thrown value. */
export function errorMessage(err: unknown): string {
	if (err instanceof ApiError) return err.message;
	if (err instanceof Error) return err.message;
	return 'Erro inesperado.';
}
