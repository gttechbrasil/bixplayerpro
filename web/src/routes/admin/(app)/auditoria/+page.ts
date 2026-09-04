import { get } from '$lib/api';
import type { AuditEntry, Page } from '$lib/types';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, url }) => {
	const q = url.searchParams;
	const params = {
		page: Number(q.get('page') ?? 1),
		per_page: Number(q.get('per_page') ?? 25),
		search: q.get('search') ?? '',
		actor_type: q.get('actor_type') ?? '',
		action: q.get('action') ?? '',
		from: q.get('from') ?? '',
		to: q.get('to') ?? ''
	};
	const result = await get<Page<AuditEntry>>('admin/audit-log', params, fetch);
	return { result, params };
};
