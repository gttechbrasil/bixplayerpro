<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { page } from '$app/state';
	import { errorMessage, post } from '$lib/api';
	import Badge from '$lib/components/Badge.svelte';
	import Button from '$lib/components/Button.svelte';
	import DataTable, { type Query } from '$lib/components/DataTable.svelte';
	import Input from '$lib/components/Input.svelte';
	import Modal from '$lib/components/Modal.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { expirationState, formatDate } from '$lib/format';
	import { updateQuery } from '$lib/query';
	import { toast } from '$lib/stores/toast.svelte';
	import type { Reseller } from '$lib/types';

	let { data } = $props();

	const columns = $derived([
		{ key: 'username', label: 'Usuário' },
		{ key: 'name', label: 'Nome' },
		...(data.creditsEnabled ? [{ key: 'credits', label: 'Créditos', class: 'text-right' }] : []),
		{ key: 'devices', label: 'Dispositivos', class: 'text-right' },
		{ key: 'expires_at', label: 'Vencimento' },
		{ key: 'status', label: 'Status' },
		{ key: 'actions', label: '', class: 'text-right' }
	]);

	const statusOptions = [
		{ value: '', label: 'Todos os status' },
		{ value: 'active', label: 'Ativos' },
		{ value: 'blocked', label: 'Bloqueados' },
		{ value: 'expired', label: 'Vencidos' }
	];

	function onchange(q: Query) {
		updateQuery(page.url, { ...q });
	}

	function statusOf(r: Reseller): { tone: 'green' | 'red' | 'yellow' | 'gray'; label: string } {
		if (r.is_blocked) return { tone: 'red', label: 'Bloqueado' };
		const st = expirationState(r.expires_at);
		if (st === 'expired') return { tone: 'red', label: 'Vencido' };
		if (st === 'soon') return { tone: 'yellow', label: 'Vence em breve' };
		return { tone: 'green', label: 'Ativo' };
	}

	// ---- create modal ----------------------------------------------------------
	let createOpen = $state(false);
	let creating = $state(false);
	let form = $state({ username: '', name: '', password: '', credits: 0, expires_at: '' });

	function openCreate() {
		form = { username: '', name: '', password: '', credits: 0, expires_at: '' };
		createOpen = true;
	}

	async function create(e: SubmitEvent) {
		e.preventDefault();
		creating = true;
		try {
			await post<Reseller>('admin/resellers', {
				username: form.username.trim(),
				name: form.name.trim(),
				password: form.password,
				credits: Number(form.credits) || 0,
				expires_at: form.expires_at || null
			});
			toast.success('Revendedor criado.');
			createOpen = false;
			await invalidateAll();
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			creating = false;
		}
	}
</script>

<PageHeader title="Revendedores" subtitle="{data.result.total} cadastrados">
	{#snippet actions()}
		<Button onclick={openCreate}>+ Novo revendedor</Button>
	{/snippet}
</PageHeader>

<DataTable
	{columns}
	items={data.result.items}
	total={data.result.total}
	page={data.result.page}
	perPage={data.result.per_page}
	search={data.params.search}
	searchPlaceholder="Pesquisar por usuário ou nome…"
	empty="Nenhum revendedor encontrado."
	{onchange}
>
	{#snippet filters()}
		<select
			class="input w-auto"
			aria-label="Filtrar por status"
			value={data.params.status}
			onchange={(e) => updateQuery(page.url, { status: e.currentTarget.value, page: 1 })}
		>
			{#each statusOptions as opt (opt.value)}
				<option value={opt.value}>{opt.label}</option>
			{/each}
		</select>
	{/snippet}
	{#snippet row(r)}
		{@const st = statusOf(r)}
		<td class="table-td font-medium">
			<a
				class="text-brand-600 hover:underline dark:text-brand-300"
				href="/admin/revendedores/{r.id}"
			>
				{r.username}
			</a>
		</td>
		<td class="table-td">{r.name}</td>
		{#if data.creditsEnabled}
			<td class="table-td text-right tabular-nums">{r.credits}</td>
		{/if}
		<td class="table-td text-right tabular-nums">{r.devices_count}</td>
		<td class="table-td">{r.expires_at ? formatDate(r.expires_at) : 'Sem vencimento'}</td>
		<td class="table-td"><Badge tone={st.tone}>{st.label}</Badge></td>
		<td class="table-td text-right">
			<a
				class="text-sm font-medium text-brand-600 hover:underline dark:text-brand-300"
				href="/admin/revendedores/{r.id}">Gerenciar</a
			>
		</td>
	{/snippet}
</DataTable>

<Modal bind:open={createOpen} title="Novo revendedor">
	<form id="create-reseller" class="space-y-4" onsubmit={create}>
		<Input
			label="Usuário (login)"
			required
			minlength={3}
			pattern="[a-zA-Z0-9_.\-]+"
			hint="Letras, números, ponto, hífen e sublinhado."
			bind:value={form.username}
		/>
		<Input label="Nome" required bind:value={form.name} />
		<Input
			label="Senha inicial"
			type="password"
			required
			minlength={6}
			autocomplete="new-password"
			bind:value={form.password}
		/>
		<div class="grid grid-cols-2 gap-4">
			{#if data.creditsEnabled}
				<Input label="Créditos iniciais" type="number" min={0} bind:value={form.credits} />
			{/if}
			<Input
				label="Vencimento"
				type="date"
				bind:value={form.expires_at}
				hint="Vazio = sem vencimento"
			/>
		</div>
	</form>
	{#snippet footer()}
		<Button variant="secondary" onclick={() => (createOpen = false)}>Cancelar</Button>
		<Button type="submit" form="create-reseller" loading={creating}>Criar</Button>
	{/snippet}
</Modal>
