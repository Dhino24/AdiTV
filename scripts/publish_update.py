#!/usr/bin/env python3
"""Publie une version pour la mise à jour automatique d'ADI TV.

Copie les APK « rapide » (Cube et Stick) dans updates/ et écrit updates/latest.json
(numéro de version, notes, URL, taille et SHA-256 de chaque APK). Les Fire TV le lisent
au lancement sur le dépôt GitHub public, puis vérifient l'empreinte avant d'installer.

Usage : python3 scripts/publish_update.py "Notes de version"
"""
import hashlib, json, re, shutil, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BRANCH = "claude/new-session-9assu0"
RAW = f"https://raw.githubusercontent.com/dhino24/aditv/{BRANCH}/updates/"

gradle = (ROOT / "app/build.gradle.kts").read_text()
code = int(re.search(r"versionCode = (\d+)", gradle).group(1))
name = re.search(r'versionName = "([^"]+)"', gradle).group(1)
out = ROOT / "updates"; out.mkdir(exist_ok=True)
manifest = {"versionCode": code, "versionName": name, "notes": sys.argv[1] if len(sys.argv) > 1 else ""}
for flavor in ("cube", "stick"):
    src = ROOT / f"app/build/outputs/apk/{flavor}/rapide/app-{flavor}-rapide.apk"
    dst = out / f"ADITV-{flavor}.apk"
    shutil.copyfile(src, dst)
    data = dst.read_bytes()
    manifest[flavor] = {"url": RAW + dst.name, "sha256": hashlib.sha256(data).hexdigest(), "size": len(data)}
(out / "latest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
print(json.dumps(manifest, ensure_ascii=False, indent=2))
