<script lang="ts">
	import { goto } from '$app/navigation';
	import { errorMessage, put } from '$lib/api';
	import DeviceForm, { type DeviceFormValues } from '$lib/components/DeviceForm.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { formatDateTime } from '$lib/format';
	import { toast } from '$lib/stores/toast.svelte';
	import type { ResellerDevice } from '$lib/types';

	let { data } = $props();
	let loading = $state(false);

	async function submit(v: DeviceFormValues) {
		loading = true;
		try {
			await put<ResellerDevice>(`reseller/devices/${data.device.id}`, {
				client_name: v.client_name.trim() || null,
				playlist_name: v.playlist_name.trim(),
				playlist_url: v.playlist_url.trim(),
				license_expires_at: v.license_expires_at || null
			});
			toast.success('Dispositivo atualizado.');
			await goto('/painel/dispositivos');
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			loading = false;
		}
	}
</script>

<PageHeader
	title="Editar dispositivo"
	subtitle="{data.device.mac_address} · {data.device.connected
		? `último acesso ${formatDateTime(data.device.last_seen_at)}`
		: 'o app ainda não conectou'}"
/>
<div class="card max-w-2xl p-6">
	{#key data.device.id}
		<DeviceForm device={data.device} {loading} onsubmit={submit} />
	{/key}
</div>
