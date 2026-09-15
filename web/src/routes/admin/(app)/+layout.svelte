<script lang="ts">
	import { goto } from '$app/navigation';
	import { post } from '$lib/api';
	import Sidebar, { type NavGroup } from '$lib/components/Sidebar.svelte';
	import ThemeToggle from '$lib/components/ThemeToggle.svelte';
	import { toast } from '$lib/stores/toast.svelte';

	let { data, children } = $props();

	const groups: NavGroup[] = [
		{
			items: [
				{ href: '/admin', label: 'Dashboard', exact: true },
				{ href: '/admin/revendedores', label: 'Revendedores' },
				{ href: '/admin/pagamentos', label: 'Pagamentos' },
				{ href: '/admin/auditoria', label: 'Auditoria' },
				{ href: '/admin/configuracoes', label: 'Configurações' }
			]
		}
	];

	async function logout() {
		try {
			await post('auth/logout');
		} catch {
			/* ignore */
		}
		toast.info('Sessão encerrada.');
		await goto('/admin/login');
	}
	let menuOpen = $state(false);
</script>

<svelte:head><title>{data.platformName} · Admin</title></svelte:head>

<div class="flex min-h-dvh lg:h-screen lg:overflow-hidden">
	<Sidebar
		bind:open={menuOpen}
		platformName={data.platformName}
		{groups}
		footer="Painel administrativo"
	/>
	<div class="flex min-w-0 flex-1 flex-col">
		<header
			class="sticky top-0 z-30 flex h-16 shrink-0 items-center gap-2 border-b border-slate-200 bg-white px-4 sm:gap-3 lg:px-6 dark:border-slate-800 dark:bg-slate-900"
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
			<span class="ml-auto min-w-0 truncate text-sm text-slate-500 dark:text-slate-400">
				<span class="hidden sm:inline">Olá, </span><strong
					class="text-slate-800 dark:text-slate-100">{data.user.username}</strong
				>
			</span>
			<ThemeToggle />
			<button
				type="button"
				class="min-h-11 shrink-0 rounded-lg px-2 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 sm:min-h-0 sm:px-3 dark:text-slate-300 dark:hover:bg-slate-800"
				onclick={logout}>Sair</button
			>
		</header>
		<main class="flex-1 p-4 lg:overflow-y-auto lg:p-6">
			<div class="mx-auto max-w-7xl">{@render children()}</div>
		</main>
	</div>
</div>
