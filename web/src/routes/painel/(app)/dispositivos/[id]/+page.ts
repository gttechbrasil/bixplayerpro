import { error } from '@sveltejs/kit';
import { ApiError, get } from '$lib/api';
import type { ResellerDevice } from '$lib/types';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, params }) => {
	try {
		const device = await get<ResellerDevice>(`reseller/devices/${params.id}`, undefined, fetch);
		return { device };
	} catch (err) {
		if (err instanceof ApiError && err.status === 404) error(404, 'Dispositivo não encontrado.');
		throw err;
	}
};
