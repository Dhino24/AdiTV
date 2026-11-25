#!/usr/bin/env python3
"""Publie une version pour la mise à jour automatique d'ADI TV.

1. Vérifie que les APK « rapide » (Cube, Stick) sont signés avec LA clé d'ADI TV (empreinte ci-dessous) :
   une autre clé serait refusée par Android à la mise à jour.
2. Écrit updates/latest.json (version, notes, URL, taille, SHA-256 de chaque APK).

Usage :
  python3 scripts/publish_update.py --release vX.Y.Z   # APK publiés comme fichiers d'une « release » GitHub (CI)
  python3 scripts/publish_update.py                     # APK copiés dans updates/ (publication manuelle)
Notes de version : updates/NOTES.txt.
"""
import glob, hashlib, json, os, re, shutil, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BRANCH = "claude/new-session-9assu0"
RAW = f"https://raw.githubusercontent.com/dhino24/aditv/{BRANCH}/updates/"
# Empreinte SHA-256 du certificat d'ADI TV (versions installées depuis la 0.1).
CERT_SHA256 = "8ff3e1ced69c4e20c45674ce1f17b7f871c50fcb9cb2f8d68794c05fee9b694e"


def apksigner() -> str:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "/opt/android-sdk"
    found = sorted(glob.glob(f"{sdk}/build-tools/*/apksigner"))
    if not found:
        sys.exit("apksigner introuvable (Android SDK build-tools).")
    return found[-1]


def check_signature(apk: Path) -> None:
    out = subprocess.run([apksigner(), "verify", "--print-certs", str(apk)], capture_output=True, text=True).stdout
    m = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", out)
    if not m or m.group(1) != CERT_SHA256:
        sys.exit(f"{apk.name} : signé avec une AUTRE clé ({m.group(1) if m else '?'}) → les Fire TV refuseraient la mise à jour. Publication annulée.")


args = sys.argv[1:]
release = args[args.index("--release") + 1] if "--release" in args else None
gradle = (ROOT / "app/build.gradle.kts").read_text()
code = int(re.search(r"versionCode = (\d+)", gradle).group(1))
name = re.search(r'versionName = "([^"]+)"', gradle).group(1)
notes_file = ROOT / "updates/NOTES.txt"
out = ROOT / "updates"; out.mkdir(exist_ok=True)
manifest = {"versionCode": code, "versionName": name, "notes": notes_file.read_text().strip() if notes_file.exists() else ""}
dist = ROOT / "app/build/dist"; dist.mkdir(parents=True, exist_ok=True)
for flavor in ("cube", "stick"):
    src = ROOT / f"app/build/outputs/apk/{flavor}/rapide/app-{flavor}-rapide.apk"
    check_signature(src)
    target = (dist if release else out) / f"ADITV-{flavor}.apk"
    shutil.copyfile(src, target)
    data = target.read_bytes()
    url = f"https://github.com/dhino24/aditv/releases/download/{release}/{target.name}" if release else RAW + target.name
    manifest[flavor] = {"url": url, "sha256": hashlib.sha256(data).hexdigest(), "size": len(data)}
(out / "latest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
print(json.dumps(manifest, ensure_ascii=False, indent=2))
