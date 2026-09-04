import { redirect } from '@sveltejs/kit';
import type { LayoutServerLoad } from './$types';
import type { Platform, ResellerUser } from '$lib/types';

// Routes an expired reseller may still use.
const EXPIRED_ALLOWED = ['/painel/renovar', '/painel/perfil'];

export const load: LayoutServerLoad = async ({ fetch, url }) => {
	const res = await fetch('/api/v1/auth/me');
	if (res.status === 401 || res.status === 403) {
		redirect(303, `/painel/login?next=${encodeURIComponent(url.pathname)}`);
	}
	if (!res.ok) throw new Error(`API indisponível (${res.status})`);
	const me = (await res.json()) as { role: string; user: ResellerUser; platform: Platform };
	if (me.role !== 'reseller') redirect(303, '/painel/login');

	if (me.user.is_expired && !EXPIRED_ALLOWED.some((p) => url.pathname.startsWith(p))) {
		redirect(303, '/painel/renovar?vencida=1');
	}
	return { user: me.user, platform: me.platform };
};
