<script lang="ts">
	import type { Snippet } from 'svelte';
	let { title, subtitle, actions }: { title: string; subtitle?: string; actions?: Snippet } =
		$props();
</script>

<div class="mb-6 flex flex-wrap items-end justify-between gap-4">
	<div>
		<h1 class="text-2xl font-semibold tracking-tight">{title}</h1>
		{#if subtitle}
			<p class="mt-1 text-sm text-slate-500 dark:text-slate-400">{subtitle}</p>
		{/if}
	</div>
	{#if actions}
		<div class="flex gap-2">{@render actions()}</div>
	{/if}
</div>
