<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { del, errorMessage, get, patch, post, put } from '$lib/api';
	import Badge from '$lib/components/Badge.svelte';
	import Button from '$lib/components/Button.svelte';
	import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
	import Input from '$lib/components/Input.svelte';
	import Modal from '$lib/components/Modal.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { toast } from '$lib/stores/toast.svelte';
	import type { Banner } from '$lib/types';

	interface StockBanner {
		id: string;
		label: string;
		url: string;
	}

	let { data } = $props();

	let newOpen = $state(false);
	// Ready-made banners (F2-012): the platform's promo art, added with one click. The list comes
	// from the API so a file swap on the server updates every panel.
	let stock = $state<StockBanner[]>([]);
	let adding = $state<string | null>(null);
	$effect(() => {
		get<StockBanner[]>('reseller/branding/banners/stock')
			.then((list) => (stock = list))
			.catch(() => (stock = []));
	});

	async function addStock(b: StockBanner) {
		adding = b.id;
		try {
			await post<Banner>('reseller/branding/banners', { title: b.label, url: b.url });
			toast.success('Banner adicionado.');
			await invalidateAll();
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			adding = null;
		}
	}
	let creating = $state(false);
	let form = $state({ title: '', url: '' });
	let deleteTarget = $state<Banner | null>(null);
	let deleteOpen = $state(false);
	// svelte-ignore state_referenced_locally
	let autoAds = $state(data.user.auto_ads);

	async function run(action: () => Promise<unknown>, success: string) {
		try {
			await action();
			if (success) toast.success(success);
			await invalidateAll();
		} catch (err) {
			toast.error(errorMessage(err));
			throw err;
		}
	}

	async function create(e: SubmitEvent) {
		e.preventDefault();
		creating = true;
		try {
			await post<Banner>('reseller/branding/banners', {
				title: form.title.trim(),
				url: form.url.trim()
			});
			toast.success('Banner criado.');
			newOpen = false;
			form = { title: '', url: '' };
			await invalidateAll();
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			creating = false;
		}
	}

	function toggleActive(b: Banner) {
		return run(
			() => patch(`reseller/branding/banners/${b.id}`, { is_active: !b.is_active }),
			b.is_active ? 'Banner desativado.' : 'Banner ativado.'
		).catch(() => {});
	}

	async function toggleAutoAds() {
		const next = !autoAds;
		autoAds = next;
		await run(
			() => put('reseller/branding', { auto_ads: next }),
			next ? 'Banners automáticos ativados.' : 'Banners automáticos desativados.'
		).catch(() => (autoAds = !next));
	}

	function askDelete(b: Banner) {
		deleteTarget = b;
		deleteOpen = true;
	}
</script>

<PageHeader title="Banners" subtitle="Até 10 banners exibidos no app ({data.banners.length}/10)">
	{#snippet actions()}
		<Button onclick={() => (newOpen = true)} disabled={data.banners.length >= 10}
			>+ Novo banner</Button
		>
	{/snippet}
</PageHeader>

<label class="card mb-6 flex cursor-pointer items-center justify-between p-4">
	<span>
		<span class="block font-medium">Banners automáticos</span>
		<span class="block text-sm text-slate-500"
			>O app usa as capas dos conteúdos como banner, além dos cadastrados aqui.</span
		>
	</span>
	<input type="checkbox" class="h-5 w-5 rounded" checked={autoAds} onchange={toggleAutoAds} />
</label>

{#if stock.length > 0}
	<section class="mb-6">
		<h2 class="mb-1 font-semibold">Banners prontos</h2>
		<p class="mb-3 text-sm text-slate-500">
			Arte pronta da plataforma: clique em Adicionar e o banner entra na sua lista, já ativo.
		</p>
		<div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
			{#each stock as b (b.id)}
				{@const added = data.banners.some((x) => x.url === b.url)}
				<div class="card min-w-0 overflow-hidden">
					<img
						src={b.url}
						alt={b.label}
						width="1920"
						height="1080"
						loading="lazy"
						class="aspect-video w-full bg-slate-900 object-cover"
					/>
					<div class="flex items-center justify-between gap-3 px-4 py-3 text-sm">
						<span class="font-medium">{b.label}</span>
						{#if added}
							<Badge tone="green">Adicionado</Badge>
						{:else}
							<Button
								size="sm"
								loading={adding === b.id}
								disabled={adding !== null || data.banners.length >= 10}
								onclick={() => addStock(b)}>Adicionar</Button
							>
						{/if}
					</div>
				</div>
			{/each}
		</div>
	</section>
{/if}

<div class="card overflow-hidden">
	<!-- The table keeps its columns on a phone; the wrapper scrolls instead of clipping (M5-026). -->
	<div class="overflow-x-auto">
		<table class="min-w-full divide-y divide-slate-200 dark:divide-slate-800">
			<thead class="bg-slate-50 dark:bg-slate-900/60">
				<tr>
					<th class="table-th">Pré-visualização</th>
					<th class="table-th">Título</th>
					<th class="table-th">URL</th>
					<th class="table-th">Status</th>
					<th class="table-th text-right">Ações</th>
				</tr>
			</thead>
			<tbody class="divide-y divide-slate-100 dark:divide-slate-800/70">
				{#if data.banners.length === 0}
					<tr
						><td class="table-td py-10 text-center text-slate-500" colspan="5"
							>Nenhum banner cadastrado.</td
						></tr
					>
				{/if}
				{#each data.banners as b (b.id)}
					<tr>
						<td class="table-td">
							<img
								src={b.url}
								alt={b.title}
								class="h-12 w-24 rounded object-cover"
								loading="lazy"
							/>
						</td>
						<td class="table-td font-medium">{b.title}</td>
						<td class="table-td max-w-xs truncate text-xs text-slate-500" title={b.url}>{b.url}</td>
						<td class="table-td">
							{#if b.is_active}<Badge tone="green">Ativo</Badge>{:else}<Badge tone="gray"
									>Inativo</Badge
								>{/if}
						</td>
						<td class="table-td text-right whitespace-nowrap">
							<button
								type="button"
								class="text-sm font-medium text-brand-700 hover:underline dark:text-brand-300"
								onclick={() => toggleActive(b)}
							>
								{b.is_active ? 'Desativar' : 'Ativar'}
							</button>
							<button
								type="button"
								class="ml-3 text-sm font-medium text-red-600 hover:underline"
								onclick={() => askDelete(b)}>Excluir</button
							>
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
</div>

<Modal bind:open={newOpen} title="Novo banner" size="sm">
	<form id="new-banner" class="space-y-4" onsubmit={create}>
		<Input
			label="Título"
			placeholder="Título do Banner"
			required
			maxlength={120}
			bind:value={form.title}
		/>
		<Input
			label="URL da imagem"
			type="url"
			placeholder="https://exemplo.com/banner.jpg"
			required
			bind:value={form.url}
		/>
	</form>
	{#snippet footer()}
		<Button variant="secondary" onclick={() => (newOpen = false)}>Cancelar</Button>
		<Button type="submit" form="new-banner" loading={creating}>Enviar</Button>
	{/snippet}
</Modal>

<ConfirmDialog
	bind:open={deleteOpen}
	title="Excluir banner"
	message="Excluir o banner “{deleteTarget?.title}”?"
	confirmLabel="Excluir"
	danger
	onconfirm={() =>
		run(() => del(`reseller/branding/banners/${deleteTarget?.id}`), 'Banner excluído.')}
/>
