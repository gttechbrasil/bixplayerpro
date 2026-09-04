import { get } from '$lib/api';
import type { Banner } from '$lib/types';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const banners = await get<Banner[]>('reseller/branding/banners', undefined, fetch);
	return { banners };
};
