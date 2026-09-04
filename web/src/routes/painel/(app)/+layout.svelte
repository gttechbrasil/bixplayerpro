<script lang="ts">
	import { goto, invalidateAll } from '$app/navigation';
	import { get, post } from '$lib/api';
	import ExpirationCard from '$lib/components/ExpirationCard.svelte';
	import Modal from '$lib/components/Modal.svelte';
	import Renewal from '$lib/components/Renewal.svelte';
	import Sidebar, { type NavGroup } from '$lib/components/Sidebar.svelte';
	import ThemeToggle from '$lib/components/ThemeToggle.svelte';
	import { toast } from '$lib/stores/toast.svelte';
	import type { Plans } from '$lib/types';

	let { data, children } = $props();

	const groups: NavGroup[] = [
		{
			title: 'Recursos',
			items: [
				{ href: '/painel/dispositivos', label: 'Dispositivos' },
				{ href: '/painel/dns', label: 'Migrador de DNS' }
			]
		},
		{
			title: 'Personalização',
			items: [
				{ href: '/painel/logomarca', label: 'Logomarca' },
				{ href: '/painel/background', label: 'Background' },
				{ href: '/painel/layout', label: 'Layout' },
				{ href: '/painel/banners', label: 'Banners' },
				{ href: '/painel/qrcode', label: 'QR Code' }
			]
		},
		{ title: 'Conta', items: [{ href: '/painel/perfil', label: 'Perfil' }] }
	];

	let renewOpen = $state(false);
	let plans = $state<Plans | null>(null);

	async function openRenewal() {
		try {
			plans = await get<Plans>('reseller/billing/plans');
			renewOpen = true;
		} catch {
			toast.error('Não foi possível carregar os planos.');
		}
	}

	async function logout() {
		try {
			await post('auth/logout');
		} catch {
			/* ignore */
		}
		toast.info('Sessão encerrada.');
		await goto('/painel/login');
	}
</script>

<svelte:head><title>{data.platform.name} · Revenda</title></svelte:head>

<div class="flex h-screen overflow-hidden">
	<Sidebar
		platformName={data.platform.name}
		{groups}
		footer="© {new Date().getFullYear()} {data.platform.name}"
	>
		{#snippet top()}
			<ExpirationCard expiresAt={data.user.expires_at} onclick={openRenewal} />
		{/snippet}
	</Sidebar>
	<div class="flex min-w-0 flex-1 flex-col">
		<header
			class="flex h-16 shrink-0 items-center justify-end gap-4 border-b border-slate-200 bg-white px-6 dark:border-slate-800 dark:bg-slate-900"
		>
			{#if data.platform.credits_enabled}
				<span
					class="rounded-full bg-brand-50 px-3 py-1 text-sm font-medium text-brand-700 dark:bg-brand-900/40 dark:text-brand-200"
				>
					Créditos: {data.user.credits}
				</span>
			{/if}
			<span class="text-sm text-slate-500 dark:text-slate-400">
				<strong class="text-slate-800 dark:text-slate-100">{data.user.name}</strong>
			</span>
			<ThemeToggle />
			<button
				type="button"
				class="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
				onclick={logout}>Sair</button
			>
		</header>
		<main class="flex-1 overflow-y-auto p-6">
			<div class="mx-auto max-w-7xl">{@render children()}</div>
		</main>
	</div>
</div>

<Modal bind:open={renewOpen} title="Renovar revenda">
	{#if plans}
		<Renewal {plans} onpaid={() => invalidateAll()} />
	{/if}
</Modal>
