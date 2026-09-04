<script lang="ts">
	import { api, errorMessage, put } from '$lib/api';
	import Button from '$lib/components/Button.svelte';
	import Input from '$lib/components/Input.svelte';
	import { toast } from '$lib/stores/toast.svelte';
	import type { Branding } from '$lib/types';

	let {
		kind,
		label,
		current,
		onchange
	}: {
		kind: 'logo' | 'bg';
		label: string;
		current: string | null;
		onchange?: (url: string | null) => void;
	} = $props();

	// svelte-ignore state_referenced_locally
	let url = $state(current ?? '');
	let saving = $state(false);
	let uploading = $state(false);
	let fileInput = $state<HTMLInputElement | null>(null);
	const field = $derived(kind === 'logo' ? 'logo_url' : 'bg_url');

	async function saveUrl(e: SubmitEvent) {
		e.preventDefault();
		saving = true;
		try {
			const b = await put<Branding>('reseller/branding', { [field]: url.trim() || null });
			url = b[field] ?? '';
			toast.success(`${label} atualizado.`);
			onchange?.(b[field]);
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			saving = false;
		}
	}

	async function upload(e: Event) {
		const file = (e.currentTarget as HTMLInputElement).files?.[0];
		if (!file) return;
		if (file.size > 2 * 1024 * 1024) {
			toast.error('Imagem muito grande. O limite é 2 MB.');
			return;
		}
		uploading = true;
		try {
			const form = new FormData();
			form.append('file', file);
			const res = await fetch(`/api/v1/reseller/branding/upload?kind=${kind}`, {
				method: 'POST',
				body: form,
				headers: { 'X-CSRF-Token': document.cookie.match(/csrf_token=([^;]+)/)?.[1] ?? '' },
				credentials: 'same-origin'
			});
			if (!res.ok) {
				const body = await res.json().catch(() => null);
				throw new Error(body?.detail?.message ?? `Erro ${res.status}`);
			}
			const data = (await res.json()) as { url: string };
			url = data.url;
			toast.success('Imagem enviada.');
			onchange?.(data.url);
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			uploading = false;
			if (fileInput) fileInput.value = '';
		}
	}
</script>

<div class="grid gap-6 lg:grid-cols-2">
	<div class="card p-5">
		<h2 class="mb-3 font-semibold">Pré-visualização</h2>
		<div
			class="flex h-56 items-center justify-center overflow-hidden rounded-lg border border-dashed border-slate-300 bg-[repeating-conic-gradient(#e2e8f0_0%_25%,transparent_0%_50%)] bg-[length:16px_16px] dark:border-slate-700"
		>
			{#if url}
				<img src={url} alt={label} class="max-h-full max-w-full object-contain" />
			{:else}
				<span class="text-sm text-slate-500">Nenhuma imagem definida</span>
			{/if}
		</div>
	</div>
	<div class="space-y-6">
		<form class="card space-y-3 p-5" onsubmit={saveUrl}>
			<h2 class="font-semibold">Usar uma URL externa</h2>
			<Input
				type="url"
				placeholder="https://exemplo.com/{kind === 'logo' ? 'logo.png' : 'fundo.jpg'}"
				bind:value={url}
			/>
			<Button type="submit" loading={saving}>Salvar URL</Button>
		</form>
		<div class="card space-y-3 p-5">
			<h2 class="font-semibold">Ou enviar um arquivo</h2>
			<p class="text-sm text-slate-500">PNG, JPG ou WebP até 2 MB.</p>
			<input
				bind:this={fileInput}
				type="file"
				accept="image/png,image/jpeg,image/webp"
				class="block w-full text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-brand-600 file:px-4 file:py-2 file:text-white hover:file:bg-brand-700"
				disabled={uploading}
				onchange={upload}
			/>
			{#if uploading}<p class="text-sm text-slate-500">Enviando…</p>{/if}
		</div>
	</div>
</div>
