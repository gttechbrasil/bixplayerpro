<script lang="ts">
	import { goto, invalidateAll } from '$app/navigation';
	import { get, post } from '$lib/api';
	import ExpirationCard from '$lib/components/ExpirationCard.svelte';
	import Modal from '$lib/components/Modal.svelte';
	import Renewal from '$lib/components/Renewal.svelte';
	import Sidebar, { type NavGroup } from '$lib/components/Sidebar.svelte';
	import ThemeToggle from '$lib/components/ThemeToggle.svelte';
	import Button from '$lib/components/Button.svelte';
	import { firstTimeThisSession, renewalNudge } from '$lib/renewalNudge';
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

	let menuOpen = $state(false);
	let renewOpen = $state(false);
	let plans = $state<Plans | null>(null);

	// Expiration nudges: banner at 3 days or less, modal once per session at 1 day or less.
	// Expired resellers are already redirected to /painel/renovar by the layout load.
	const nudge = $derived(data.user.is_expired ? null : renewalNudge(data.user.expires_at));
	let nudgeOpen = $state(false);
	$effect(() => {
		if (nudge?.level === 'modal' && firstTimeThisSession(nudge.sessionKey)) nudgeOpen = true;
	});

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

<div class="flex min-h-dvh lg:h-screen lg:overflow-hidden">
	<Sidebar
		bind:open={menuOpen}
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
			class="sticky top-0 z-30 flex h-16 shrink-0 items-center gap-2 border-b border-slate-200 bg-white px-4 sm:gap-4 lg:px-6 dark:border-slate-800 dark:bg-slate-900"
		>
			<button
				type="button"
				class="-ml-2 rounded-lg p-2 text-slate-600 hover:bg-slate-100 lg:hidden dark:text-slate-300 dark:hover:bg-slate-800"
				aria-label="Abrir menu"
				aria-expanded={menuOpen}
				onclick={() => (menuOpen = true)}
			>
				<svg
					class="size-6"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2"
					aria-hidden="true"
				>
					<path d="M4 7h16M4 12h16M4 17h16" stroke-linecap="round" />
				</svg>
			</button>
			{#if data.platform.credits_enabled}
				<span
					class="rounded-full bg-brand-50 px-3 py-1 text-sm font-medium text-brand-700 dark:bg-brand-900/40 dark:text-brand-200"
				>
					<span class="hidden sm:inline">Créditos: </span>{data.user.credits}
				</span>
			{/if}
			<span class="ml-auto min-w-0 truncate text-sm text-slate-500 dark:text-slate-400">
				<strong class="text-slate-800 dark:text-slate-100">{data.user.name}</strong>
			</span>
			<ThemeToggle />
			<button
				type="button"
				class="min-h-11 shrink-0 rounded-lg px-2 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 sm:min-h-0 sm:px-3 dark:text-slate-300 dark:hover:bg-slate-800"
				onclick={logout}>Sair</button
			>
		</header>
		<main class="flex-1 p-4 lg:overflow-y-auto lg:p-6">
			<div class="mx-auto max-w-7xl">
				{#if nudge && nudge.level !== 'none'}
					<div
						class="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/50 dark:text-amber-100"
						role="status"
						data-testid="renewal-banner"
					>
						<p>
							<strong>{nudge.message}.</strong>
							Renove para manter seus clientes assistindo sem interrupção.
						</p>
						<Button size="sm" onclick={openRenewal}>Renovar agora</Button>
					</div>
				{/if}
				{@render children()}
			</div>
		</main>
	</div>
</div>

{#if nudge}
	<Modal bind:open={nudgeOpen} title={nudge.message} size="sm">
		<p class="text-sm text-slate-600 dark:text-slate-300">
			Depois do vencimento o painel fica restrito à renovação e os apps dos seus clientes passam a
			mostrar "Expirado". Renove agora por Pix; a confirmação é automática.
		</p>
		{#snippet footer()}
			<Button variant="secondary" onclick={() => (nudgeOpen = false)}>Depois</Button>
			<Button
				onclick={() => {
					nudgeOpen = false;
					openRenewal();
				}}>Renovar agora</Button
			>
		{/snippet}
	</Modal>
{/if}

<Modal bind:open={renewOpen} title="Renovar revenda">
	{#if plans}
		<Renewal {plans} onpaid={() => invalidateAll()} />
	{/if}
</Modal>
