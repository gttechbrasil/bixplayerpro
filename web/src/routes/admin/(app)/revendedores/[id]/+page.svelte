<script lang="ts">
	import { goto, invalidateAll } from '$app/navigation';
	import { page } from '$app/state';
	import { del, errorMessage, patch, post } from '$lib/api';
	import Badge from '$lib/components/Badge.svelte';
	import Button from '$lib/components/Button.svelte';
	import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
	import Input from '$lib/components/Input.svelte';
	import Modal from '$lib/components/Modal.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import Select from '$lib/components/Select.svelte';
	import { expirationState, formatDate, formatDateTime } from '$lib/format';
	import { updateQuery } from '$lib/query';
	import { toast } from '$lib/stores/toast.svelte';
	import type { Reseller } from '$lib/types';

	let { data } = $props();
	const r = $derived(data.reseller);
	const base = $derived(`admin/resellers/${r.id}`);

	const themes = [
		{ value: 'theme_d', label: 'Padrão' },
		{ value: 'theme_1', label: 'Tema 1' },
		{ value: 'theme_2', label: 'Tema 2' },
		{ value: 'theme_3', label: 'Tema 3' },
		{ value: 'theme_4', label: 'Tema 4' },
		{ value: 'theme_5', label: 'Tema 5' },
		{ value: 'theme_6', label: 'Tema HTV' },
		{ value: 'theme_7', label: 'Tema XC' },
		{ value: 'theme_8', label: 'Tema P2P' }
	];

	const status = $derived.by(() => {
		if (r.is_blocked) return { tone: 'red' as const, label: 'Bloqueado' };
		const st = expirationState(r.expires_at);
		if (st === 'expired') return { tone: 'red' as const, label: 'Vencido' };
		if (st === 'soon') return { tone: 'yellow' as const, label: 'Vence em breve' };
		return { tone: 'green' as const, label: 'Ativo' };
	});

	async function run(action: () => Promise<unknown>, success: string) {
		try {
			await action();
			toast.success(success);
			await invalidateAll();
			return true;
		} catch (err) {
			toast.error(errorMessage(err));
			return false;
		}
	}

	// ---- edit ------------------------------------------------------------------
	let edit = $state({ name: '', username: '', theme: 'theme_d' });
	let saving = $state(false);
	$effect(() => {
		edit = { name: r.name, username: r.username, theme: r.theme };
	});
	async function saveEdit(e: SubmitEvent) {
		e.preventDefault();
		saving = true;
		await run(() => patch<Reseller>(base, edit), 'Dados atualizados.');
		saving = false;
	}

	// ---- credits ---------------------------------------------------------------
	let creditOpen = $state(false);
	let creditForm = $state({ delta: 1, note: '' });
	let creditLoading = $state(false);
	function openCredits(sign: 1 | -1) {
		creditForm = { delta: sign, note: '' };
		creditOpen = true;
	}
	async function submitCredits(e: SubmitEvent) {
		e.preventDefault();
		creditLoading = true;
		const ok = await run(
			() =>
				post<Reseller>(`${base}/credits`, {
					delta: Number(creditForm.delta),
					note: creditForm.note
				}),
			'Créditos ajustados.'
		);
		creditLoading = false;
		if (ok) creditOpen = false;
	}

	// ---- expiration --------------------------------------------------------------
	let expiration = $state('');
	let expLoading = $state(false);
	$effect(() => {
		expiration = r.expires_at ?? '';
	});
	async function saveExpiration(e: SubmitEvent) {
		e.preventDefault();
		expLoading = true;
		await run(
			() => patch<Reseller>(`${base}/expiration`, { expires_at: expiration || null }),
			'Vencimento atualizado.'
		);
		expLoading = false;
	}
	function addMonths(n: number) {
		const start =
			r.expires_at && expirationState(r.expires_at) !== 'expired'
				? new Date(r.expires_at + 'T00:00:00')
				: new Date();
		start.setMonth(start.getMonth() + n);
		expiration = start.toISOString().slice(0, 10);
	}

	// ---- password ----------------------------------------------------------------
	let pwOpen = $state(false);
	let pw = $state('');
	let pwLoading = $state(false);
	async function submitPassword(e: SubmitEvent) {
		e.preventDefault();
		pwLoading = true;
		const ok = await run(() => post(`${base}/password`, { password: pw }), 'Senha redefinida.');
		pwLoading = false;
		if (ok) {
			pwOpen = false;
			pw = '';
		}
	}

	// ---- block / delete ------------------------------------------------------------
	let blockOpen = $state(false);
	let deleteOpen = $state(false);
	async function toggleBlock() {
		await run(
			() => post<Reseller>(`${base}/block`, { is_blocked: !r.is_blocked }),
			r.is_blocked ? 'Revendedor desbloqueado.' : 'Revendedor bloqueado.'
		);
	}
	async function remove() {
		try {
			await del(base);
			toast.success('Revendedor excluído.');
			await goto('/admin/revendedores');
		} catch (err) {
			toast.error(errorMessage(err));
		}
	}

	const ledgerPages = $derived(Math.max(1, Math.ceil(data.ledger.total / data.ledger.per_page)));
