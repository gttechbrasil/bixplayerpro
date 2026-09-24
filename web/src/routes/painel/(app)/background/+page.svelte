<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { errorMessage, get, put } from '$lib/api';
	import ImageField from '$lib/components/ImageField.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { toast } from '$lib/stores/toast.svelte';

	interface StockBackground {
		id: string;
		label: string;
		url: string;
	}

	let { data } = $props();
	let stock = $state<StockBackground[]>([]);
	let applying = $state<string | null>(null);

	// Ready-made backgrounds (F2-008): a reseller who has no art of their own still gets an app
	// that does not look unfinished. The list comes from the platform, so swapping the files
	// updates every panel at once.
	$effect(() => {
		get<StockBackground[]>('reseller/branding/backgrounds')
			.then((list) => (stock = list))
			.catch(() => (stock = []));
	});

	async function apply(bg: StockBackground) {
		applying = bg.id;
		try {
			await put('reseller/branding', { bg_url: bg.url });
			toast.success('Background atualizado. O app aplica na próxima abertura.');
			await invalidateAll();
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			applying = null;
		}
	}
</script>

<PageHeader title="Background" subtitle="Imagem de fundo do app (recomendado 1920×1080)" />

{#if stock.length > 0}
	<section class="mb-6">
		<h2 class="mb-1 font-semibold">Fundos prontos</h2>
		<p class="mb-3 text-sm text-slate-500">
			Escolha um destes e o app dos seus clientes já fica com cara de pronto. Você pode trocar por
			uma imagem sua a qualquer momento, logo abaixo.
		</p>
		<div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
			{#each stock as bg (bg.id)}
				{@const active = data.user.bg_url === bg.url}
				<button
					type="button"
					class="card min-w-0 overflow-hidden text-left transition disabled:opacity-100 {active
						? 'ring-2 ring-brand-500'
						: 'hover:brightness-110'}"
					disabled={applying !== null}
					onclick={() => apply(bg)}
				>
					<img
						src={bg.url}
						alt="Fundo {bg.label}"
						width="1920"
						height="1080"
						loading="lazy"
						class="aspect-video w-full bg-slate-900 object-cover"
					/>
					<span class="flex items-center justify-between px-4 py-3 text-sm">
						<span class="font-medium">{bg.label}</span>
						<span class="text-xs text-slate-500">
							{#if active}Em uso{:else if applying === bg.id}Aplicando…{:else}Usar{/if}
						</span>
					</span>
				</button>
			{/each}
		</div>
	</section>
{/if}

<h2 class="mb-3 font-semibold">Sua própria imagem</h2>
{#key data.user.bg_url}
	<ImageField kind="bg" label="Background" current={data.user.bg_url} />
{/key}
