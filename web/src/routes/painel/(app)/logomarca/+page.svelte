<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { errorMessage, put } from '$lib/api';
	import Button from '$lib/components/Button.svelte';
	import ImageField from '$lib/components/ImageField.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { toast } from '$lib/stores/toast.svelte';

	let { data } = $props();
	let restoring = $state(false);

	// Back to the platform's logo (F2-013): the app already falls back to it when logo_url is
	// empty, so "default" is simply clearing the field.
	async function useDefault() {
		restoring = true;
		try {
			await put('reseller/branding', { logo_url: null });
			toast.success('Logomarca padrão restaurada. O app aplica na próxima abertura.');
			await invalidateAll();
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			restoring = false;
		}
	}
</script>

<PageHeader title="Logomarca" subtitle="Exibida na tela inicial do app" />

<section class="card mb-6 flex flex-col gap-4 p-5 sm:flex-row sm:items-center">
	<div class="flex h-20 w-44 shrink-0 items-center justify-center rounded-lg bg-slate-950 p-3">
		<img src="/app-logo.png" alt="Logomarca padrão" class="max-h-full max-w-full object-contain" />
	</div>
	<div class="min-w-0 flex-1">
		<h2 class="font-semibold">Logomarca padrão</h2>
		<p class="text-sm text-slate-500">
			{#if data.user.logo_url}
				Você está usando uma logomarca própria. Volte para a padrão a qualquer momento.
			{:else}
				Em uso. Envie a sua logo abaixo quando quiser trocar.
			{/if}
		</p>
	</div>
	<Button variant="secondary" loading={restoring} disabled={!data.user.logo_url} onclick={useDefault}>
		Usar logomarca padrão
	</Button>
</section>

<h2 class="mb-3 font-semibold">Sua própria logomarca</h2>
{#key data.user.logo_url}
	<ImageField kind="logo" label="Logomarca" current={data.user.logo_url} />
{/key}
