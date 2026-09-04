<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { errorMessage, post } from '$lib/api';
	import Button from '$lib/components/Button.svelte';
	import Input from '$lib/components/Input.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { toast } from '$lib/stores/toast.svelte';

	let { data } = $props();
	let fromHost = $state('');
	let toHost = $state('');
	let loading = $state(false);
	let result = $state<{ from_host: string; to_host: string; affected: number } | null>(null);

	async function migrate(e: SubmitEvent) {
		e.preventDefault();
		if (!fromHost) return toast.warning('Selecione a DNS atual.');
		loading = true;
		result = null;
		try {
			result = await post('reseller/dns/migrate', { from_host: fromHost, to_host: toHost.trim() });
			toast.success('DNS migrada com sucesso!');
			toHost = '';
			await invalidateAll();
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			loading = false;
		}
	}
</script>

<PageHeader
	title="Migrador de DNS"
	subtitle="Troca o endereço do servidor em todas as playlists de uma só vez"
/>

<div class="grid gap-6 lg:grid-cols-2">
	<form class="card space-y-4 p-6" onsubmit={migrate}>
		<div>
			<label class="label" for="dns-from">DNS atual</label>
			<select id="dns-from" class="input" required bind:value={fromHost}>
				<option value="" disabled>Selecione…</option>
				{#each data.hosts as h (h.host)}
					<option value={h.host}
						>{h.host} ({h.playlists} playlist{h.playlists === 1 ? '' : 's'})</option
					>
				{/each}
			</select>
			{#if data.hosts.length === 0}
				<p class="mt-1 text-xs text-slate-500">Nenhuma playlist cadastrada ainda.</p>
			{/if}
		</div>
		<Input
			label="Nova DNS"
			placeholder="https://novadns.com:8080"
			required
			hint="Somente o endereço do servidor; caminho, usuário e senha das playlists são preservados."
			bind:value={toHost}
		/>
		<Button type="submit" {loading} disabled={data.hosts.length === 0}>Migrar</Button>
		{#if result}
			<p
				class="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-800 dark:bg-emerald-950 dark:text-emerald-200"
			>
				{result.affected} playlist(s) migrada(s) de <strong>{result.from_host}</strong> para
				<strong>{result.to_host}</strong>.
			</p>
		{/if}
	</form>

	<div class="card p-6">
		<h2 class="font-semibold">DNS em uso</h2>
		<ul class="mt-3 divide-y divide-slate-100 text-sm dark:divide-slate-800">
			{#each data.hosts as h (h.host)}
				<li class="flex justify-between py-2">
					<span class="font-mono">{h.host}</span>
					<span class="text-slate-500">{h.playlists}</span>
				</li>
			{:else}
				<li class="py-2 text-slate-500">Nenhuma.</li>
			{/each}
		</ul>
	</div>
</div>
