export type ToastKind = 'success' | 'error' | 'warning' | 'info';

export interface ToastItem {
	id: number;
	kind: ToastKind;
	message: string;
}

let seq = 0;
export const toasts = $state<ToastItem[]>([]);

export function dismiss(id: number) {
	const idx = toasts.findIndex((t) => t.id === id);
	if (idx >= 0) toasts.splice(idx, 1);
}

function push(kind: ToastKind, message: string, timeout = 4500) {
	const id = ++seq;
	toasts.push({ id, kind, message });
	if (timeout > 0) setTimeout(() => dismiss(id), timeout);
}

export const toast = {
	success: (m: string) => push('success', m),
	error: (m: string) => push('error', m, 7000),
	warning: (m: string) => push('warning', m),
	info: (m: string) => push('info', m)
};
