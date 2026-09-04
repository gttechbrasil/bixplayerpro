import { get } from '$lib/api';
import type { Settings } from '$lib/types';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const settings = await get<Settings>('admin/settings', undefined, fetch);
	return { settings };
};
