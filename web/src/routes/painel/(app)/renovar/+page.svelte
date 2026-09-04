<script lang="ts">
	import { goto } from '$app/navigation';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import Renewal from '$lib/components/Renewal.svelte';

	let { data } = $props();
</script>

<PageHeader title="Renovar revenda" subtitle="Pagamento por Pix com confirmação automática" />

{#if data.user.is_expired}
	<div
		class="mb-6 rounded-lg border border-red-300 bg-red-50 p-4 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200"
		role="alert"
	>
		<strong>Sua revenda venceu.</strong>
		{#if data.expiredNotice}
			O acesso às demais telas fica bloqueado até a renovação.
		{/if}
		Os dispositivos dos seus clientes aparecem como vencidos no app até o pagamento ser confirmado.
	</div>
{/if}

<div class="card max-w-xl p-6">
	<Renewal
		plans={data.plans}
		onpaid={() => goto('/painel/dispositivos', { invalidateAll: true })}
	/>
</div>
