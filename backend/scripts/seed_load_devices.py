"""Creates N registered devices (with a playlist and a valid token) for the load test.

    uv run python scripts/seed_load_devices.py --count 2000 --out /tmp/tokens.txt

Refuses to run against a production database. Devices hang off the reseller `carga`
(created if missing) so they can be wiped with one DELETE afterwards:

    uv run python scripts/seed_load_devices.py --wipe
"""

import argparse
import asyncio
import secrets
from datetime import date
from pathlib import Path

from sqlalchemy import delete, select

from app.core.config import get_settings
from app.core.security import (
    generate_opaque_token,
    hash_device_identifier,
    hash_password,
    hash_token,
)
from app.db.session import dispose_engine, get_session_factory
from app.models import Device, Playlist, Reseller

RESELLER = "carga"
PLAYLIST_URL = "http://carga.exemplo.local:8080/get.php?username=u{n}&password=p{n}&type=m3u_plus"


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=2000)
    parser.add_argument("--out", type=Path, default=Path("loadtest-tokens.txt"))
    parser.add_argument("--wipe", action="store_true", help="delete the load-test reseller")
    args = parser.parse_args()

    if get_settings().is_production:
        raise SystemExit("refusing to seed load-test devices in production")

    factory = get_session_factory()
    async with factory() as db:
        reseller = await db.scalar(select(Reseller).where(Reseller.username == RESELLER))
        if args.wipe:
            if reseller is not None:
                await db.execute(delete(Device).where(Device.reseller_id == reseller.id))
                await db.delete(reseller)
                await db.commit()
            print("load-test data removed")
            await dispose_engine()
            return

        if reseller is None:
            reseller = Reseller(
                username=RESELLER,
                name="Revenda de carga",
                password_hash=hash_password(secrets.token_urlsafe(16)),
                credits=0,
                expires_at=date(2050, 1, 1),
            )
            db.add(reseller)
            await db.flush()

        tokens: list[str] = []
        prefix = secrets.token_hex(2).upper()
        for n in range(args.count):
            token = generate_opaque_token()
            mac = f"02:51:{prefix[:2]}:{prefix[2:]}:{n // 256:02X}:{n % 256:02X}"
            device = Device(
                reseller_id=reseller.id,
                mac_address=mac,
                device_id=hash_device_identifier(f"load-{prefix}-{n}"),
                client_name=f"Carga {n}",
                license_expires_at=date(2050, 1, 1),
                token_hash=hash_token(token),
                app_type="tv",
                app_version="1.1.0",
            )
            db.add(device)
            await db.flush()
            db.add(
                Playlist(
                    device_id=device.id,
                    name="Lista de carga",
                    type="m3u",
                    host="http://carga.exemplo.local:8080",
                    url=PLAYLIST_URL.format(n=n),
                    position=0,
                )
            )
            tokens.append(token)
            if n % 500 == 499:
                await db.commit()
        await db.commit()

    args.out.write_text("\n".join(tokens), encoding="utf-8")
    print(f"{len(tokens)} devices for reseller '{RESELLER}'; tokens in {args.out}")
    await dispose_engine()


if __name__ == "__main__":
    asyncio.run(main())
