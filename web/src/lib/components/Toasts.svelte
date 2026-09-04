<script lang="ts">
	import { dismiss, toasts } from '$lib/stores/toast.svelte';

	const styles = {
		success:
			'border-emerald-300 bg-emerald-50 text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-100',
		error:
			'border-red-300 bg-red-50 text-red-900 dark:border-red-800 dark:bg-red-950 dark:text-red-100',
		warning:
			'border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-100',
		info: 'border-brand-300 bg-brand-50 text-brand-900 dark:border-brand-800 dark:bg-slate-900 dark:text-brand-100'
	};
</script>

<div
	class="pointer-events-none fixed top-4 right-4 z-50 flex w-80 flex-col gap-2"
	aria-live="polite"
>
	{#each toasts as t (t.id)}
		<div
			class="pointer-events-auto flex items-start justify-between gap-3 rounded-lg border px-4 py-3 text-sm shadow-lg {styles[
				t.kind
			]}"
			role="status"
		>
			<span>{t.message}</span>
			<button
				type="button"
				class="opacity-60 hover:opacity-100"
				aria-label="Fechar"
				onclick={() => dismiss(t.id)}>✕</button
			>
		</div>
	{/each}
</div>
