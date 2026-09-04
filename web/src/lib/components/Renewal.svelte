<script lang="ts">
	import { onDestroy } from 'svelte';
	import { invalidateAll } from '$app/navigation';
	import { errorMessage, get, post } from '$lib/api';
	import Button from '$lib/components/Button.svelte';
	import { formatDate, formatMoney } from '$lib/format';
	import { toast } from '$lib/stores/toast.svelte';
	import type { Pix, Plans } from '$lib/types';

	let { plans, onpaid }: { plans: Plans; onpaid?: (pix: Pix) => void } = $props();

	let months = $state(1);
	let packageId = $state<number | null>(null);
	let generating = $state(false);
	let pix = $state<Pix | null>(null);
	let copied = $state(false);
	let timer: ReturnType<typeof setInterval> | undefined;

	const monthly = $derived(Number(plans.monthly_price));
	const total = $derived.by(() => {
		if (packageId !== null) {
			const pkg = plans.packages.find((p) => p.id === packageId);
			return pkg ? Number(pkg.price) : 0;
		}
		return monthly * months;
	});
	const totalMonths = $derived(
		packageId !== null ? (plans.packages.find((p) => p.id === packageId)?.months ?? 0) : months
	);

	function stopPolling() {
		if (timer) clearInterval(timer);
		timer = undefined;
	}
	onDestroy(stopPolling);

	async function poll() {
		if (!pix || pix.status !== 'pending') return stopPolling();
		try {
			const fresh = await get<Pix>(`reseller/billing/pix/${pix.payment_id}`);
			pix = fresh;
			if (fresh.status === 'approved') {
				stopPolling();
				toast.success('Pagamento aprovado! Revenda renovada.');
				await invalidateAll();
				onpaid?.(fresh);
			} else if (fresh.status !== 'pending') {
				stopPolling();
			}
		} catch {
			/* keep polling; transient errors are expected */
		}
	}

	async function generate() {
		generating = true;
		try {
			pix = await post<Pix>(
				'reseller/billing/pix',
				packageId !== null ? { package_id: packageId } : { months }
			);
			stopPolling();
			timer = setInterval(poll, 4000);
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			generating = false;
		}
	}

	async function copy() {
		if (!pix?.qr_code) return;
		try {
			await navigator.clipboard.writeText(pix.qr_code);
			copied = true;
			toast.success('Código Pix copiado!');
			setTimeout(() => (copied = false), 2500);
		} catch {
			toast.warning('Não foi possível copiar. Selecione o código manualmente.');
		}
	}

	function reset() {
		stopPolling();
		pix = null;
	}
</script>

{#if !plans.can_renew}
	<p
		class="rounded-lg bg-amber-50 p-4 text-sm text-amber-800 dark:bg-amber-950 dark:text-amber-200"
	>
		Sua revenda não possui vencimento definido e não precisa de renovação. Fale com o administrador.
	</p>
{:else if pix && pix.status === 'approved'}
	<div class="space-y-3 text-center">
		<div
			class="mx-auto grid h-14 w-14 place-items-center rounded-full bg-emerald-100 text-2xl text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300"
		>
			✓
		</div>
		<h3 class="text-lg font-semibold">Pagamento aprovado!</h3>
		<p class="text-sm text-slate-600 dark:text-slate-300">
			Novo vencimento: <strong>{formatDate(pix.new_expires_at)}</strong>
		</p>
	</div>
{:else if pix}
	<div class="space-y-4">
		<div class="flex items-center justify-between text-sm">
			<span>
				{pix.months} mês(es) · <strong>{formatMoney(pix.amount)}</strong>
			</span>
			{#if pix.status === 'pending'}
				<span class="inline-flex items-center gap-2 text-amber-700 dark:text-amber-300">
					<span class="h-2 w-2 animate-pulse rounded-full bg-amber-500"></span>
					Aguardando pagamento…
				</span>
			{:else}
				<span class="text-red-600"
					>Cobrança {pix.status === 'expired' ? 'expirada' : 'cancelada'}</span
				>
			{/if}
		</div>
		{#if pix.qr_base64}
			<img
				class="mx-auto h-56 w-56 rounded-lg bg-white p-2"
				src="data:image/png;base64,{pix.qr_base64}"
				alt="QR Code Pix"
			/>
		{/if}
		<div>
			<label class="label" for="pix-code">Pix copia e cola</label>
			<textarea
				id="pix-code"
				class="input h-24 font-mono text-xs"
				readonly
				onclick={(e) => e.currentTarget.select()}>{pix.qr_code}</textarea
			>
		</div>
		<div class="flex flex-wrap gap-2">
			<Button onclick={copy}>{copied ? 'Copiado!' : 'Copiar código'}</Button>
			<Button variant="secondary" onclick={reset}>Gerar outro</Button>
		</div>
		<p class="text-xs text-slate-500">
			Vencimento após o pagamento: {formatDate(pix.projected_expires_at)}. O QR vale até
			{pix.expires_at
				? new Date(pix.expires_at).toLocaleTimeString('pt-BR', {
						hour: '2-digit',
						minute: '2-digit'
					})
				: '—'}. Fechar esta janela não cancela a cobrança: a confirmação chega automaticamente.
		</p>
	</div>
{:else}
	<div class="space-y-4">
		<p class="text-sm text-slate-600 dark:text-slate-300">
			Vencimento atual: <strong>{formatDate(plans.expires_at)}</strong> · Valor mensal
			<strong>{formatMoney(plans.monthly_price)}</strong>
		</p>
		{#if plans.packages.length > 0}
			<fieldset class="space-y-2">
				<legend class="label">Pacotes promocionais</legend>
				{#each plans.packages as pkg (pkg.id)}
					<label
						class="flex cursor-pointer items-center justify-between rounded-lg border px-3 py-2 text-sm {packageId ===
						pkg.id
							? 'border-brand-500 bg-brand-50 dark:bg-brand-900/30'
							: 'border-slate-200 dark:border-slate-700'}"
					>
						<span class="flex items-center gap-2">
							<input type="radio" name="billing_package" value={pkg.id} bind:group={packageId} />
							{pkg.months} meses
						</span>
						<strong>{formatMoney(pkg.price)}</strong>
					</label>
				{/each}
				<label
					class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm {packageId ===
					null
						? 'border-brand-500 bg-brand-50 dark:bg-brand-900/30'
						: 'border-slate-200 dark:border-slate-700'}"
				>
					<input type="radio" name="billing_package" value={null} bind:group={packageId} />
					Avulso (por mês)
				</label>
			</fieldset>
		{/if}
		{#if packageId === null}
			<div>
				<label class="label" for="months">Quantidade de meses (1 a {plans.max_months})</label>
				<input
					id="months"
					type="number"
					class="input"
					min={1}
					max={plans.max_months}
					bind:value={months}
				/>
			</div>
		{/if}
		<div
			class="flex items-center justify-between rounded-lg bg-slate-100 px-4 py-3 dark:bg-slate-800"
		>
			<span class="text-sm">Total ({totalMonths} mês(es))</span>
			<span class="text-xl font-semibold">{formatMoney(total)}</span>
		</div>
		<Button class="w-full" loading={generating} onclick={generate} disabled={total <= 0}
			>Gerar Pix</Button
		>
	</div>
{/if}
