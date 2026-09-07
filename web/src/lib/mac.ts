/**
 * MAC address input mask (M5-015): keeps only hex digits, upper-cases them and groups them
 * in pairs separated by ":" — typing "aabbcc" gives "AA:BB:CC", pasting "aa-bb-cc-dd-ee-ff"
 * or "AABBCCDDEEFF" gives "AA:BB:CC:DD:EE:FF". Never longer than 17 characters.
 */
export function formatMac(raw: string): string {
	const hex = raw
		.replace(/[^0-9a-fA-F]/g, '')
		.toUpperCase()
		.slice(0, 12);
	return hex.match(/.{1,2}/g)?.join(':') ?? '';
}

export function isCompleteMac(value: string): boolean {
	return /^[0-9A-F]{2}(:[0-9A-F]{2}){5}$/.test(value);
}
