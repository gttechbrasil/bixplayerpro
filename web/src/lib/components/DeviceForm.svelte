<script lang="ts">
	import Button from '$lib/components/Button.svelte';
	import Input from '$lib/components/Input.svelte';
	import type { ResellerDevice } from '$lib/types';

	export interface DeviceFormValues {
		mac_address: string;
		client_name: string;
		playlist_name: string;
		playlist_url: string;
		license_expires_at: string;
	}

	let {
		device = null,
		loading = false,
		onsubmit
	}: {
		device?: ResellerDevice | null;
		loading?: boolean;
		onsubmit: (values: DeviceFormValues) => Promise<void> | void;
	} = $props();

	// Initial values only: the parent re-mounts the form with {#key} when the device changes.
	// svelte-ignore state_referenced_locally
	let values = $state<DeviceFormValues>({
		mac_address: device?.mac_address ?? '',
		client_name: device?.client_name ?? '',
		playlist_name: device?.playlist_name ?? '',
		playlist_url: device?.playlist_url ?? '',
		license_expires_at: device?.license_expires_at ?? '2050-01-01'
	});

	function submit(e: SubmitEvent) {
		e.preventDefault();
		onsubmit(values);
	}
</script>

<form class="space-y-4" onsubmit={submit}>
	<Input
		label="MAC do dispositivo"
		placeholder="00:11:22:33:44:55"
		required
		maxlength={17}
		disabled={device !== null}
		hint={device ? 'O MAC não pode ser alterado.' : 'Exibido na tela inicial do app.'}
		bind:value={values.mac_address}
	/>
	<Input label="Nome do cliente" placeholder="Ex: João Silva" bind:value={values.client_name} />
	<Input label="Nome da playlist" required bind:value={values.playlist_name} />
	<Input
		label="URL da playlist (M3U ou Xtream)"
		required
		placeholder="http://servidor/get.php?username=...&password=..."
		hint="Para Xtream, usuário e senha são extraídos da URL e a senha fica cifrada."
		bind:value={values.playlist_url}
	/>
	<Input
		label="Licença válida até"
		type="date"
		hint="Vazio = vitalícia"
		bind:value={values.license_expires_at}
	/>
	<div class="flex gap-2">
		<Button type="submit" {loading}>{device ? 'Atualizar' : 'Cadastrar'}</Button>
		<a
			href="/painel/dispositivos"
			class="inline-flex items-center rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
			>Cancelar</a
		>
	</div>
</form>
