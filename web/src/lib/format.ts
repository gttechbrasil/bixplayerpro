const dateFmt = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' });
const dateTimeFmt = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const moneyFmt = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/** `52998224725` -> `529.982.247-25`; also masks a CPF while it is being typed. */
export function formatCpf(value: string | null | undefined): string {
	const d = (value ?? '').replace(/\D/g, '').slice(0, 11);
	return [d.slice(0, 3), d.slice(3, 6), d.slice(6, 9)].filter(Boolean).join('.') +
		(d.length > 9 ? `-${d.slice(9)}` : '');
}

export function formatDate(value: string | null | undefined): string {
	if (!value) return '—';
	const [y, m, d] = value.slice(0, 10).split('-').map(Number);
	return dateFmt.format(new Date(y, m - 1, d));
}

export function formatDateTime(value: string | null | undefined): string {
	if (!value) return '—';
	return dateTimeFmt.format(new Date(value));
}

export function formatMoney(value: string | number | null | undefined): string {
	if (value === null || value === undefined || value === '') return '—';
	return moneyFmt.format(Number(value));
}

export function daysUntil(value: string | null | undefined): number | null {
	if (!value) return null;
	const [y, m, d] = value.slice(0, 10).split('-').map(Number);
	const target = new Date(y, m - 1, d);
	const today = new Date();
	today.setHours(0, 0, 0, 0);
	return Math.round((target.getTime() - today.getTime()) / 86_400_000);
}

export type ExpirationState = 'none' | 'ok' | 'soon' | 'expired';

export function expirationState(value: string | null | undefined): ExpirationState {
	const days = daysUntil(value);
	if (days === null) return 'none';
	if (days < 0) return 'expired';
	if (days <= 7) return 'soon';
	return 'ok';
}
