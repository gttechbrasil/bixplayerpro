<script lang="ts">
	import { daysUntil, expirationState, formatDate } from '$lib/format';

	let { expiresAt, onclick }: { expiresAt: string | null; onclick?: () => void } = $props();

	const state = $derived(expirationState(expiresAt));
	const days = $derived(daysUntil(expiresAt));
	const styles = {
		none: 'border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-800',
		ok: 'border-emerald-200 bg-emerald-50 dark:border-emerald-900 dark:bg-emerald-950/40',
		soon: 'border-amber-300 bg-amber-50 dark:border-amber-900 dark:bg-amber-950/40',
		expired: 'border-red-300 bg-red-50 dark:border-red-900 dark:bg-red-950/40'
	};
	const labels = $derived({
		none: 'Sem vencimento',
		ok: 'Em dia',
		soon: days === 0 ? 'Vence hoje' : `Vence em ${days} dia(s)`,
		expired: 'Vencida'
	});
</script>

{#if state === 'none'}
	<div class="rounded-lg border p-3 text-left {styles.none}">
		<p class="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">Vencimento</p>
		<p class="mt-1 text-sm font-medium">{labels.none}</p>
	</div>
{:else}
	<button
		type="button"
		class="w-full rounded-lg border p-3 text-left transition hover:brightness-95 {styles[state]}"
		{onclick}
		title="Renovar revenda"
	>
		<p class="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">Vencimento</p>
		<p class="mt-1 text-lg font-semibold">{formatDate(expiresAt)}</p>
		<p class="text-xs">{labels[state]} · <span class="underline">Renovar</span></p>
	</button>
{/if}
