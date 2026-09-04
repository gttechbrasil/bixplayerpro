<script lang="ts">
	import { errorMessage, put } from '$lib/api';
	import Button from '$lib/components/Button.svelte';
	import Input from '$lib/components/Input.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';
	import { formatDate } from '$lib/format';
	import { toast } from '$lib/stores/toast.svelte';

	let { data } = $props();
	let current = $state('');
	let next = $state('');
	let confirm = $state('');
	let saving = $state(false);

	async function save(e: SubmitEvent) {
		e.preventDefault();
		if (next !== confirm) return toast.warning('A confirmação não confere com a nova senha.');
		saving = true;
		try {
			await put('reseller/profile/password', { current_password: current, new_password: next });
			toast.success('Senha alterada.');
			current = next = confirm = '';
		} catch (err) {
			toast.error(errorMessage(err));
		} finally {
			saving = false;
		}
	}
</script>

<PageHeader title="Perfil" subtitle="Dados da sua conta" />

<div class="grid gap-6 lg:grid-cols-2">
	<div class="card space-y-4 p-6">
		<Input label="Nome" value={data.user.name} readonly disabled />
		<Input label="Usuário" value={data.user.username} readonly disabled />
		<Input
			label="Vencimento"
			value={data.user.expires_at ? formatDate(data.user.expires_at) : 'Sem vencimento'}
			readonly
			disabled
		/>
	</div>
	<form class="card space-y-4 p-6" onsubmit={save}>
		<h2 class="font-semibold">Alterar senha</h2>
		<Input
			label="Senha atual"
			type="password"
			autocomplete="current-password"
			required
			bind:value={current}
		/>
		<Input
			label="Nova senha"
			type="password"
			autocomplete="new-password"
			required
			minlength={6}
			bind:value={next}
		/>
		<Input
			label="Confirmar nova senha"
			type="password"
			autocomplete="new-password"
			required
			minlength={6}
			bind:value={confirm}
		/>
		<Button type="submit" loading={saving}>Atualizar senha</Button>
	</form>
</div>
