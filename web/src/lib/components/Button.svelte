<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLButtonAttributes } from 'svelte/elements';

	type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';
	type Size = 'sm' | 'md';

	let {
		variant = 'primary',
		size = 'md',
		loading = false,
		type = 'button',
		class: klass = '',
		children,
		...rest
	}: HTMLButtonAttributes & {
		variant?: Variant;
		size?: Size;
		loading?: boolean;
		children: Snippet;
	} = $props();

	const variants: Record<Variant, string> = {
		primary:
			'bg-brand-600 text-white hover:bg-brand-700 focus-visible:ring-brand-500 disabled:bg-brand-400',
		secondary:
			'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 focus-visible:ring-brand-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800',
		danger: 'bg-red-600 text-white hover:bg-red-700 focus-visible:ring-red-500 disabled:bg-red-400',
		ghost:
			'text-slate-600 hover:bg-slate-100 focus-visible:ring-brand-500 dark:text-slate-300 dark:hover:bg-slate-800'
	};
	const sizes: Record<Size, string> = { sm: 'px-2.5 py-1.5 text-xs', md: 'px-4 py-2 text-sm' };
</script>

<button
	{type}
	class="inline-flex items-center justify-center gap-2 rounded-lg font-medium shadow-sm transition focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-70 dark:focus-visible:ring-offset-slate-900 {variants[
		variant
	]} {sizes[size]} {klass}"
	disabled={loading || rest.disabled}
	{...rest}
>
	{#if loading}
		<svg class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
			<circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
			<path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
		</svg>
	{/if}
	{@render children()}
</button>
