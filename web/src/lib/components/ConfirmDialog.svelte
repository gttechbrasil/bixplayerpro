<script lang="ts">
	import Button from './Button.svelte';
	import Modal from './Modal.svelte';

	let {
		open = $bindable(false),
		title = 'Confirmar',
		message,
		confirmLabel = 'Confirmar',
		danger = false,
		onconfirm
	}: {
		open?: boolean;
		title?: string;
		message: string;
		confirmLabel?: string;
		danger?: boolean;
		onconfirm: () => Promise<void> | void;
	} = $props();

	let loading = $state(false);

	async function confirm() {
		loading = true;
		try {
			await onconfirm();
			open = false;
		} finally {
			loading = false;
		}
	}
</script>

<Modal bind:open {title} size="sm">
	<p class="text-sm text-slate-600 dark:text-slate-300">{message}</p>
	{#snippet footer()}
		<Button variant="secondary" onclick={() => (open = false)}>Cancelar</Button>
		<Button variant={danger ? 'danger' : 'primary'} {loading} onclick={confirm}
			>{confirmLabel}</Button
		>
	{/snippet}
</Modal>
