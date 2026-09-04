<script lang="ts">
	import type { HTMLInputAttributes } from 'svelte/elements';

	let {
		label,
		error,
		hint,
		value = $bindable(),
		id = crypto.randomUUID(),
		class: klass = '',
		...rest
	}: HTMLInputAttributes & { label?: string; error?: string; hint?: string } = $props();
</script>

<div class={klass}>
	{#if label}
		<label for={id} class="label">{label}</label>
	{/if}
	<input {id} class="input" class:border-red-500={!!error} bind:value {...rest} />
	{#if error}
		<p class="mt-1 text-xs text-red-600">{error}</p>
	{:else if hint}
		<p class="mt-1 text-xs text-slate-500">{hint}</p>
	{/if}
</div>
