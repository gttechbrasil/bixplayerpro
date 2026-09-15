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
	import logoLight from '$lib/assets/logo-light.png';
	import logoDark from '$lib/assets/logo-dark.png';
	import type { Snippet } from 'svelte';
	import { page } from '$app/state';

	let {
		platformName = 'Painel',
		groups,
		footer = '',
		top,
		open = $bindable(false)
	}: {
		platformName?: string;
		groups: NavGroup[];
		footer?: string;
		top?: Snippet;
		open?: boolean;
	} = $props();

	function active(href: string, exact?: boolean) {
		const p = page.url.pathname;
		return exact ? p === href : p === href || p.startsWith(href + '/');
	}

	// Below `lg` the sidebar is a drawer over the content (M5-026): a 240px column left a
	// phone with ~130px of usable width. Navigating, Escape and the backdrop all close it.
	function close() {
		open = false;
	}

	function onkeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') close();
	}
</script>

<svelte:window {onkeydown} />

{#if open}
	<button
		type="button"
		class="fixed inset-0 z-40 bg-slate-900/60 backdrop-blur-sm lg:hidden"
		aria-label="Fechar menu"
		onclick={close}
	></button>
{/if}

<aside
	class="fixed inset-y-0 left-0 z-50 flex h-full w-72 max-w-[85vw] shrink-0 flex-col border-r border-slate-200 bg-white transition-transform duration-200 lg:static lg:z-auto lg:w-60 lg:max-w-none lg:translate-x-0 dark:border-slate-800 dark:bg-slate-900 {open
		? 'translate-x-0'
		: '-translate-x-full'}"
>
	<div
		class="flex h-16 shrink-0 items-center justify-between gap-2 border-b border-slate-200 px-5 dark:border-slate-800"
	>
		<img src={logoLight} alt={platformName} class="h-8 w-auto dark:hidden" />
		<img src={logoDark} alt={platformName} class="hidden h-8 w-auto dark:block" />
		<button
			type="button"
			class="-mr-2 min-h-11 min-w-11 rounded-lg p-2 text-lg leading-none text-slate-500 hover:bg-slate-100 lg:hidden dark:hover:bg-slate-800"
			aria-label="Fechar menu"
			onclick={close}>✕</button
		>
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
						onclick={close}
						class="flex min-h-11 items-center rounded-lg px-3 text-sm font-medium transition {active(
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
