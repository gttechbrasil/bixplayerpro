<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { errorMessage, put } from '$lib/api';
	import Button from '$lib/components/Button.svelte';
	import Input from '$lib/components/Input.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { toast } from '$lib/stores/toast.svelte';

	let { data } = $props();
	// svelte-ignore state_referenced_locally
	let content = $state(data.user.qr_content ?? '');
	let saving = $state(false);

	async function save(e: SubmitEvent) {
		e.preventDefault();
		saving = true;
		try {
			await put('reseller/branding', { qr_content: content.trim() || null });
			toast.success('QR Code salvo.');
			await invalidateAll();
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			saving = false;
		}
	}
</script>

<PageHeader
	title="QR Code"
	subtitle="O app renderiza um QR Code com este conteúdo (link, WhatsApp, texto)"
/>
<form class="card max-w-2xl space-y-4 p-6" onsubmit={save}>
	<Input
		label="Conteúdo do QR Code"
		placeholder="Texto, link ou informação para o QR Code"
		maxlength={2048}
		bind:value={content}
	/>
	<Button type="submit" loading={saving}>Salvar QR Code</Button>
</form>
