<script lang="ts">
	import PageHeader from '$lib/components/PageHeader.svelte';
	import Stat from '$lib/components/Stat.svelte';
	import { formatMoney } from '$lib/format';

	let { data } = $props();
	const d = $derived(data.dashboard);
</script>

<PageHeader title="Dashboard" subtitle="Visão geral da plataforma" />

<section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
	<Stat
		label="Revendedores"
		value={d.resellers_total}
		hint="{d.resellers_active} ativos · {d.resellers_blocked} bloqueados · {d.resellers_expired} vencidos"
	/>
	<Stat
		label="Dispositivos ativos"
		value={d.devices_active}
		hint="{d.devices_registered} cadastrados de {d.devices_total} conhecidos"
	/>
	<Stat
		label="Online nas últimas 24h"
		value={d.devices_seen_24h}
		hint="dispositivos que consultaram a API"
	/>
	<Stat
		label="Recebido no mês"
		value={formatMoney(d.payments_month_amount)}
		hint="{d.payments_month_count} pagamentos aprovados · {d.payments_pending} pendentes"
	/>
</section>

<section class="mt-8 grid gap-4 md:grid-cols-3">
	<a href="/admin/revendedores" class="card p-5 transition hover:border-brand-400">
		<h2 class="font-semibold">Revendedores</h2>
		<p class="mt-1 text-sm text-slate-500">Cadastrar, editar, ajustar créditos e vencimentos.</p>
	</a>
	<a href="/admin/pagamentos" class="card p-5 transition hover:border-brand-400">
		<h2 class="font-semibold">Pagamentos</h2>
		<p class="mt-1 text-sm text-slate-500">Histórico de renovações via Pix.</p>
	</a>
	<a href="/admin/configuracoes" class="card p-5 transition hover:border-brand-400">
		<h2 class="font-semibold">Configurações</h2>
		<p class="mt-1 text-sm text-slate-500">Preço mensal, pacotes e versão mínima do app.</p>
	</a>
</section>
