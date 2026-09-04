<script lang="ts">
	import ImageField from '$lib/components/ImageField.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';

	let { data } = $props();
</script>

<PageHeader title="Logomarca" subtitle="Exibida na tela inicial do app" />
{#key data.user.logo_url}
	<ImageField kind="logo" label="Logomarca" current={data.user.logo_url} />
{/key}
