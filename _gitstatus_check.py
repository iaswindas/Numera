import struct, os, hashlib, zlib, fnmatch

REPO = r'F:\Context'
GIT_DIR = os.path.join(REPO, '.git')

def read_file(path):
    with open(path, 'rb') as f:
        return f.read()

def sha1_of_blob(data):
    header = ('blob ' + str(len(data)) + '\x00').encode()
    return hashlib.sha1(header + data).hexdigest()

# ── HEAD / branch ─────────────────────────────────────────────────────────────
head_raw = read_file(os.path.join(GIT_DIR, 'HEAD')).decode().strip()
if head_raw.startswith('ref: '):
    ref = head_raw[5:]
    branch = ref.split('/')[-1]
    ref_file = os.path.join(GIT_DIR, *ref.split('/'))
    try:
        head_sha = read_file(ref_file).decode().strip()
    except FileNotFoundError:
        head_sha = None
else:
    branch = None
    head_sha = head_raw

# ── upstream from .git/config ─────────────────────────────────────────────────
def find_upstream(branch):
    cfg_path = os.path.join(GIT_DIR, 'config')
    try:
        cfg = read_file(cfg_path).decode(errors='replace')
    except Exception:
        return None
    in_section = False
    remote = merge = None
    for line in cfg.splitlines():
        line = line.strip()
        if line == '[branch "' + branch + '"]':
            in_section = True
            continue
        if in_section:
            if line.startswith('['):
                break
            if line.startswith('remote'):
                remote = line.split('=', 1)[1].strip()
            if line.startswith('merge'):
                merge = line.split('=', 1)[1].strip()
    if remote and merge:
        ub = merge.split('/')[-1]
        return remote + '/' + ub
    return None

upstream = find_upstream(branch) if branch else None

def resolve_ref(ref_path):
    full = os.path.join(GIT_DIR, *ref_path.split('/'))
    if os.path.exists(full):
        val = read_file(full).decode().strip()
        if val.startswith('ref: '):
            return resolve_ref(val[5:])
        return val
    packed = os.path.join(GIT_DIR, 'packed-refs')
    if os.path.exists(packed):
        for line in read_file(packed).decode().splitlines():
            if line.startswith('#') or line.startswith('^'):
                continue
            parts = line.split()
            if len(parts) == 2 and parts[1] == ref_path:
                return parts[0]
    return None

upstream_sha = None
if upstream:
    upstream_sha = resolve_ref('refs/remotes/' + upstream)

# ── ahead / behind (limited object walk) ─────────────────────────────────────
def commits_reachable(start, limit=400):
    visited, queue = set(), [start]
    while queue and len(visited) < limit:
        s = queue.pop(0)
        if s in visited:
            continue
        visited.add(s)
        obj_path = os.path.join(GIT_DIR, 'objects', s[:2], s[2:])
        if not os.path.exists(obj_path):
            continue
        try:
            body = zlib.decompress(read_file(obj_path))
            nul = body.index(b'\x00')
            text = body[nul+1:].decode(errors='replace')
            for ln in text.splitlines():
                if ln.startswith('parent '):
                    queue.append(ln.split()[1])
        except Exception:
            pass
    return visited

# ── branch header line ────────────────────────────────────────────────────────
if branch:
    header = '## ' + branch
    if upstream:
        header += '...' + upstream
        if head_sha and upstream_sha and head_sha != upstream_sha:
            try:
                hs = commits_reachable(head_sha)
                rs = commits_reachable(upstream_sha)
                ahead  = len(hs - rs)
                behind = len(rs - hs)
                ab = []
                if ahead:  ab.append('ahead '  + str(ahead))
                if behind: ab.append('behind ' + str(behind))
                if ab:
                    header += ' [' + ', '.join(ab) + ']'
            except Exception:
                pass
else:
    header = '## HEAD (detached) ' + (head_sha[:7] if head_sha else '?')

print(header)

