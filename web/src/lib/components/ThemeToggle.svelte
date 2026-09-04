<script lang="ts">
	import { theme, toggleTheme } from '$lib/theme.svelte';
</script>

<button
	type="button"
	class="rounded-lg p-2 text-slate-500 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
	onclick={toggleTheme}
	aria-label={theme.value === 'dark' ? 'Usar tema claro' : 'Usar tema escuro'}
	title={theme.value === 'dark' ? 'Tema claro' : 'Tema escuro'}
>
	{#if theme.value === 'dark'}
		<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
			><circle cx="12" cy="12" r="4" /><path
				d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4m11.4-11.4 1.4-1.4"
			/></svg
		>
	{:else}
		<svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
			><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" /></svg
		>
	{/if}
</button>
