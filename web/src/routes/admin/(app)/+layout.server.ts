import { redirect } from '@sveltejs/kit';
import type { LayoutServerLoad } from './$types';
import type { AdminUser, Settings } from '$lib/types';

export const load: LayoutServerLoad = async ({ fetch, url }) => {
	const res = await fetch('/api/v1/auth/me');
	if (res.status === 401 || res.status === 403) {
		redirect(303, `/admin/login?next=${encodeURIComponent(url.pathname)}`);
	}
	if (!res.ok) {
		throw new Error(`API indisponível (${res.status})`);
	}
	const me = (await res.json()) as { role: string; user: AdminUser };
	if (me.role !== 'admin') redirect(303, '/admin/login');

	const settingsRes = await fetch('/api/v1/admin/settings');
	const settings = settingsRes.ok ? ((await settingsRes.json()) as Settings) : null;
	return {
		user: me.user,
		platformName: settings?.platform_name ?? 'Painel',
		creditsEnabled: settings?.credits_enabled ?? false
	};
};
