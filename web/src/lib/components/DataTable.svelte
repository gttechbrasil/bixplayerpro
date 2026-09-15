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
		card,
		filters,
		onchange,
		selectable = false,
		rowId = (item: T) => (item as { id: number }).id,
		selected = $bindable(new Set<number>())
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
		/** Phone layout (M5-026): when given, below `md` the rows render as cards instead of a
		 *  table, because a 6-column table on a 390px screen is only reachable by side-scrolling. */
		card?: Snippet<[T]>;
		filters?: Snippet;
		onchange: (q: Query) => void;
		selectable?: boolean;
		rowId?: (item: T) => number;
		selected?: Set<number>;
	} = $props();

	const pageIds = $derived(items.map(rowId));
	const allSelected = $derived(pageIds.length > 0 && pageIds.every((id) => selected.has(id)));
	const someSelected = $derived(!allSelected && pageIds.some((id) => selected.has(id)));

	function toggleAll() {
		const next = new Set(selected);
		if (allSelected) pageIds.forEach((id) => next.delete(id));
		else pageIds.forEach((id) => next.add(id));
		selected = next;
	}

	function toggleOne(id: number) {
		const next = new Set(selected);
		if (next.has(id)) next.delete(id);
		else next.add(id);
		selected = next;
	}

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
		class="flex flex-col gap-3 border-b border-slate-200 p-4 sm:flex-row sm:flex-wrap sm:items-center dark:border-slate-800"
	>
		{#if searchable}
			<form class="w-full sm:min-w-64 sm:flex-1" onsubmit={onSearchSubmit}>
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
			<div class="flex flex-wrap gap-2 *:w-full sm:*:w-auto">{@render filters()}</div>
		{/if}
		<div class="flex items-center gap-2 text-sm text-slate-500 sm:ml-auto">
			<label class="hidden sm:inline" for="table-per-page">Por página</label>
			<span class="sr-only sm:hidden" id="table-per-page-label">Itens por página</span>
			<select
				id="table-per-page"
				aria-labelledby="table-per-page-label"
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

	{#if card}
		<ul class="divide-y divide-slate-100 md:hidden dark:divide-slate-800/70" aria-busy={loading}>
			{#if items.length === 0}
				<li class="px-4 py-10 text-center text-sm text-slate-500">{empty}</li>
			{:else}
				{#each items as item, i (i)}
					<li
						class="flex items-start gap-3 px-4 py-3"
						class:bg-brand-50={selectable && selected.has(rowId(item))}
						class:dark:bg-brand-900={selectable && selected.has(rowId(item))}
					>
						{#if selectable}
							<input
								type="checkbox"
								class="mt-1 size-5 shrink-0 rounded"
								aria-label="Selecionar linha"
								checked={selected.has(rowId(item))}
								onchange={() => toggleOne(rowId(item))}
							/>
						{/if}
						<div class="min-w-0 flex-1">{@render card(item)}</div>
					</li>
				{/each}
			{/if}
		</ul>
		{#if selectable && items.length > 0}
			<button
				type="button"
				class="w-full border-t border-slate-200 px-4 py-2 text-sm font-medium text-brand-700 md:hidden dark:border-slate-800 dark:text-brand-300"
				onclick={toggleAll}
			>
				{allSelected ? 'Limpar seleção' : 'Selecionar todos desta página'}
			</button>
		{/if}
	{/if}

	<div class="overflow-x-auto {card ? 'hidden md:block' : ''}" aria-busy={loading}>
		<table class="min-w-full divide-y divide-slate-200 dark:divide-slate-800">
			<thead class="bg-slate-50 dark:bg-slate-900/60">
				<tr>
					{#if selectable}
						<th scope="col" class="table-th w-10">
							<input
								type="checkbox"
								class="rounded"
								aria-label="Selecionar todos"
								checked={allSelected}
								indeterminate={someSelected}
								onchange={toggleAll}
							/>
						</th>
					{/if}
					{#each columns as col (col.key)}
						<th scope="col" class="table-th {col.class ?? ''}">{col.label}</th>
					{/each}
				</tr>
			</thead>
			<tbody class="divide-y divide-slate-100 dark:divide-slate-800/70">
				{#if items.length === 0}
					<tr>
						<td
							class="table-td py-10 text-center text-slate-500"
							colspan={columns.length + (selectable ? 1 : 0)}
						>
							{empty}
						</td>
					</tr>
				{:else}
					{#each items as item, i (i)}
						<tr
							class="hover:bg-slate-50 dark:hover:bg-slate-800/40"
							class:bg-brand-50={selectable && selected.has(rowId(item))}
						>
							{#if selectable}
								<td class="table-td w-10">
									<input
										type="checkbox"
										class="rounded"
										aria-label="Selecionar linha"
										checked={selected.has(rowId(item))}
										onchange={() => toggleOne(rowId(item))}
									/>
								</td>
							{/if}
							{@render row(item)}
						</tr>
					{/each}
				{/if}
			</tbody>
		</table>
	</div>

	<div
		class="flex flex-wrap items-center justify-between gap-2 border-t border-slate-200 px-4 py-3 text-sm text-slate-500 dark:border-slate-800"
	>
		<span>Mostrando {from}–{to} de {total}</span>
		<nav class="flex items-center gap-1" aria-label="Paginação">
			<button
				type="button"
				class="rounded-md px-3 py-2 hover:bg-slate-100 disabled:opacity-40 sm:px-2 sm:py-1 dark:hover:bg-slate-800"
				disabled={page <= 1}
				onclick={() => emit({ page: 1 })}
				aria-label="Primeira página">«</button
			>
			<button
				type="button"
				class="rounded-md px-3 py-2 hover:bg-slate-100 disabled:opacity-40 sm:px-2 sm:py-1 dark:hover:bg-slate-800"
				disabled={page <= 1}
				onclick={() => emit({ page: page - 1 })}
				aria-label="Página anterior">‹</button
			>
			<span class="px-2">Página {page} de {pages}</span>
			<button
				type="button"
				class="rounded-md px-3 py-2 hover:bg-slate-100 disabled:opacity-40 sm:px-2 sm:py-1 dark:hover:bg-slate-800"
				disabled={page >= pages}
				onclick={() => emit({ page: page + 1 })}
				aria-label="Próxima página">›</button
			>
			<button
				type="button"
				class="rounded-md px-3 py-2 hover:bg-slate-100 disabled:opacity-40 sm:px-2 sm:py-1 dark:hover:bg-slate-800"
				disabled={page >= pages}
				onclick={() => emit({ page: pages })}
				aria-label="Última página">»</button
			>
		</nav>
	</div>
</div>
