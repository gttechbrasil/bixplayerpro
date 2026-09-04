import { get } from '$lib/api';
import type { Page, Reseller } from '$lib/types';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, url }) => {
	const q = url.searchParams;
	const params = {
		page: Number(q.get('page') ?? 1),
		per_page: Number(q.get('per_page') ?? 25),
		search: q.get('search') ?? '',
		status: q.get('status') ?? ''
	};
	const result = await get<Page<Reseller>>('admin/resellers', params, fetch);
	return { result, params };
};
