import { get } from '$lib/api';
import type { Plans } from '$lib/types';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, url }) => {
	const plans = await get<Plans>('reseller/billing/plans', undefined, fetch);
	return { plans, expiredNotice: url.searchParams.get('vencida') === '1' };
};
