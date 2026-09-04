import { goto } from '$app/navigation';

/** Navigates to the current page with updated query params (empty values are dropped). */
export function updateQuery(
	current: URL,
	patch: Record<string, string | number | null | undefined>
) {
	const url = new URL(current);
	for (const [k, v] of Object.entries(patch)) {
		if (v === undefined || v === null || v === '') url.searchParams.delete(k);
		else url.searchParams.set(k, String(v));
	}
	return goto(`${url.pathname}${url.search}`, { keepFocus: true, noScroll: true });
}
