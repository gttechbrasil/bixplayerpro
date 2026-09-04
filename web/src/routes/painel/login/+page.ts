import { redirect } from '@sveltejs/kit';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const res = await fetch('/api/v1/auth/me');
	if (res.ok) {
		const me = (await res.json()) as { role: string };
		if (me.role === 'reseller') redirect(303, '/painel');
	}
	return {};
};
