<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		open = $bindable(false),
		title,
		size = 'md',
		children,
		footer
	}: {
		open?: boolean;
		title: string;
		size?: 'sm' | 'md' | 'lg';
		children: Snippet;
		footer?: Snippet;
	} = $props();

	const sizes = { sm: 'max-w-sm', md: 'max-w-lg', lg: 'max-w-3xl' };

	function close() {
		open = false;
	}
	function onkeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') close();
	}
</script>

<svelte:window {onkeydown} />

{#if open}
	<div class="fixed inset-0 z-40 flex items-center justify-center p-4">
		<button
			type="button"
			class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm"
			aria-label="Fechar"
			onclick={close}
		></button>
		<div
			role="dialog"
			aria-modal="true"
			aria-labelledby="modal-title"
			class="card relative z-50 w-full {sizes[size]} max-h-[90vh] overflow-y-auto"
		>
			<div
				class="flex items-center justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800"
			>
				<h2 id="modal-title" class="text-base font-semibold">{title}</h2>
				<button
					type="button"
					class="rounded-md p-1 text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800"
					aria-label="Fechar"
					onclick={close}>✕</button
				>
			</div>
			<div class="px-5 py-4">{@render children()}</div>
			{#if footer}
				<div
					class="flex justify-end gap-2 border-t border-slate-200 px-5 py-3 dark:border-slate-800"
				>
					{@render footer()}
				</div>
			{/if}
		</div>
	</div>
{/if}
