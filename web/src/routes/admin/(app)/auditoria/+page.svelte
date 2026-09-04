<script lang="ts">
	import { page } from '$app/state';
	import Badge from '$lib/components/Badge.svelte';
	import DataTable, { type Query } from '$lib/components/DataTable.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { formatDateTime } from '$lib/format';
	import { updateQuery } from '$lib/query';

	let { data } = $props();

	const columns = [
		{ key: 'created_at', label: 'Data' },
		{ key: 'actor', label: 'Ator' },
		{ key: 'action', label: 'Ação' },
		{ key: 'target', label: 'Alvo' },
		{ key: 'payload', label: 'Detalhes' },
		{ key: 'ip', label: 'IP' }
	];

	const actorTones: Record<string, 'blue' | 'green' | 'gray' | 'yellow'> = {
		admin: 'blue',
		reseller: 'green',
		device: 'gray',
		system: 'yellow'
	};
	const actorLabels: Record<string, string> = {
		admin: 'Admin',
		reseller: 'Revenda',
		device: 'Dispositivo',
		system: 'Sistema'
	};

	function onchange(q: Query) {
		updateQuery(page.url, { ...q });
	}

	function targetLink(target: string | null): string | null {
		if (!target) return null;
		const [kind, id] = target.split(':');
		if (kind === 'reseller' && id) return `/admin/revendedores/${id}`;
		return null;
	}

	function summary(payload: Record<string, unknown> | null): string {
		if (!payload) return '';
		return Object.entries(payload)
			.filter(([, v]) => v !== null && v !== undefined && v !== '')
			.map(([k, v]) => `${k}: ${typeof v === 'object' ? JSON.stringify(v) : String(v)}`)
			.join(' · ');
	}
</script>

<PageHeader title="Auditoria" subtitle="Registro de operações sensíveis" />

<DataTable
	{columns}
	items={data.result.items}
	total={data.result.total}
	page={data.result.page}
	perPage={data.result.per_page}
	search={data.params.search}
	searchPlaceholder="Pesquisar por ação ou alvo…"
	empty="Nenhum registro de auditoria."
	{onchange}
>
	{#snippet filters()}
		<select
			class="input w-auto"
			aria-label="Filtrar por ator"
			value={data.params.actor_type}
			onchange={(e) => updateQuery(page.url, { actor_type: e.currentTarget.value, page: 1 })}
		>
			<option value="">Todos os atores</option>
			<option value="admin">Admin</option>
			<option value="reseller">Revenda</option>
			<option value="device">Dispositivo</option>
			<option value="system">Sistema</option>
		</select>
		<input
			type="date"
			class="input w-auto"
			aria-label="De"
			value={data.params.from}
			onchange={(e) => updateQuery(page.url, { from: e.currentTarget.value, page: 1 })}
		/>
		<input
			type="date"
			class="input w-auto"
			aria-label="Até"
			value={data.params.to}
			onchange={(e) => updateQuery(page.url, { to: e.currentTarget.value, page: 1 })}
		/>
	{/snippet}
	{#snippet row(entry)}
		{@const link = targetLink(entry.target)}
		<td class="table-td whitespace-nowrap">{formatDateTime(entry.created_at)}</td>
		<td class="table-td whitespace-nowrap">
			<Badge tone={actorTones[entry.actor_type] ?? 'gray'}>
				{actorLabels[entry.actor_type] ?? entry.actor_type}{entry.actor_id
					? ` #${entry.actor_id}`
					: ''}
			</Badge>
		</td>
		<td class="table-td font-mono text-xs">{entry.action}</td>
		<td class="table-td font-mono text-xs">
			{#if link}
				<a class="text-brand-600 hover:underline dark:text-brand-300" href={link}>{entry.target}</a>
			{:else}
				{entry.target ?? '—'}
			{/if}
		</td>
		<td class="table-td max-w-md truncate text-xs text-slate-500" title={summary(entry.payload)}>
			{summary(entry.payload) || '—'}
		</td>
		<td class="table-td font-mono text-xs">{entry.ip ?? '—'}</td>
	{/snippet}
</DataTable>
