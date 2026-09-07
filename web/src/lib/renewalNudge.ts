import { daysUntil } from '$lib/format';

export type NudgeLevel = 'none' | 'banner' | 'modal';

export interface RenewalNudge {
	days: number | null;
	/** `banner` from 3 days out, `modal` (once per session, on top of the banner) at 1 day or less. */
	level: NudgeLevel;
	message: string;
	/** sessionStorage key: changes with the expiration date, so a renewal re-arms the modal. */
	sessionKey: string;
}

export const BANNER_DAYS = 3;
export const MODAL_DAYS = 1;

export function renewalNudge(expiresAt: string | null | undefined): RenewalNudge {
	const days = daysUntil(expiresAt);
	if (days === null || days < 0 || days > BANNER_DAYS) {
		return { days, level: 'none', message: '', sessionKey: '' };
	}
	const message =
		days === 0
			? 'Seu painel vence hoje'
			: days === 1
				? 'Seu painel vence em 1 dia'
				: `Seu painel vence em ${days} dias`;
	return {
		days,
		level: days <= MODAL_DAYS ? 'modal' : 'banner',
		message,
		sessionKey: `renewal-nudge:${(expiresAt ?? '').slice(0, 10)}`
	};
}

/** True the first time it is called for `key` in this browser session. */
export function firstTimeThisSession(key: string): boolean {
	try {
		if (sessionStorage.getItem(key)) return false;
		sessionStorage.setItem(key, '1');
		return true;
	} catch {
		return true;
	}
}
