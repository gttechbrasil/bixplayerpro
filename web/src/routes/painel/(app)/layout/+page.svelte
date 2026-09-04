<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { errorMessage, put } from '$lib/api';
	import Badge from '$lib/components/Badge.svelte';
	import Button from '$lib/components/Button.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { toast } from '$lib/stores/toast.svelte';

	let { data } = $props();
	let saving = $state<string | null>(null);

	const themes = [
		{
			value: 'default',
			label: 'Padrão',
			description: 'Menu lateral com TV, Filmes e Séries; destaque em carrossel e banners largos.',
			banner: '1920×1080'
		},
		{
			value: 'grid',
			label: 'Grade',
			description: 'Blocos grandes em grade na tela inicial, ideal para controle remoto.',
			banner: '1024×418'
		}
	];

	async function choose(value: string) {
		saving = value;
		try {
			await put('reseller/branding', { theme: value });
			toast.success('Layout atualizado. O app aplica na próxima abertura.');
			await invalidateAll();
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			saving = null;
		}
	}
</script>

<PageHeader title="Layout" subtitle="Escolha a tela inicial do app dos seus clientes" />

<div class="grid gap-6 md:grid-cols-2">
	{#each themes as t (t.value)}
		{@const active = data.user.theme === t.value}
		<div class="card overflow-hidden {active ? 'ring-2 ring-brand-500' : ''}">
			<div class="relative aspect-video bg-slate-900 p-4">
				{#if t.value === 'default'}
					<div class="flex h-full gap-2">
						<div class="w-1/5 space-y-2">
							{#each Array(4) as _, i (i)}<div class="h-3 rounded bg-slate-700"></div>{/each}
						</div>
						<div class="flex-1 space-y-2">
							<div class="h-1/2 rounded bg-brand-700/70"></div>
							<div class="grid grid-cols-4 gap-2">
								{#each Array(4) as _, i (i)}<div class="h-8 rounded bg-slate-700"></div>{/each}
							</div>
						</div>
					</div>
				{:else}
					<div class="grid h-full grid-cols-3 gap-2">
						{#each Array(6) as _, i (i)}
							<div class="rounded {i === 0 ? 'bg-brand-700/70' : 'bg-slate-700'}"></div>
						{/each}
					</div>
				{/if}
				{#if active}
					<div class="absolute top-3 right-3"><Badge tone="green">Ativo</Badge></div>
				{/if}
			</div>
			<div class="space-y-3 p-5">
				<h2 class="font-semibold">{t.label}</h2>
				<p class="text-sm text-slate-500">{t.description}</p>
				<p class="text-xs text-slate-500">Banners recomendados: {t.banner}</p>
				<Button
					variant={active ? 'secondary' : 'primary'}
					disabled={active}
					loading={saving === t.value}
					onclick={() => choose(t.value)}
				>
					{active ? 'Layout atual' : 'Usar este layout'}
				</Button>
			</div>
		</div>
	{/each}
</div>
