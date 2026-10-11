import sys, json, hashlib, urllib.request, binascii, os
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

uuid, frag, out = sys.argv[1], sys.argv[2], sys.argv[3]
key = bytes.fromhex(frag) if len(frag) == 64 and all(c in '0123456789abcdef' for c in frag) else frag.encode()
assert len(key) == 32, len(key)
GW = "https://gateway.filen.io"

def post(endpoint, data):
    body = json.dumps(data, separators=(',', ':')).encode()
    req = urllib.request.Request(GW + endpoint, data=body, method="POST", headers={
        "Content-Type": "application/json",
        "Checksum": hashlib.sha512(body).hexdigest(),
        "User-Agent": "filen-sdk-ts"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)

pw = post("/v3/file/link/password", {"uuid": uuid})
print("password info:", pw)
hashed = hashlib.sha1(hashlib.sha512(b"empty").hexdigest().encode()).hexdigest()
info = post("/v3/file/link/info", {"uuid": uuid, "password": hashed})
print("info status:", info.get("status"), info.get("message"), info.get("code"))
d = info["data"]
print({k: d[k] for k in ("bucket", "region", "chunks", "version")})
assert d["version"] == 2, d["version"]
aes = AESGCM(key)
total = 0
h = hashlib.sha256()
with open(out, "wb") as f:
    for i in range(d["chunks"]):
        url = f"https://egest.filen.io/{d['region']}/{d['bucket']}/{d['uuid']}/{i}"
        for attempt in range(5):
            try:
                with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "filen-sdk-ts"}), timeout=120) as r:
                    enc = r.read()
                break
            except Exception as e:
                print("retry", i, e); 
                if attempt == 4: raise
        pt = aes.decrypt(enc[:12], enc[12:], None)
        f.write(pt); h.update(pt); total += len(pt)
        if i % 50 == 0 or i == d["chunks"] - 1:
            print(f"chunk {i+1}/{d['chunks']} bytes={total}", flush=True)
print("DONE", out, total, "sha256", h.hexdigest())
