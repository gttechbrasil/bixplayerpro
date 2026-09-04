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
</script>

<svelte:head><title>{data.platformName} · Admin</title></svelte:head>

<div class="flex h-screen overflow-hidden">
	<Sidebar platformName={data.platformName} {groups} footer="Painel administrativo" />
	<div class="flex min-w-0 flex-1 flex-col">
		<header
			class="flex h-16 shrink-0 items-center justify-end gap-3 border-b border-slate-200 bg-white px-6 dark:border-slate-800 dark:bg-slate-900"
		>
			<span class="text-sm text-slate-500 dark:text-slate-400">
				Olá, <strong class="text-slate-800 dark:text-slate-100">{data.user.username}</strong>
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
