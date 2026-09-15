<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { errorMessage, put } from '$lib/api';
	import Badge from '$lib/components/Badge.svelte';
	import Button from '$lib/components/Button.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { toast } from '$lib/stores/toast.svelte';

	let { data } = $props();
	let saving = $state<string | null>(null);

	// Five layouts since F2-002. `value` is what the app receives in `theme`.
	const themes = [
		{
			value: 'default',
			label: 'Padrão',
			description: 'Cartões de menu sobre o seu fundo, com "continuar assistindo" e banners.',
			banner: '1920×1080'
		},
		{
			value: 'grid',
			label: 'Grade',
			description: 'Blocos grandes em grade, os maiores alvos para o controle remoto.',
			banner: '1024×418'
		},
		{
			value: 'cinema',
			label: 'Cinema',
			description:
				'Capa do filme mais recente ocupando a tela, menu em botões e fileira de lançamentos.',
			banner: '1920×1080'
		},
		{
			value: 'rail',
			label: 'Menu lateral',
			description:
				'Menu vertical à esquerda e, à direita, destaque com as capas de filmes e séries.',
			banner: '1920×1080'
		},
		{
			value: 'mosaic',
			label: 'Mosaico',
			description: 'Um painel grande de TV ao vivo e blocos de Filmes e Séries com capas reais.',
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

<div class="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
	{#each themes as t (t.value)}
		{@const active = data.user.theme === t.value}
		<div class="card overflow-hidden {active ? 'ring-2 ring-brand-500' : ''}">
			<div class="relative aspect-video bg-slate-900 p-3">
				<!-- Miniatures: the same shapes the app draws, so the reseller picks by eye. -->
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
				{:else if t.value === 'grid'}
					<div class="grid h-full grid-cols-3 gap-2">
						{#each Array(6) as _, i (i)}
							<div class="rounded {i === 0 ? 'bg-brand-700/70' : 'bg-slate-700'}"></div>
						{/each}
					</div>
				{:else if t.value === 'cinema'}
					<div
						class="flex h-full flex-col justify-end rounded bg-gradient-to-t from-slate-950 via-slate-800 to-slate-600 p-2"
					>
						<div class="mb-1 h-3 w-2/5 rounded bg-white/80"></div>
						<div class="mb-2 h-2 w-1/4 rounded bg-white/40"></div>
						<div class="flex gap-1.5">
							{#each Array(5) as _, i (i)}
								<div class="h-4 flex-1 rounded {i === 0 ? 'bg-brand-500' : 'bg-slate-700'}"></div>
							{/each}
						</div>
						<div class="mt-2 flex gap-1.5">
							{#each Array(6) as _, i (i)}<div
									class="h-6 flex-1 rounded bg-slate-700/80"
								></div>{/each}
						</div>
					</div>
				{:else if t.value === 'rail'}
					<div class="flex h-full gap-2">
						<div class="w-1/3 space-y-1.5 rounded bg-slate-950 p-1.5">
							{#each Array(5) as _, i (i)}
								<div class="h-3 rounded {i === 0 ? 'bg-brand-500' : 'bg-slate-700'}"></div>
							{/each}
						</div>
						<div class="flex flex-1 flex-col justify-end gap-1.5">
							<div class="h-3 w-1/2 rounded bg-white/70"></div>
							<div class="flex gap-1.5">
								{#each Array(4) as _, i (i)}<div
										class="h-8 flex-1 rounded bg-slate-700"
									></div>{/each}
							</div>
						</div>
					</div>
				{:else}
					<div class="flex h-full flex-col gap-2">
						<div class="flex flex-1 gap-2">
							<div class="flex-[1.4] rounded bg-brand-700/70"></div>
							<div class="flex flex-1 flex-col gap-2">
								<div class="flex-1 rounded bg-slate-700"></div>
								<div class="flex-1 rounded bg-slate-700"></div>
							</div>
						</div>
						<div class="flex gap-1.5">
							{#each Array(4) as _, i (i)}<div
									class="h-3 flex-1 rounded bg-slate-700/80"
								></div>{/each}
						</div>
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

<p class="mt-6 text-sm text-slate-500">
	O cliente pode escolher outro layout no próprio aparelho em <strong
		>Configurações → Layout da tela inicial</strong
	>; essa escolha vale só para aquele aparelho e tem prioridade sobre a definida aqui.
</p>
