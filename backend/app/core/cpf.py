"""CPF: stored as 11 digits, validated by its check digits, never logged in full."""

import re


def is_valid_cpf(digits: str) -> bool:
    if not re.fullmatch(r"\d{11}", digits) or len(set(digits)) == 1:
        return False
    for size in (9, 10):
        total = sum(int(d) * (size + 1 - i) for i, d in enumerate(digits[:size]))
        if (total * 10) % 11 % 10 != int(digits[size]):
            return False
    return True


def normalize_cpf(value: str | None) -> str | None:
    """The 11 digits of a valid CPF, `None` when empty; `ValueError` when it is not a CPF."""
    digits = re.sub(r"\D", "", value or "")
    if not digits:
        return None
    if not is_valid_cpf(digits):
        raise ValueError("invalid cpf")
    return digits


def mask_cpf(digits: str | None) -> str | None:
    """Last two digits only, for the audit log: enough to tell two CPFs apart, not to read one."""
    return f"*********{digits[-2:]}" if digits else None
