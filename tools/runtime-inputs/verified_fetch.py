#!/usr/bin/env python3
"""Cyrene 2026-10-18: fail-closed downloads backed by official hash metadata.

No runtime pack may infer a trusted hash from the downloaded artifact itself.
The shipped lock is intentionally empty. This tool is not license clearance.
"""
import gzip
import hashlib
import json
import os
import shutil
from pathlib import Path
import re
import sys
import subprocess
from importlib.metadata import version as package_version
import tempfile
from urllib.parse import unquote, urlsplit
from urllib.request import HTTPRedirectHandler, build_opener

LOCK = Path(__file__).with_name("inputs.lock.json")


def fail(message):
    raise ValueError(message)


def official(url, host):
    p = urlsplit(url)
    if (p.scheme != "https" or p.hostname != host or p.port not in (None, 443)
            or p.username or p.password or p.query or p.fragment):
        fail("Only an exact official HTTPS URL is allowed: " + url)
    return p


class SameHostRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        official(newurl, urlsplit(req.full_url).hostname)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


OPENER = build_opener(SameHostRedirect())


def get_metadata(url):
    with OPENER.open(url, timeout=30) as response:
        data = response.read(32 * 1024 * 1024 + 1)
    if len(data) > 32 * 1024 * 1024:
        fail("Official metadata exceeds size limit")
    return data


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def locked_entry(url):
    lock = json.loads(LOCK.read_text(encoding="utf-8"))
    if lock.get("schemaVersion") != 1:
        fail("Unsupported input lock schema")
    entries = [entry for entry in lock["inputs"] if entry.get("url") == url]
    if len(entries) != 1:
        fail("BLOCKED: no unique verified official SHA256 lock for " + url
             + ". Obtain official hash/index evidence; do not guess or hash an untrusted download as a substitute.")
    entry = entries[0]
    for key in ("sha256", "sourceSha256"):
        if not re.fullmatch(r"[0-9a-f]{64}", entry.get(key, "")):
            fail("BLOCKED: missing/invalid " + key + " for " + url)
    host = urlsplit(url).hostname
    if host not in ("packages.termux.dev", "www.python.org"):
        fail("Unapproved artifact supplier")
    artifact = official(url, host)
    source = official(entry["sourceUrl"], host)
    if host == "packages.termux.dev":
        if not artifact.path.startswith("/apt/termux-main/pool/main/"):
            fail("Unexpected Termux artifact path")
        expected = "/apt/termux-main/dists/stable/main/binary-" + entry["indexArchitecture"] + "/Packages"
        if entry["indexArchitecture"] not in ("aarch64", "x86_64"):
            fail("Unsupported Termux index architecture")
        if source.path not in (expected, expected + ".gz") or entry["sourceFormat"] != "termux-packages":
            fail("Expected official Termux Packages index")
    elif entry["sourceFormat"] == "sigstore-bundle":
        # Identity policy comes from python.org, never from the untrusted bundle.
        name = artifact.path.rsplit("/", 1)[-1]
        if (not re.fullmatch(r"/ftp/python/3\.14\.8/python-3\.14\.8-(aarch64|x86_64)-linux-android\.tar\.gz", artifact.path)
                or entry["sourceUrl"] != url + ".sigstore"
                or entry.get("certIdentity") != "hugo@python.org"
                or entry.get("certOidcIssuer") != "https://github.com/login/oauth"
                or entry.get("buildProvenance") != "unconfirmed"
                or entry.get("architecture") != name.split("-", 3)[2]):
            fail("Unexpected Python release identity, ABI or provenance policy")
    elif not (artifact.path.startswith("/ftp/python/") and source.path.startswith("/ftp/python/")
              and entry["sourceFormat"] == "sha256sum"):
        fail("Expected supported official Python signature/checksum evidence")
    return entry


