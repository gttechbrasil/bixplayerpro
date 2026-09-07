<script lang="ts">
	import './layout.css';
	import favicon from '$lib/assets/favicon.png';
	import Toasts from '$lib/components/Toasts.svelte';

	let { children } = $props();
</script>

<svelte:head><link rel="icon" href={favicon} /></svelte:head>
{@render children()}
<Toasts />
