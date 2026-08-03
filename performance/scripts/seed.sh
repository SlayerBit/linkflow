#!/usr/bin/env bash
# Seed verified users + short URLs for k6 scenarios.
# Requires: curl, python3. Stack must be up with MailHog on MAILHOG_URL.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export BASE_URL="${BASE_URL:-https://localhost}"
export MAILHOG_URL="${MAILHOG_URL:-http://localhost:8025}"
export SEED_USERS="${SEED_USERS:-20}"
export SEED_URLS_PER_USER="${SEED_URLS_PER_USER:-3}"
export PERF_PASSWORD="${PERF_PASSWORD:-PerfT3st!Pass}"
export OUT_FILE="${SEED_FILE:-$ROOT/performance/data/seed.json}"
export INSECURE_SKIP_TLS_VERIFY="${INSECURE_SKIP_TLS_VERIFY:-true}"

mkdir -p "$(dirname "$OUT_FILE")"

python3 <<'PY'
import json, os, quopri, re, ssl, time, urllib.error, urllib.parse, urllib.request
from datetime import datetime, timezone

base = os.environ["BASE_URL"].rstrip("/")
mailhog = os.environ["MAILHOG_URL"].rstrip("/")
n_users = int(os.environ["SEED_USERS"])
urls_per = int(os.environ["SEED_URLS_PER_USER"])
password = os.environ["PERF_PASSWORD"]
out_file = os.environ["OUT_FILE"]
insecure = os.environ.get("INSECURE_SKIP_TLS_VERIFY", "true").lower() != "false"

ctx = ssl.create_default_context()
if insecure:
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE

TOKEN_RE = re.compile(r"verify-email\?token=([A-Za-z0-9_-]+)")


def http(method, url, body=None, headers=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    if body is not None:
        req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=30) as resp:
            raw = resp.read().decode()
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode(errors="replace")
        raise SystemExit(f"{method} {url} → HTTP {e.code}: {raw}") from e


def walk_mime(node, out):
    """Collect decoded leaf bodies from MailHog's nested MIME tree."""
    if not node:
        return
    # Bare MIME object: {"Parts": [...]}
    if isinstance(node.get("Parts"), list) and "Body" not in node:
        for part in node["Parts"]:
            walk_mime(part, out)
        return
    mime = node.get("MIME") or {}
    parts = mime.get("Parts") if isinstance(mime, dict) else None
    if parts:
        for part in parts:
            walk_mime(part, out)
        return
    headers = node.get("Headers") or {}
    encoding = ""
    for key, vals in headers.items():
        if key.lower() == "content-transfer-encoding" and vals:
            encoding = vals[0].lower()
            break
    body = node.get("Body") or ""
    if not body:
        return
    text = body
    if "quoted-printable" in encoding:
        text = quopri.decodestring(body.encode("utf-8", "ignore")).decode("utf-8", "ignore")
    out.append(text)


def extract_token(payload):
    chunks = []
    for item in payload.get("items") or []:
        walk_mime(item.get("MIME"), chunks)
        walk_mime(item.get("Content"), chunks)
    for text in chunks:
        m = TOKEN_RE.search(text)
        if m:
            return m.group(1)
    return None


def await_token(email, timeout_s=30):
    deadline = time.time() + timeout_s
    q = urllib.parse.quote(email)
    while time.time() < deadline:
        req = urllib.request.Request(f"{mailhog}/api/v2/search?kind=to&query={q}")
        with urllib.request.urlopen(req, timeout=10) as resp:
            payload = json.loads(resp.read().decode())
        token = extract_token(payload)
        if token:
            return token
        time.sleep(0.4)
    raise TimeoutError(f"No verification mail for {email}")


stamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
users = []
short_codes = []
url_ids = []

print(f"Seeding {n_users} users × {urls_per} URLs against {base}")

for i in range(1, n_users + 1):
    email = f"perf.{stamp}.{i}@example.com"
    print(f"  register {email}")
    status, _ = http("POST", f"{base}/api/v1/auth/register", {
        "email": email,
        "password": password,
        "firstName": "Perf",
        "lastName": f"User{i}",
    })
    if status not in (200, 201):
        raise SystemExit(f"register failed for {email}: HTTP {status}")

    token = await_token(email)
    status, _ = http("POST", f"{base}/api/v1/auth/verify-email", {"token": token})
    if status != 200:
        raise SystemExit(f"verify failed for {email}: HTTP {status}")

    status, login = http("POST", f"{base}/api/v1/auth/login",
                         {"email": email, "password": password})
    if status != 200 or not login.get("data", {}).get("accessToken"):
        raise SystemExit(f"login failed for {email}: HTTP {status}")
    access = login["data"]["accessToken"]

    user_codes, user_ids = [], []
    for j in range(1, urls_per + 1):
        alias = f"perf{stamp}u{i}a{j}"
        status, created = http(
            "POST",
            f"{base}/api/v1/urls",
            {"originalUrl": f"https://example.com/perf/{i}/{j}", "customAlias": alias},
            headers={"Authorization": f"Bearer {access}"},
        )
        if status not in (200, 201):
            raise SystemExit(f"create url failed: HTTP {status} {created}")
        data = created["data"]
        user_codes.append(data["shortCode"])
        user_ids.append(data["id"])
        short_codes.append(data["shortCode"])
        url_ids.append(data["id"])

    users.append({
        "email": email,
        "password": password,
        "shortCodes": user_codes,
        "urlIds": user_ids,
    })

payload = {
    "generatedAt": stamp,
    "baseUrl": base,
    "users": users,
    "shortCodes": short_codes,
    "urlIds": url_ids,
}
with open(out_file, "w", encoding="utf-8") as f:
    json.dump(payload, f, indent=2)
print(f"Wrote {len(users)} users, {len(short_codes)} shortCodes → {out_file}")
PY
