<script lang="ts" module>
	export interface Column {
		key: string;
		label: string;
		class?: string;
	}

	export interface Query {
		page: number;
		per_page: number;
		search: string;
	}
</script>

<script lang="ts" generics="T">
	import type { Snippet } from 'svelte';

	let {
		columns,
		items,
		total,
		page,
		perPage,
		search = '',
		searchable = true,
		searchPlaceholder = 'Pesquisar…',
		empty = 'Nenhum registro encontrado.',
		loading = false,
		row,
		filters,
		onchange
	}: {
		columns: Column[];
		items: T[];
		total: number;
		page: number;
		perPage: number;
		search?: string;
		searchable?: boolean;
		searchPlaceholder?: string;
		empty?: string;
		loading?: boolean;
		row: Snippet<[T]>;
		filters?: Snippet;
		onchange: (q: Query) => void;
	} = $props();

	let searchValue = $state('');
	let timer: ReturnType<typeof setTimeout> | undefined;

	$effect(() => {
		searchValue = search;
	});

	const pages = $derived(Math.max(1, Math.ceil(total / perPage)));
	const from = $derived(total === 0 ? 0 : (page - 1) * perPage + 1);
	const to = $derived(Math.min(total, page * perPage));

	function emit(patch: Partial<Query>) {
		onchange({ page, per_page: perPage, search: searchValue, ...patch });
	}

	function onSearchInput() {
		clearTimeout(timer);
		timer = setTimeout(() => emit({ page: 1, search: searchValue }), 350);
	}

	function onSearchSubmit(e: SubmitEvent) {
		e.preventDefault();
		clearTimeout(timer);
		emit({ page: 1, search: searchValue });
	}
</script>

<div class="card overflow-hidden">
	<div
		class="flex flex-wrap items-center gap-3 border-b border-slate-200 p-4 dark:border-slate-800"
	>
		{#if searchable}
			<form class="min-w-64 flex-1" onsubmit={onSearchSubmit}>
				<label class="sr-only" for="table-search">Pesquisar</label>
				<input
					id="table-search"
					type="search"
					class="input"
					placeholder={searchPlaceholder}
					bind:value={searchValue}
					oninput={onSearchInput}
				/>
			</form>
		{/if}
		{#if filters}
			{@render filters()}
		{/if}
		<div class="ml-auto flex items-center gap-2 text-sm text-slate-500">
			<label for="table-per-page">Por página</label>
			<select
				id="table-per-page"
				class="input w-auto py-1.5"
				value={perPage}
				onchange={(e) => emit({ page: 1, per_page: Number(e.currentTarget.value) })}
			>
				{#each [10, 25, 50, 100] as n (n)}
					<option value={n}>{n}</option>
				{/each}
			</select>
		</div>
	</div>

	<div class="overflow-x-auto" aria-busy={loading}>
		<table class="min-w-full divide-y divide-slate-200 dark:divide-slate-800">
			<thead class="bg-slate-50 dark:bg-slate-900/60">
				<tr>
					{#each columns as col (col.key)}
						<th scope="col" class="table-th {col.class ?? ''}">{col.label}</th>
					{/each}
				</tr>
			</thead>
			<tbody class="divide-y divide-slate-100 dark:divide-slate-800/70">
				{#if items.length === 0}
					<tr>
						<td class="table-td py-10 text-center text-slate-500" colspan={columns.length}>
							{empty}
						</td>
					</tr>
				{:else}
					{#each items as item, i (i)}
						<tr class="hover:bg-slate-50 dark:hover:bg-slate-800/40">
							{@render row(item)}
						</tr>
					{/each}
				{/if}
			</tbody>
		</table>
	</div>

	<div
		class="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 px-4 py-3 text-sm text-slate-500 dark:border-slate-800"
	>
		<span>Mostrando {from}–{to} de {total}</span>
		<nav class="flex items-center gap-1" aria-label="Paginação">
			<button
				type="button"
				class="rounded-md px-2 py-1 hover:bg-slate-100 disabled:opacity-40 dark:hover:bg-slate-800"
				disabled={page <= 1}
				onclick={() => emit({ page: 1 })}
				aria-label="Primeira página">«</button
			>
			<button
				type="button"
				class="rounded-md px-2 py-1 hover:bg-slate-100 disabled:opacity-40 dark:hover:bg-slate-800"
				disabled={page <= 1}
				onclick={() => emit({ page: page - 1 })}
				aria-label="Página anterior">‹</button
			>
			<span class="px-2">Página {page} de {pages}</span>
			<button
				type="button"
				class="rounded-md px-2 py-1 hover:bg-slate-100 disabled:opacity-40 dark:hover:bg-slate-800"
				disabled={page >= pages}
				onclick={() => emit({ page: page + 1 })}
				aria-label="Próxima página">›</button
			>
			<button
				type="button"
				class="rounded-md px-2 py-1 hover:bg-slate-100 disabled:opacity-40 dark:hover:bg-slate-800"
				disabled={page >= pages}
				onclick={() => emit({ page: pages })}
				aria-label="Última página">»</button
			>
		</nav>
	</div>
</div>
