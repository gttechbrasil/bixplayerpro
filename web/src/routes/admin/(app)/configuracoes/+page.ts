import { get } from '$lib/api';
import type { GatewayInfo, Settings } from '$lib/types';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const [settings, gateway] = await Promise.all([
		get<Settings>('admin/settings', undefined, fetch),
		get<GatewayInfo>('admin/settings/gateway', undefined, fetch)
	]);
	return { settings, gateway };
};
