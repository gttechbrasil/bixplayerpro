<script lang="ts">
	import type { Snippet } from 'svelte';
	let { title, subtitle, actions }: { title: string; subtitle?: string; actions?: Snippet } =
		$props();
</script>

<div class="mb-6 flex flex-wrap items-end justify-between gap-3">
	<div class="min-w-0">
		<h1 class="text-xl font-semibold tracking-tight sm:text-2xl">{title}</h1>
		{#if subtitle}
			<p class="mt-1 text-sm text-slate-500 dark:text-slate-400">{subtitle}</p>
		{/if}
	</div>
	{#if actions}
		<div class="flex w-full flex-wrap gap-2 *:flex-1 sm:w-auto sm:*:flex-none">
			{@render actions()}
		</div>
	{/if}
</div>
