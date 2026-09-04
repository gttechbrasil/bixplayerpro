<script lang="ts">
	import { page } from '$app/state';

	let { platformName = 'Painel' }: { platformName?: string } = $props();

	const items = [
		{ href: '/admin', label: 'Dashboard', exact: true },
		{ href: '/admin/revendedores', label: 'Revendedores' },
		{ href: '/admin/pagamentos', label: 'Pagamentos' },
		{ href: '/admin/auditoria', label: 'Auditoria' },
		{ href: '/admin/configuracoes', label: 'Configurações' }
	];

	function active(href: string, exact?: boolean) {
		const p = page.url.pathname;
		return exact ? p === href : p === href || p.startsWith(href + '/');
	}
</script>

<aside
	class="flex h-full w-60 shrink-0 flex-col border-r border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900"
>
	<div class="flex h-16 items-center gap-2 border-b border-slate-200 px-5 dark:border-slate-800">
		<span class="grid h-8 w-8 place-items-center rounded-lg bg-brand-600 font-bold text-white"
			>A</span
		>
		<span class="truncate font-semibold">{platformName}</span>
	</div>
	<nav class="flex-1 space-y-1 p-3" aria-label="Menu principal">
		{#each items as item (item.href)}
			<a
				href={item.href}
				class="block rounded-lg px-3 py-2 text-sm font-medium transition {active(
					item.href,
					item.exact
				)
					? 'bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200'
					: 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'}"
				aria-current={active(item.href, item.exact) ? 'page' : undefined}
			>
				{item.label}
			</a>
		{/each}
	</nav>
	<div class="border-t border-slate-200 p-4 text-xs text-slate-400 dark:border-slate-800">
		Painel administrativo
	</div>
</aside>
