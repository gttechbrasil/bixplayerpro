<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { errorMessage, put } from '$lib/api';
	import Button from '$lib/components/Button.svelte';
	import Input from '$lib/components/Input.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { formatMoney } from '$lib/format';
	import { toast } from '$lib/stores/toast.svelte';
	import type { Settings } from '$lib/types';

	let { data } = $props();

	let form = $state({
		platform_name: '',
		monthly_price: '',
		min_app_version: '',
		apk_url: '',
		packages: [] as { months: number; price: string }[]
	});
	let saving = $state(false);

	$effect(() => {
		const s = data.settings;
		form = {
			platform_name: s.platform_name,
			monthly_price: s.monthly_price,
			min_app_version: s.min_app_version,
			apk_url: s.apk_url,
			packages: s.packages.map((p) => ({ ...p }))
		};
	});

	function addPackage() {
		form.packages.push({ months: 3, price: '' });
	}
	function removePackage(i: number) {
		form.packages.splice(i, 1);
	}

	async function save(e: SubmitEvent) {
		e.preventDefault();
		saving = true;
		try {
			await put<Settings>('admin/settings', {
				platform_name: form.platform_name.trim(),
				monthly_price: String(form.monthly_price).replace(',', '.'),
				min_app_version: form.min_app_version.trim(),
				apk_url: form.apk_url.trim(),
				packages: form.packages.map((p) => ({
					months: Number(p.months),
					price: String(p.price).replace(',', '.')
				}))
			});
			toast.success('Configurações salvas.');
			await invalidateAll();
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			saving = false;
		}
	}
</script>

<PageHeader title="Configurações" subtitle="Parâmetros globais da plataforma" />

<form class="grid gap-6 lg:grid-cols-2" onsubmit={save}>
	<section class="card space-y-4 p-5">
		<h2 class="font-semibold">Plataforma</h2>
		<Input label="Nome da plataforma" required maxlength={80} bind:value={form.platform_name} />
		<Input
			label="Versão mínima do app"
			required
			pattern="\d+(\.\d+){'{0,3}'}"
			hint="Ex.: 1.0.0 — versões anteriores verão o aviso de atualização."
			bind:value={form.min_app_version}
		/>
		<Input
			label="Link de download do APK"
			type="url"
			placeholder="https://…/app.apk"
			bind:value={form.apk_url}
		/>
	</section>

	<section class="card space-y-4 p-5">
		<h2 class="font-semibold">Renovação da revenda</h2>
		<Input
			label="Preço mensal (R$)"
			type="number"
			step="0.01"
			min="0.01"
			required
			hint="Valor cobrado por mês na renovação avulsa via Pix. Atual: {formatMoney(
				data.settings.monthly_price
			)}"
			bind:value={form.monthly_price}
		/>

		<div>
			<div class="mb-2 flex items-center justify-between">
				<span class="label mb-0">Pacotes promocionais</span>
				<Button size="sm" variant="secondary" onclick={addPackage}>+ Pacote</Button>
			</div>
			{#if form.packages.length === 0}
				<p class="text-sm text-slate-500">Nenhum pacote. A revenda só verá a opção avulsa.</p>
			{/if}
			<div class="space-y-2">
				{#each form.packages as pkg, i (i)}
					<div class="flex items-end gap-2">
						<Input
							label="Meses"
							type="number"
							min={1}
							max={60}
							required
							class="w-28"
							bind:value={pkg.months}
						/>
						<Input
							label="Preço total (R$)"
							type="number"
							step="0.01"
							min="0.01"
							required
							class="flex-1"
							bind:value={pkg.price}
						/>
						<Button
							variant="ghost"
							size="sm"
							class="mb-0.5"
							aria-label="Remover pacote"
							onclick={() => removePackage(i)}>✕</Button
						>
					</div>
				{/each}
			</div>
		</div>
	</section>

	<div class="lg:col-span-2">
		<Button type="submit" loading={saving}>Salvar configurações</Button>
	</div>
</form>
