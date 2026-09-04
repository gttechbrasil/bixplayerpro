export interface Page<T> {
	items: T[];
	total: number;
	page: number;
	per_page: number;
}

export interface AdminUser {
	id: number;
	username: string;
}

export interface Reseller {
	id: number;
	username: string;
	name: string;
	credits: number;
	expires_at: string | null;
	is_blocked: boolean;
	logo_url: string | null;
	bg_url: string | null;
	qr_content: string | null;
	theme: string;
	auto_ads: boolean;
	created_at: string;
	updated_at: string;
	devices_count: number;
}

export interface LedgerEntry {
	id: number;
	reseller_id: number | null;
	delta: number;
	balance_after: number;
	reason: string;
	note: string | null;
	ref: string | null;
	actor_type: string;
	actor_id: number | null;
	created_at: string;
}

export interface Package {
	months: number;
	price: string;
}

export interface Platform {
	name: string;
	credits_enabled: boolean;
}

export interface Settings {
	credits_enabled: boolean;
	monthly_price: string;
	packages: Package[];
	min_app_version: string;
	apk_url: string;
	platform_name: string;
}

export interface Dashboard {
	resellers_total: number;
	resellers_active: number;
	resellers_blocked: number;
	resellers_expired: number;
	devices_total: number;
	devices_registered: number;
	devices_active: number;
	devices_seen_24h: number;
	payments_month_count: number;
	payments_month_amount: string;
	payments_pending: number;
}

export interface Payment {
	id: number;
	reseller_id: number | null;
	reseller_username: string | null;
	provider: string;
	provider_id: string | null;
	months: number;
	amount: string;
	status: 'pending' | 'approved' | 'cancelled' | 'expired';
	paid_at: string | null;
	previous_expires_at: string | null;
	new_expires_at: string | null;
	created_at: string;
}

export interface AuditEntry {
	id: number;
	actor_type: string;
	actor_id: number | null;
	action: string;
	target: string | null;
	payload: Record<string, unknown> | null;
	ip: string | null;
	created_at: string;
}
