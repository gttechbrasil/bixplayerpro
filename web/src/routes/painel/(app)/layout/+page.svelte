<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { errorMessage, put } from '$lib/api';
	import Badge from '$lib/components/Badge.svelte';
	import Button from '$lib/components/Button.svelte';
	import Modal from '$lib/components/Modal.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { toast } from '$lib/stores/toast.svelte';
	import cinemaShot from '$lib/assets/layouts/cinema.jpg';
	import defaultShot from '$lib/assets/layouts/default.jpg';
	import gridShot from '$lib/assets/layouts/grid.jpg';
	import mosaicShot from '$lib/assets/layouts/mosaic.jpg';
	import railShot from '$lib/assets/layouts/rail.jpg';

	let { data } = $props();
	let saving = $state<string | null>(null);
	let preview = $state<(typeof themes)[number] | null>(null);
	let previewOpen = $state(false);

	// Five layouts since F2-002; the shots are captured from the app itself (F2-007), so what the
	// reseller sees here is what lands on the customer's TV.
	const themes = [
		{
			value: 'rail',
			label: 'Menu lateral',
			description:
				'Menu vertical à esquerda e, à direita, destaque com as capas de filmes e séries.',
			banner: '1920×1080',
			shot: railShot
		},
		{
			value: 'cinema',
			label: 'Cinema',
			description:
				'Capa do filme mais recente ocupando a tela, menu em botões e fileira de lançamentos.',
			banner: '1920×1080',
			shot: cinemaShot
		},
		{
			value: 'mosaic',
			label: 'Mosaico',
			description: 'Um painel grande de TV ao vivo e blocos de Filmes e Séries com capas reais.',
			banner: '1024×418',
			shot: mosaicShot
		},
		{
			value: 'default',
			label: 'Padrão',
			description: 'Cartões de menu sobre o seu fundo, com "continuar assistindo" e banners.',
			banner: '1920×1080',
			shot: defaultShot
		},
		{
			value: 'grid',
			label: 'Grade',
			description: 'Blocos grandes em grade, os maiores alvos para o controle remoto.',
			banner: '1024×418',
			shot: gridShot
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
			<button
				type="button"
				class="relative block w-full cursor-zoom-in"
				onclick={() => {
					preview = t;
					previewOpen = true;
				}}
				aria-label="Ampliar a prévia do layout {t.label}"
			>
				<img
					src={t.shot}
					alt="Tela inicial do app no layout {t.label}"
					width="960"
					height="540"
					loading="lazy"
					class="aspect-video w-full bg-slate-900 object-cover"
				/>
				{#if active}
					<span class="absolute top-3 right-3"><Badge tone="green">Ativo</Badge></span>
				{/if}
				<span
					class="absolute right-3 bottom-3 rounded-md bg-slate-950/70 px-2 py-1 text-xs text-white"
					>Ampliar</span
				>
			</button>
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
	As prévias são capturas do próprio app. O cliente também pode trocar o layout no aparelho dele em
	<strong>Configurações → Layout da tela inicial</strong>; essa escolha vale só para aquele aparelho
	e tem prioridade sobre a definida aqui.
</p>

{#if preview}
	<Modal bind:open={previewOpen} title="Layout {preview.label}" size="lg">
		<img
			src={preview.shot}
			alt="Tela inicial do app no layout {preview.label}"
			class="w-full rounded-lg bg-slate-900"
		/>
		<p class="mt-3 text-sm text-slate-500">{preview.description}</p>
	</Modal>
{/if}
