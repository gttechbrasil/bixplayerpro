<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { page } from '$app/state';
	import { del, errorMessage, post } from '$lib/api';
	import Badge from '$lib/components/Badge.svelte';
	import Button from '$lib/components/Button.svelte';
	import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
	import DataTable, { type Query } from '$lib/components/DataTable.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { formatDate, formatDateTime } from '$lib/format';
	import { updateQuery } from '$lib/query';
	import { toast } from '$lib/stores/toast.svelte';
	import type { ResellerDevice } from '$lib/types';

	let { data } = $props();

	const columns = [
		{ key: 'client', label: 'Cliente' },
		{ key: 'mac', label: 'MAC' },
		{ key: 'playlist', label: 'Playlist' },
		{ key: 'license', label: 'Licença' },
		{ key: 'seen', label: 'Último acesso' },
		{ key: 'actions', label: '', class: 'text-right' }
	];

	let selected = $state(new Set<number>());
	let deleteTarget = $state<ResellerDevice | null>(null);
	let deleteOpen = $state(false);
	let batchOpen = $state(false);

	function onchange(q: Query) {
		updateQuery(page.url, { ...q });
	}

	function askDelete(d: ResellerDevice) {
		deleteTarget = d;
		deleteOpen = true;
	}

	async function removeOne() {
		if (!deleteTarget) return;
		try {
			await del(`reseller/devices/${deleteTarget.id}`);
			toast.success('Dispositivo excluído.');
			selected.delete(deleteTarget.id);
			await invalidateAll();
		} catch (err) {
			toast.error(errorMessage(err));
			throw err;
		}
	}

	async function removeBatch() {
		try {
			const res = await post<{ deleted: number; message: string }>(
				'reseller/devices/batch-delete',
				{ ids: [...selected] }
			);
			toast.success(res.message);
			selected = new Set();
			await invalidateAll();
		} catch (err) {
			toast.error(errorMessage(err));
			throw err;
		}
	}
</script>

<PageHeader title="Dispositivos" subtitle="{data.result.total} cadastrados">
	{#snippet actions()}
		{#if selected.size > 0}
			<Button variant="danger" onclick={() => (batchOpen = true)}>
				Excluir selecionados ({selected.size})
			</Button>
		{/if}
		<a
			href="/painel/dispositivos/novo"
			class="inline-flex items-center rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-brand-700"
			>+ Adicionar dispositivo</a
		>
	{/snippet}
</PageHeader>

<DataTable
	{columns}
	items={data.result.items}
	total={data.result.total}
	page={data.result.page}
	perPage={data.result.per_page}
	search={data.params.search}
	searchPlaceholder="Pesquise por MAC ou nome…"
	empty="Nenhum dispositivo cadastrado. Clique em “Adicionar dispositivo”."
	selectable
	bind:selected
	{onchange}
>
	{#snippet filters()}
		<select
			class="input w-auto"
			aria-label="Filtrar por status"
			value={data.params.status}
			onchange={(e) => updateQuery(page.url, { status: e.currentTarget.value, page: 1 })}
		>
			<option value="">Todos</option>
			<option value="active">Licença ativa</option>
			<option value="expired">Licença vencida</option>
		</select>
	{/snippet}
	{#snippet row(d)}
		<td class="table-td">
			<a
				class="font-medium text-brand-600 hover:underline dark:text-brand-300"
				href="/painel/dispositivos/{d.id}"
			>
				{d.client_name || '(sem nome)'}
			</a>
		</td>
		<td class="table-td font-mono text-xs">
			{d.mac_address}
			{#if d.connected}
				<Badge tone="green">conectado</Badge>
			{:else}
				<Badge tone="gray">nunca conectou</Badge>
			{/if}
		</td>
		<td class="table-td">
			<span class="block">{d.playlist_name ?? '—'}</span>
			<span class="block text-xs text-slate-500">{d.playlist_host ?? ''}</span>
		</td>
		<td class="table-td">
			{#if d.status === 'expired'}
				<Badge tone="red">Vencida em {formatDate(d.license_expires_at)}</Badge>
			{:else}
				{d.license_expires_at ? formatDate(d.license_expires_at) : 'Vitalícia'}
			{/if}
		</td>
		<td class="table-td text-xs text-slate-500">{formatDateTime(d.last_seen_at)}</td>
		<td class="table-td text-right whitespace-nowrap">
			<a
				class="text-sm font-medium text-brand-600 hover:underline dark:text-brand-300"
				href="/painel/dispositivos/{d.id}">Editar</a
			>
			<button
				type="button"
				class="ml-3 text-sm font-medium text-red-600 hover:underline"
				onclick={() => askDelete(d)}>Excluir</button
			>
		</td>
	{/snippet}
</DataTable>

<ConfirmDialog
	bind:open={deleteOpen}
	title="Excluir dispositivo"
	message="Excluir o dispositivo {deleteTarget?.mac_address} ({deleteTarget?.client_name ||
		'sem nome'})? O app deixará de receber a playlist."
	confirmLabel="Excluir"
	danger
	onconfirm={removeOne}
/>

<ConfirmDialog
	bind:open={batchOpen}
	title="Excluir selecionados"
	message="Excluir {selected.size} dispositivo(s) selecionado(s)? Esta ação não pode ser desfeita."
	confirmLabel="Excluir"
	danger
	onconfirm={removeBatch}
/>
