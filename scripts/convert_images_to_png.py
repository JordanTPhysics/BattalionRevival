#!/usr/bin/env python3
"""
Recursively convert images (default: WebP) to PNG.

Requires Pillow:
  pip install pillow

Example (mirror tree under a new folder, good for reviewing before swapping assets):
  python scripts/convert_images_to_png.py --src src/main/resources/assets --dst build/png-assets

Example (only WebP files):
  python scripts/convert_images_to_png.py --src src/main/resources/assets --dst out/png --ext webp

Example (PNGs next to WebPs in the same folder; WebPs are not removed):
  python scripts/convert_images_to_png.py --src src/main/resources/assets/terrain

Output PNG filenames are always lowercase (e.g. Archipelago_1.webp -> archipelago_1.png).
Subfolders under --src are preserved; only the file basename is lowercased.

python scripts/convert_images_to_png.py --src C:/Users/Thijssenj/Projects/BattalionRevival/battalion-browser/public/assets/custom --dst C:/Users/Thijssenj/Projects/BattalionRevival/battalion-browser/public/assets/structures/heni --ext png
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parent.parent
    default_src = root / "src" / "main" / "resources" / "assets"

    p = argparse.ArgumentParser(description="Convert images under a folder tree to PNG.")
    p.add_argument(
        "--src",
        type=Path,
        default=default_src,
        help=f"Root folder to scan (default: {default_src})",
    )
    p.add_argument(
        "--dst",
        type=Path,
        default=None,
        help="Output root; structure under --src is preserved. Default: same as --src (write PNGs alongside sources)",
    )
    p.add_argument(
        "--ext",
        action="append",
        default=[],
        metavar="EXT",
        help="File extension to convert, without dot (repeatable). Default: webp",
    )
    p.add_argument(
        "--force",
        action="store_true",
        help="Overwrite existing PNGs",
    )
    p.add_argument(
        "--dry-run",
        action="store_true",
        help="Print actions only",
    )
    return p.parse_args()


def main() -> int:
    args = parse_args()

    try:
        from PIL import Image
    except ImportError:
        print("Missing dependency: install with  pip install pillow", file=sys.stderr)
        return 1
    src_root: Path = args.src.resolve()
    dst_root: Path = (args.dst.resolve() if args.dst is not None else src_root)
    exts = {e.lower().lstrip(".") for e in (args.ext or [])}
    if not exts:
        exts = {"webp"}

    if not src_root.is_dir():
        print(f"Source is not a directory: {src_root}", file=sys.stderr)
        return 1

    converted = 0
    skipped = 0
    errors = 0
    claimed_outputs: dict[Path, Path] = {}

    for path in sorted(src_root.rglob("*")):
        if not path.is_file():
            continue
        if path.suffix.lower().lstrip(".") not in exts:
            continue

        rel = path.relative_to(src_root)
        out_dir = dst_root / rel.parent
        out_path = out_dir / f"{rel.stem.lower().replace('-', '_')}.png"

        prior = claimed_outputs.get(out_path)
        if prior is not None and prior != path:
            print(
                f"ERROR: two sources map to the same output path {out_path}:\n  {prior}\n  {path}",
                file=sys.stderr,
            )
            errors += 1
            continue
        claimed_outputs[out_path] = path

        if not args.force and out_path.exists():
            skipped += 1
            continue

        if args.dry_run:
            print(f"would convert: {path} -> {out_path}")
            converted += 1
            continue

        out_path.parent.mkdir(parents=True, exist_ok=True)
        try:
            with Image.open(path) as im:
                # Preserve alpha if present (RGBA / P with transparency)
                im.save(out_path, format="PNG", optimize=True)
        except OSError as e:
            print(f"ERROR {path}: {e}", file=sys.stderr)
            errors += 1
            continue

        print(f"{path} -> {out_path}")
        converted += 1

    print(f"Done. converted={converted} skipped_existing={skipped} errors={errors}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
