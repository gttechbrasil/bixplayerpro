import { get } from '$lib/api';
import type { DnsHost } from '$lib/types';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const hosts = await get<DnsHost[]>('reseller/dns', undefined, fetch);
	return { hosts };
};