</script>

<PageHeader title={r.name} subtitle="@{r.username} · criado em {formatDate(r.created_at)}">
	{#snippet actions()}
		<Badge tone={status.tone}>{status.label}</Badge>
		<Button variant="secondary" onclick={() => (blockOpen = true)}>
			{r.is_blocked ? 'Desbloquear' : 'Bloquear'}
		</Button>
		<Button variant="danger" onclick={() => (deleteOpen = true)}>Excluir</Button>
	{/snippet}
</PageHeader>

<div class="grid gap-6 lg:grid-cols-3">
	<!-- credits / devices -->
	<section class="card p-5">
		{#if data.creditsEnabled}
			<h2 class="text-sm font-medium text-slate-500">Créditos</h2>
			<p class="mt-2 text-4xl font-semibold tabular-nums">{r.credits}</p>
			<p class="mt-1 text-xs text-slate-500">{r.devices_count} dispositivos cadastrados</p>
			<div class="mt-4 flex gap-2">
				<Button size="sm" onclick={() => openCredits(1)}>+ Adicionar</Button>
				<Button size="sm" variant="secondary" onclick={() => openCredits(-1)}>− Remover</Button>
			</div>
		{:else}
			<h2 class="text-sm font-medium text-slate-500">Dispositivos</h2>
			<p class="mt-2 text-4xl font-semibold tabular-nums">{r.devices_count}</p>
			<p class="mt-1 text-xs text-slate-500">cadastrados por esta revenda</p>
		{/if}
	</section>

	<!-- expiration -->
	<section class="card p-5">
		<h2 class="text-sm font-medium text-slate-500">Vencimento</h2>
		<p class="mt-2 text-2xl font-semibold">
			{r.expires_at ? formatDate(r.expires_at) : 'Sem vencimento'}
		</p>
		<form class="mt-4 space-y-3" onsubmit={saveExpiration}>
			<Input type="date" aria-label="Nova data de vencimento" bind:value={expiration} />
			<div class="flex flex-wrap gap-2">
				<Button size="sm" variant="ghost" onclick={() => addMonths(1)}>+1 mês</Button>
				<Button size="sm" variant="ghost" onclick={() => addMonths(3)}>+3 meses</Button>
				<Button size="sm" variant="ghost" onclick={() => addMonths(12)}>+12 meses</Button>
				<Button size="sm" variant="ghost" onclick={() => (expiration = '')}>Sem vencimento</Button>
			</div>
			<Button size="sm" type="submit" loading={expLoading}>Salvar vencimento</Button>
		</form>
	</section>

	<!-- access -->
	<section class="card p-5">
		<h2 class="text-sm font-medium text-slate-500">Acesso</h2>
		<dl class="mt-2 space-y-1 text-sm">
			<div class="flex justify-between">
				<dt class="text-slate-500">Usuário</dt>
				<dd class="font-medium">{r.username}</dd>
			</div>
			<div class="flex justify-between">
				<dt class="text-slate-500">Tema do app</dt>
				<dd class="font-medium">{themes.find((t) => t.value === r.theme)?.label ?? r.theme}</dd>
			</div>
			<div class="flex justify-between">
				<dt class="text-slate-500">Banners automáticos</dt>
				<dd class="font-medium">{r.auto_ads ? 'Sim' : 'Não'}</dd>
			</div>
		</dl>
		<Button class="mt-4" size="sm" variant="secondary" onclick={() => (pwOpen = true)}
			>Redefinir senha</Button
		>
	</section>
</div>

<div class="mt-6 grid gap-6 lg:grid-cols-3">
	<section class="card p-5">
		<h2 class="font-semibold">Editar dados</h2>
		<form class="mt-4 space-y-4" onsubmit={saveEdit}>
			<Input label="Nome" required bind:value={edit.name} />
			<Input
				label="Usuário"
				required
				minlength={3}
				pattern="[a-zA-Z0-9_.\-]+"
				bind:value={edit.username}
			/>
			<Select label="Tema do app" options={themes} bind:value={edit.theme} />
			<Button type="submit" loading={saving}>Salvar</Button>
		</form>
	</section>

	{#if data.creditsEnabled}
		<section class="card lg:col-span-2">
			<div
				class="flex items-center justify-between border-b border-slate-200 px-5 py-4 dark:border-slate-800"
			>
				<h2 class="font-semibold">Movimentação de créditos</h2>
				<span class="text-xs text-slate-500">{data.ledger.total} registros</span>
			</div>
			<div class="overflow-x-auto">
				<table class="min-w-full divide-y divide-slate-200 dark:divide-slate-800">
					<thead class="bg-slate-50 dark:bg-slate-900/60">
						<tr>
							<th class="table-th">Data</th>
							<th class="table-th text-right">Ajuste</th>
							<th class="table-th text-right">Saldo</th>
							<th class="table-th">Motivo</th>
							<th class="table-th">Por</th>
						</tr>
					</thead>
					<tbody class="divide-y divide-slate-100 dark:divide-slate-800/70">
						{#if data.ledger.items.length === 0}
							<tr
								><td class="table-td py-8 text-center text-slate-500" colspan="5"
									>Nenhuma movimentação.</td
								></tr
							>
						{/if}
						{#each data.ledger.items as entry (entry.id)}
							<tr>
								<td class="table-td whitespace-nowrap">{formatDateTime(entry.created_at)}</td>
								<td
									class="table-td text-right font-medium tabular-nums {entry.delta > 0
										? 'text-emerald-600'
										: 'text-red-600'}"
								>
									{entry.delta > 0 ? '+' : ''}{entry.delta}
								</td>
								<td class="table-td text-right tabular-nums">{entry.balance_after}</td>
								<td class="table-td"
									>{entry.note ?? entry.reason}{entry.ref ? ` (${entry.ref})` : ''}</td
								>
								<td class="table-td"
									>{entry.actor_type}{entry.actor_id ? ` #${entry.actor_id}` : ''}</td
								>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
			{#if ledgerPages > 1}
				<div
					class="flex items-center justify-end gap-2 border-t border-slate-200 px-5 py-3 text-sm dark:border-slate-800"
				>
					<Button
						size="sm"
						variant="ghost"
						disabled={data.ledger.page <= 1}
						onclick={() => updateQuery(page.url, { lpage: data.ledger.page - 1 })}>‹</Button
					>
					<span>Página {data.ledger.page} de {ledgerPages}</span>
					<Button
						size="sm"
						variant="ghost"
						disabled={data.ledger.page >= ledgerPages}
						onclick={() => updateQuery(page.url, { lpage: data.ledger.page + 1 })}>›</Button
					>
				</div>
			{/if}
		</section>
	{/if}
</div>

<Modal bind:open={creditOpen} title="Ajustar créditos" size="sm">
	<form id="credit-form" class="space-y-4" onsubmit={submitCredits}>
		<Input
			label="Quantidade (negativo remove)"
			type="number"
			required
			bind:value={creditForm.delta}
			hint="Saldo atual: {r.credits}"
		/>
		<Input
			label="Motivo"
			required
			minlength={3}
			maxlength={500}
			bind:value={creditForm.note}
			placeholder="Ex.: pagamento do pacote de 10 créditos"
		/>
	</form>
	{#snippet footer()}
		<Button variant="secondary" onclick={() => (creditOpen = false)}>Cancelar</Button>
		<Button type="submit" form="credit-form" loading={creditLoading}>Confirmar</Button>
	{/snippet}
</Modal>

<Modal bind:open={pwOpen} title="Redefinir senha" size="sm">
	<form id="pw-form" class="space-y-4" onsubmit={submitPassword}>
		<Input
			label="Nova senha"
			type="password"
			required
			minlength={6}
			autocomplete="new-password"
			bind:value={pw}
		/>
	</form>
	{#snippet footer()}
		<Button variant="secondary" onclick={() => (pwOpen = false)}>Cancelar</Button>
		<Button type="submit" form="pw-form" loading={pwLoading}>Redefinir</Button>
	{/snippet}
</Modal>

<ConfirmDialog
	bind:open={blockOpen}
	title={r.is_blocked ? 'Desbloquear revendedor' : 'Bloquear revendedor'}
	message={r.is_blocked
		? 'O revendedor voltará a acessar o painel e seus dispositivos voltarão a funcionar.'
		: 'O revendedor perderá o acesso ao painel e todos os seus dispositivos ficarão como vencidos no app.'}
	confirmLabel={r.is_blocked ? 'Desbloquear' : 'Bloquear'}
	danger={!r.is_blocked}
	onconfirm={toggleBlock}
/>

<ConfirmDialog
	bind:open={deleteOpen}
	title="Excluir revendedor"
	message="Esta ação não pode ser desfeita. Os dispositivos ficarão sem revenda e o histórico de pagamentos e créditos será mantido."
	confirmLabel="Excluir"
	danger
	onconfirm={remove}
/>
