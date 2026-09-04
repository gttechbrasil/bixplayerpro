import { error } from '@sveltejs/kit';
import { ApiError, get } from '$lib/api';
import type { LedgerEntry, Page, Reseller } from '$lib/types';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, params, url }) => {
	const ledgerPage = Number(url.searchParams.get('lpage') ?? 1);
	try {
		const [reseller, ledger] = await Promise.all([
			get<Reseller>(`admin/resellers/${params.id}`, undefined, fetch),
			get<Page<LedgerEntry>>(
				`admin/resellers/${params.id}/credits`,
				{ page: ledgerPage, per_page: 10 },
				fetch
			)
		]);
		return { reseller, ledger };
	} catch (err) {
		if (err instanceof ApiError && err.status === 404) error(404, 'Revenda não encontrada.');
		throw err;
	}
};
