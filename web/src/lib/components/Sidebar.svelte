<script lang="ts" module>
	export interface NavItem {
		href: string;
		label: string;
		exact?: boolean;
	}
	export interface NavGroup {
		title?: string;
		items: NavItem[];
	}
</script>

<script lang="ts">
	import type { Snippet } from 'svelte';
	import { page } from '$app/state';

	let {
		platformName = 'Painel',
		groups,
		footer = '',
		top
	}: { platformName?: string; groups: NavGroup[]; footer?: string; top?: Snippet } = $props();

	function active(href: string, exact?: boolean) {
		const p = page.url.pathname;
		return exact ? p === href : p === href || p.startsWith(href + '/');
	}
</script>

<aside
	class="flex h-full w-60 shrink-0 flex-col border-r border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900"
>
	<div class="flex h-16 items-center gap-2 border-b border-slate-200 px-5 dark:border-slate-800">
		<span class="grid h-8 w-8 place-items-center rounded-lg bg-brand-600 font-bold text-white">
			{platformName.slice(0, 1).toUpperCase()}
		</span>
		<span class="truncate font-semibold">{platformName}</span>
	</div>
	{#if top}
		<div class="border-b border-slate-200 p-3 dark:border-slate-800">{@render top()}</div>
	{/if}
	<nav class="flex-1 overflow-y-auto p-3" aria-label="Menu principal">
		{#each groups as group, gi (gi)}
			{#if group.title}
				<p
					class="mt-3 mb-1 px-3 text-[11px] font-semibold tracking-wider text-slate-400 uppercase first:mt-0"
				>
					{group.title}
				</p>
			{/if}
			<div class="space-y-1">
				{#each group.items as item (item.href)}
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
			</div>
		{/each}
	</nav>
	{#if footer}
		<div class="border-t border-slate-200 p-4 text-xs text-slate-400 dark:border-slate-800">
			{footer}
		</div>
	{/if}
</aside>
