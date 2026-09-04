<script lang="ts">
	import { page } from '$app/state';
	import Badge from '$lib/components/Badge.svelte';
	import DataTable, { type Query } from '$lib/components/DataTable.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { formatDate, formatDateTime, formatMoney } from '$lib/format';
	import { updateQuery } from '$lib/query';
	import type { Payment } from '$lib/types';

	let { data } = $props();

	const columns = [
		{ key: 'created_at', label: 'Data' },
		{ key: 'reseller', label: 'Revendedor' },
		{ key: 'months', label: 'Meses', class: 'text-right' },
		{ key: 'amount', label: 'Valor', class: 'text-right' },
		{ key: 'status', label: 'Status' },
		{ key: 'paid_at', label: 'Pago em' },
		{ key: 'expiration', label: 'Vencimento' },
		{ key: 'provider_id', label: 'ID no provedor' }
	];

	const statuses: Record<
		Payment['status'],
		{ tone: 'green' | 'yellow' | 'gray' | 'red'; label: string }
	> = {
		approved: { tone: 'green', label: 'Aprovado' },
		pending: { tone: 'yellow', label: 'Pendente' },
		cancelled: { tone: 'gray', label: 'Cancelado' },
		expired: { tone: 'red', label: 'Expirado' }
	};

	function onchange(q: Query) {
		updateQuery(page.url, { ...q });
	}
</script>

<PageHeader title="Pagamentos" subtitle="Renovações de revenda via Pix" />

<DataTable
	{columns}
	items={data.result.items}
	total={data.result.total}
	page={data.result.page}
	perPage={data.result.per_page}
	search={data.params.search}
	searchPlaceholder="Pesquisar por revendedor ou ID…"
	empty="Nenhum pagamento encontrado."
	{onchange}
>
	{#snippet filters()}
		<select
			class="input w-auto"
			aria-label="Filtrar por status"
			value={data.params.status}
			onchange={(e) => updateQuery(page.url, { status: e.currentTarget.value, page: 1 })}
		>
			<option value="">Todos os status</option>
			<option value="approved">Aprovados</option>
			<option value="pending">Pendentes</option>
			<option value="cancelled">Cancelados</option>
			<option value="expired">Expirados</option>
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
	{#snippet row(p)}
		{@const st = statuses[p.status] ?? { tone: 'gray', label: p.status }}
		<td class="table-td whitespace-nowrap">{formatDateTime(p.created_at)}</td>
		<td class="table-td">
			{#if p.reseller_id}
				<a
					class="text-brand-600 hover:underline dark:text-brand-300"
					href="/admin/revendedores/{p.reseller_id}"
				>
					{p.reseller_username ?? `#${p.reseller_id}`}
				</a>
			{:else}
				<span class="text-slate-400">(excluído)</span>
			{/if}
		</td>
		<td class="table-td text-right tabular-nums">{p.months}</td>
		<td class="table-td text-right font-medium tabular-nums">{formatMoney(p.amount)}</td>
		<td class="table-td"><Badge tone={st.tone}>{st.label}</Badge></td>
		<td class="table-td whitespace-nowrap">{formatDateTime(p.paid_at)}</td>
		<td class="table-td whitespace-nowrap">
			{#if p.new_expires_at}
				{formatDate(p.previous_expires_at)} → {formatDate(p.new_expires_at)}
			{:else}
				—
			{/if}
		</td>
		<td class="table-td font-mono text-xs">{p.provider_id ?? '—'}</td>
	{/snippet}
</DataTable>
