import { get } from '$lib/api';
import type { Dashboard } from '$lib/types';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const dashboard = await get<Dashboard>('admin/dashboard', undefined, fetch);
	return { dashboard };
};
