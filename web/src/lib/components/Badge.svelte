<script lang="ts">
	import type { Snippet } from 'svelte';

	type Tone = 'gray' | 'green' | 'red' | 'yellow' | 'blue';
	let { tone = 'gray', children }: { tone?: Tone; children: Snippet } = $props();

	const tones: Record<Tone, string> = {
		gray: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
		green: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-300',
		red: 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300',
		yellow: 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300',
		blue: 'bg-brand-100 text-brand-800 dark:bg-brand-900/40 dark:text-brand-300'
	};
</script>

<span
	class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap {tones[
		tone
	]}"
>
	{@render children()}
</span>
