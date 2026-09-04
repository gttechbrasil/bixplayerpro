import { error } from '@sveltejs/kit';
import { ApiError, get } from '$lib/api';
import type { LedgerEntry, Page, Payment, Reseller, ResellerDevice } from '$lib/types';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, params, url }) => {
	const ledgerPage = Number(url.searchParams.get('lpage') ?? 1);
	const devicesPage = Number(url.searchParams.get('dpage') ?? 1);
	const paymentsPage = Number(url.searchParams.get('ppage') ?? 1);
	try {
		const [reseller, ledger, devices, payments] = await Promise.all([
			get<Reseller>(`admin/resellers/${params.id}`, undefined, fetch),
			get<Page<LedgerEntry>>(
				`admin/resellers/${params.id}/credits`,
				{ page: ledgerPage, per_page: 10 },
				fetch
			),
			get<Page<ResellerDevice>>(
				`admin/resellers/${params.id}/devices`,
				{ page: devicesPage, per_page: 10 },
				fetch
			),
			get<Page<Payment>>(
				`admin/resellers/${params.id}/payments`,
				{ page: paymentsPage, per_page: 10 },
				fetch
			)
		]);
		return { reseller, ledger, devices, payments };
	} catch (err) {
		if (err instanceof ApiError && err.status === 404) error(404, 'Revenda não encontrada.');
		throw err;
	}
};
