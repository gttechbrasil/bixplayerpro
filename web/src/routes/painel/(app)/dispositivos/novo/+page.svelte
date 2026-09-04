<script lang="ts">
	import { goto } from '$app/navigation';
	import { errorMessage, post } from '$lib/api';
	import DeviceForm, { type DeviceFormValues } from '$lib/components/DeviceForm.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { toast } from '$lib/stores/toast.svelte';
	import type { ResellerDevice } from '$lib/types';

	let loading = $state(false);

	async function submit(v: DeviceFormValues) {
		loading = true;
		try {
			await post<ResellerDevice>('reseller/devices', {
				mac_address: v.mac_address.trim(),
				client_name: v.client_name.trim() || null,
				playlist_name: v.playlist_name.trim(),
				playlist_url: v.playlist_url.trim(),
				license_expires_at: v.license_expires_at || null
			});
			toast.success('Dispositivo cadastrado.');
			await goto('/painel/dispositivos');
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			loading = false;
		}
	}
</script>

<PageHeader
	title="Adicionar dispositivo"
	subtitle="Informe o MAC exibido no app e a playlist do cliente"
/>
<div class="card max-w-2xl p-6">
	<DeviceForm {loading} onsubmit={submit} />
</div>
