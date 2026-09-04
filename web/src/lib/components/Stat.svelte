<script lang="ts">
	let { label, value, hint }: { label: string; value: string | number; hint?: string } = $props();
</script>

<div class="card p-5">
	<p class="text-sm font-medium text-slate-500 dark:text-slate-400">{label}</p>
	<p class="mt-2 text-3xl font-semibold tracking-tight">{value}</p>
	{#if hint}
		<p class="mt-1 text-xs text-slate-500 dark:text-slate-400">{hint}</p>
	{/if}
</div>
