<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { del, errorMessage, patch, post, put } from '$lib/api';
	import Badge from '$lib/components/Badge.svelte';
	import Button from '$lib/components/Button.svelte';
	import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
	import Input from '$lib/components/Input.svelte';
	import Modal from '$lib/components/Modal.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { toast } from '$lib/stores/toast.svelte';
	import type { Banner } from '$lib/types';

	let { data } = $props();

	let newOpen = $state(false);
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

<div class="card overflow-hidden">
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
						<img src={b.url} alt={b.title} class="h-12 w-24 rounded object-cover" loading="lazy" />
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
							class="text-sm font-medium text-brand-600 hover:underline dark:text-brand-300"
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
