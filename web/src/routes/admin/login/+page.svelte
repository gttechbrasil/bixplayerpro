<script lang="ts">
	import { goto } from '$app/navigation';
	import { errorMessage, post } from '$lib/api';
	import Button from '$lib/components/Button.svelte';
	import Input from '$lib/components/Input.svelte';
	import ThemeToggle from '$lib/components/ThemeToggle.svelte';

	let username = $state('');
	let password = $state('');
	let error = $state('');
	let loading = $state(false);

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		error = '';
		loading = true;
		try {
			await post('auth/admin/login', { username, password });
			await goto('/admin', { invalidateAll: true });
		} catch (err) {
			error = errorMessage(err);
		} finally {
			loading = false;
		}
	}
</script>

<svelte:head><title>Entrar · Painel administrativo</title></svelte:head>

<div class="flex min-h-screen items-center justify-center p-4">
	<div class="absolute top-4 right-4"><ThemeToggle /></div>
	<form class="card w-full max-w-sm space-y-4 p-8" onsubmit={submit}>
		<div class="text-center">
			<div
				class="mx-auto grid h-12 w-12 place-items-center rounded-xl bg-brand-600 text-xl font-bold text-white"
			>
				A
			</div>
			<h1 class="mt-4 text-xl font-semibold">Painel administrativo</h1>
			<p class="mt-1 text-sm text-slate-500">Entre com sua conta de administrador.</p>
		</div>
		<Input label="Usuário" name="username" autocomplete="username" required bind:value={username} />
		<Input
			label="Senha"
			name="password"
			type="password"
			autocomplete="current-password"
			required
			bind:value={password}
		/>
		{#if error}
			<p
				class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-200"
				role="alert"
			>
				{error}
			</p>
		{/if}
		<Button type="submit" class="w-full" {loading}>Entrar</Button>
	</form>
</div>