def verify_evidence(entry, raw=None):
    if raw is None and entry.get("evidenceFile"):
        evidence_root = LOCK.parent / "evidence"
        path = (evidence_root / entry["evidenceFile"]).resolve()
        if path.parent != evidence_root.resolve():
            fail("Evidence file must be inside the pinned evidence directory")
        raw = path.read_bytes()
    raw = get_metadata(entry["sourceUrl"]) if raw is None else raw
    original = raw
    if hashlib.sha256(raw).hexdigest() != entry["sourceSha256"]:
        fail("Official metadata changed: review and relock its evidence; refusing stale assumptions")
    if entry["sourceFormat"] == "sigstore-bundle":
        if package_version("sigstore") != "4.5.0":
            fail("Install the pinned Sigstore 4.5.0 verification environment")
        # Verify the pinned digest using the supplier's bundle and trusted client
        # roots. Cached artifacts are independently hashed by fetch/validate.
        # This authenticates a release publisher, not its build workflow.
        with tempfile.TemporaryDirectory(prefix="cyrene-sigstore-") as directory:
            bundle = Path(directory) / "release.sigstore"
            bundle.write_bytes(raw)
            result = subprocess.run(
                [sys.executable, "-m", "sigstore", "verify", "identity", "--offline",
                 "--bundle", str(bundle), "--cert-identity", entry["certIdentity"],
                 "--cert-oidc-issuer", entry["certOidcIssuer"], "sha256:" + entry["sha256"]],
                capture_output=True, text=True, timeout=120)
            if result.returncode != 0:
                fail("Python publisher verification failed: " + result.stderr[-2000:])
        return original
    if entry["sourceUrl"].endswith(".gz"):
        raw = gzip.decompress(raw)
    content = raw.decode("utf-8")
    expected = entry["sha256"]
    if entry["sourceFormat"] == "termux-packages":
        matches = []
        for paragraph in re.split(r"\n\s*\n", content.replace("\r\n", "\n")):
            fields = dict(line.split(": ", 1) for line in paragraph.splitlines()
                          if ": " in line and not line.startswith(" "))
            filename = fields.get("Filename", "")
            if unquote("https://packages.termux.dev/apt/termux-main/" + filename) == unquote(entry["url"]):
                matches.append(fields)
        if len(matches) != 1:
            fail("Artifact missing or ambiguous in official Packages index")
        record = matches[0]
        for field, key in (("Package", "package"), ("Version", "version"), ("Architecture", "architecture")):
            if record.get(field) != entry[key]:
                fail("Official package identity mismatch: " + field)
        if record.get("SHA256", "").lower() != expected:
            fail("Official package SHA256 does not match lock")
    else:
        name = unquote(urlsplit(entry["url"]).path.rsplit("/", 1)[-1])
        matches = []
        for line in content.splitlines():
            m = re.fullmatch(r"([0-9a-fA-F]{64}) [ *](.+)", line)
            if m and m[2] in (name, "./" + name):
                matches.append(m[1].lower())
        if matches != [expected]:
            fail("No unique matching SHA256 entry in official checksum source")

    return original

def fetch(entry, output):
    output = Path(output)
    if output.exists():
        if not output.is_file() or digest(output) != entry["sha256"]:
            fail("Cached input SHA256 mismatch; refusing reuse: " + str(output))
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=output.name + ".partial-", dir=output.parent)
    try:
        with os.fdopen(fd, "wb") as target, OPENER.open(entry["url"], timeout=30) as response:
            for chunk in iter(lambda: response.read(1024 * 1024), b""):
                target.write(chunk)
        if digest(Path(temporary)) != entry["sha256"]:
            fail("Downloaded input SHA256 mismatch: " + entry["url"])
        os.replace(temporary, output)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    output.with_suffix(output.suffix + ".input.json").write_text(
        json.dumps(entry, indent=2) + "\n", encoding="utf-8")


def main(args):
    if args and args[0] == "--check" and len(args) > 1:
        # Resolve every lock first: an absent hash stops before network or build output.
        entries = [locked_entry(url) for url in args[1:]]
        for entry in entries:
            verify_evidence(entry)
    elif len(args) == 2:
        entry = locked_entry(args[0])
        evidence = verify_evidence(entry)
        fetch(entry, args[1])
        evidence_dir = os.environ.get("MUSE_RUNTIME_EVIDENCE")
        if evidence_dir:
            base = Path(evidence_dir) / hashlib.sha256(entry["url"].encode()).hexdigest()
            shutil.copyfile(args[1], base.with_suffix(".archive"))
            base.with_suffix(".metadata").write_bytes(evidence)
            base.with_suffix(".json").write_text(json.dumps(entry), encoding="utf-8")
    else:
        fail("Usage: verified_fetch.py --check URL... | URL OUTPUT")


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except Exception as error:
        print("Runtime input gate FAILED: " + str(error), file=sys.stderr)
        sys.exit(1)