# ── parse .git/index ──────────────────────────────────────────────────────────
idx_path = os.path.join(GIT_DIR, 'index')
data = read_file(idx_path)
pos = 0

sig = data[pos:pos+4]; pos += 4
assert sig == b'DIRC', 'Bad index signature: ' + repr(sig)
version = struct.unpack('>I', data[pos:pos+4])[0]; pos += 4
count   = struct.unpack('>I', data[pos:pos+4])[0]; pos += 4

index_map = {}
for _ in range(count):
    entry_start = pos
    pos += 24  # ctime(8) mtime(8) dev(4) ino(4)
    _mode = struct.unpack('>I', data[pos:pos+4])[0]; pos += 4
    pos += 8   # uid(4) gid(4)
    _fsize = struct.unpack('>I', data[pos:pos+4])[0]; pos += 4
    sha_hex = data[pos:pos+20].hex(); pos += 20
    flags = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
    stage = (flags >> 12) & 0x3
    if version >= 3 and (flags & 0x4000):
        pos += 2  # extended flags
    nul = data.index(b'\x00', pos)
    path = data[pos:nul].decode('utf-8', errors='replace')
    pos = nul + 1
    if version < 4:
        entry_len = pos - entry_start
        rem = entry_len % 8
        if rem:
            pos += 8 - rem
    if stage == 0:
        index_map[path] = sha_hex

index_paths = set(index_map.keys())

# ── .gitignore (root only, simplified) ───────────────────────────────────────
def load_gitignore(root):
    pats = []
    gi = os.path.join(root, '.gitignore')
    if os.path.exists(gi):
        for line in read_file(gi).decode(errors='replace').splitlines():
            line = line.rstrip()
            if line and not line.startswith('#'):
                pats.append(line)
    return pats

pats = load_gitignore(REPO)

def is_ignored(rel, pats):
    name = os.path.basename(rel)
    rel_fwd = rel.replace('\\', '/')
    for pat in pats:
        if pat.endswith('/'):
            for part in rel_fwd.split('/'):
                if fnmatch.fnmatch(part, pat.rstrip('/')):
                    return True
        else:
            if fnmatch.fnmatch(name, pat):
                return True
            if fnmatch.fnmatch(rel_fwd, pat):
                return True
    return False

# ── walk working tree ─────────────────────────────────────────────────────────
wt_files = set()
for dirpath, dirnames, filenames in os.walk(REPO):
    dirnames[:] = [
        d for d in dirnames
        if d != '.git' and not is_ignored(
            os.path.relpath(os.path.join(dirpath, d), REPO).replace('\\', '/') + '/',
            pats)
    ]
    for fname in filenames:
        abs_path = os.path.join(dirpath, fname)
        rel = os.path.relpath(abs_path, REPO).replace('\\', '/')
        if not is_ignored(rel, pats) and fname != '_gitstatus_check.py':
            wt_files.add(rel)

# ── classify files ────────────────────────────────────────────────────────────
modified  = []
deleted   = []
untracked = []

for path in sorted(index_paths):
    if path not in wt_files:
        deleted.append(path)
    else:
        abs_path = os.path.join(REPO, path.replace('/', os.sep))
        try:
            content = read_file(abs_path)
            wt_sha = sha1_of_blob(content)
            if wt_sha != index_map[path]:
                modified.append(path)
        except Exception as ex:
            modified.append(path + '  [read error: ' + str(ex) + ']')

for path in sorted(wt_files - index_paths):
    untracked.append(path)

# ── output ────────────────────────────────────────────────────────────────────
for p in modified:
    print(' M ' + p)
for p in deleted:
    print(' D ' + p)
for p in untracked:
    print('?? ' + p)

if not (modified or deleted or untracked):
    print('(nothing to commit, working tree clean)')

print()
print('--- debug ---')
print('index version :', version)
print('index entries :', len(index_map))
print('wt files      :', len(wt_files))
