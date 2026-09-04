<script lang="ts">
	import type { HTMLSelectAttributes } from 'svelte/elements';

	let {
		label,
		options,
		value = $bindable(),
		id = crypto.randomUUID(),
		class: klass = '',
		...rest
	}: HTMLSelectAttributes & {
		label?: string;
		options: { value: string | number; label: string }[];
	} = $props();
</script>

<div class={klass}>
	{#if label}
		<label for={id} class="label">{label}</label>
	{/if}
	<select {id} class="input" bind:value {...rest}>
		{#each options as opt (opt.value)}
			<option value={opt.value}>{opt.label}</option>
		{/each}
	</select>
</div>
