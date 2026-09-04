<script lang="ts">
	import ImageField from '$lib/components/ImageField.svelte';
	import PageHeader from '$lib/components/PageHeader.svelte';

	let { data } = $props();
</script>

<PageHeader title="Background" subtitle="Imagem de fundo do app (recomendado 1920×1080)" />
{#key data.user.bg_url}
	<ImageField kind="bg" label="Background" current={data.user.bg_url} />
{/key}
