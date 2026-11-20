"""Bakes the skinned Quaternius horse (CC0) into flat per-frame vertex positions.

Usage: python3 -I bake_horse.py Horse.gltf out.bin [--report]

The app never evaluates bones: it blends two neighbouring baked frames, then shades and
projects the triangles itself. Output layout (little endian):
  magic "HRS1", u16 vertices, u16 triangles, u8 colors, u8 clips
  colors x u32 ARGB, triangles x (3 x u16 index, u8 color)
  per clip: u8 name length, name, u16 frames, f32 seconds, f32 origin xyz, f32 step,
            frames x vertices x 3 x i16 (position = origin + (value + 32500) x step)
Model space is metres: +y up, +z toward the head, hooves standing on y = 0.
"""
import base64, json, math, struct, sys

CLIPS = [("Gallop", 18), ("Gallop_Jump", 44)]


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def trs(t, r, s):
    x, y, z, w = r
    rot = [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
           [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
           [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]]
    return [[rot[i][0] * s[0], rot[i][1] * s[1], rot[i][2] * s[2], t[i]] for i in range(3)] + [[0, 0, 0, 1]]


def nlerp(a, b, k):
    if sum(p * q for p, q in zip(a, b)) < 0:
        b = [-q for q in b]
    out = [p + (q - p) * k for p, q in zip(a, b)]
    n = math.sqrt(sum(v * v for v in out)) or 1
    return [v / n for v in out]


def srgb(linear):
    c = linear * 12.92 if linear <= .0031308 else 1.055 * linear ** (1 / 2.4) - .055
    return max(0, min(255, round(c * 255)))


class Gltf:
    def __init__(self, path):
        self.doc = json.load(open(path))
        self.buffers = [base64.b64decode(b["uri"].split(",", 1)[1]) for b in self.doc["buffers"]]

    def read(self, index):
        acc = self.doc["accessors"][index]
        view = self.doc["bufferViews"][acc["bufferView"]]
        fmt, size = {5126: ("f", 4), 5123: ("H", 2), 5121: ("B", 1), 5125: ("I", 4)}[acc["componentType"]]
        width = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}[acc["type"]]
        stride = view.get("byteStride", size * width)
        start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
        data = self.buffers[view["buffer"]]
        rows = [list(struct.unpack_from("<%d%s" % (width, fmt), data, start + i * stride)) for i in range(acc["count"])]
        if acc.get("normalized"):
            scale = float((1 << (8 * size)) - 1)
            rows = [[v / scale for v in row] for row in rows]
        return rows


def main(source, target, report):
    g = Gltf(source)
    doc = g.doc
    nodes = doc["nodes"]
    parent = {c: i for i, n in enumerate(nodes) for c in n.get("children", [])}
    skin = doc["skins"][0]
    joints = skin["joints"]
    inverse_bind = [[[m[c * 4 + r] for c in range(4)] for r in range(4)] for m in g.read(skin["inverseBindMatrices"])]

    colors, positions, influences, triangles = [], [], [], []
    keys = {}
    for material in doc["materials"]:
        r, gr, b, _ = material["pbrMetallicRoughness"]["baseColorFactor"]
        colors.append(0xff000000 | srgb(r) << 16 | srgb(gr) << 8 | srgb(b))
    for primitive in doc["meshes"][0]["primitives"]:
        attrs = primitive["attributes"]
        pos, jo, we = g.read(attrs["POSITION"]), g.read(attrs["JOINTS_0"]), g.read(attrs["WEIGHTS_0"])
        remap = []
        for p, j, w in zip(pos, jo, we):
            key = tuple(round(v, 5) for v in p) + tuple(j) + tuple(round(v, 4) for v in w)
            if key not in keys:
                keys[key] = len(positions)
                positions.append(p)
                influences.append([(jj, ww) for jj, ww in zip(j, w) if ww > 1e-5])
            remap.append(keys[key])
        index = [row[0] for row in g.read(primitive["indices"])]
        for i in range(0, len(index), 3):
            a, b, c = (remap[v] for v in index[i:i + 3])
            if len({a, b, c}) == 3:
                triangles.append((a, b, c, primitive["material"]))

    def pose(clip, time):
        local = {}
        for i, n in enumerate(nodes):
            local[i] = [n.get("translation", [0, 0, 0]), n.get("rotation", [0, 0, 0, 1]), n.get("scale", [1, 1, 1])]
        for channel in clip["channels"]:
            sampler = clip["samplers"][channel["sampler"]]
            times = [t[0] for t in g.read(sampler["input"])]
            values = g.read(sampler["output"])
            k = 0
            while k + 1 < len(times) and times[k + 1] <= time:
                k += 1
            if k + 1 < len(times):
                f = (time - times[k]) / (times[k + 1] - times[k])
                a, b = values[k], values[k + 1]
            else:
                f, a, b = 0, values[k], values[k]
            slot = {"translation": 0, "rotation": 1, "scale": 2}[channel["target"]["path"]]
            local[channel["target"]["node"]][slot] = nlerp(a, b, f) if slot == 1 else [p + (q - p) * f for p, q in zip(a, b)]
        world = {}

        def resolve(i):
            if i not in world:
                m = trs(*local[i])
                world[i] = mat_mul(resolve(parent[i]), m) if i in parent else m
            return world[i]

        skinning = [mat_mul(resolve(j), inverse_bind[n]) for n, j in enumerate(joints)]
        out = []
        for p, inf in zip(positions, influences):
            v = [0.0, 0.0, 0.0]
            for j, w in inf:
                m = skinning[j]
                for r in range(3):
                    v[r] += w * (m[r][0] * p[0] + m[r][1] * p[1] + m[r][2] * p[2] + m[r][3])
            out.append(v)
        return out

    animations = {a["name"]: a for a in doc["animations"]}
    baked = []
    for name, count in CLIPS:
        clip = animations[name]
        seconds = max(t[0] for s in clip["samplers"] for t in g.read(s["input"]))
        frames = [pose(clip, seconds * i / count) for i in range(count)]
        baked.append((name, seconds, frames))

    # Ground the hooves, centre the body over the origin and turn the head toward +z.
    first = baked[0][2]
    lo = [min(v[a] for f in first for v in f) for a in range(3)]
    hi = [max(v[a] for f in first for v in f) for a in range(3)]
    long_axis = 0 if hi[0] - lo[0] > hi[2] - lo[2] else 2
    head = max(range(len(positions)), key=lambda i: first[0][i][1])
    sign = 1 if first[0][head][long_axis] > (lo[long_axis] + hi[long_axis]) / 2 else -1
    cx, cz = (lo[0] + hi[0]) / 2, (lo[2] + hi[2]) / 2

    def orient(v):
        x, y, z = v[0] - cx, v[1] - lo[1], v[2] - cz
        return [-z * sign, y, x * sign] if long_axis == 0 else [x * sign, y, z * sign]

    baked = [(n, s, [[orient(v) for v in f] for f in frames]) for n, s, frames in baked]

    if report:
        print("vertices", len(positions), "triangles", len(triangles), "colors", ["%08x" % c for c in colors])
        for name, seconds, frames in baked:
            print(name, "%.3fs" % seconds)
            for i, f in enumerate(frames):
                ys = [v[1] for v in f]
                zs = [v[2] for v in f]
                print("  %2d t=%.2f ground=%.3f top=%.3f z=%.3f..%.3f" % (i, seconds * i / len(frames), min(ys), max(ys), min(zs), max(zs)))

    out = bytearray(b"HRS1")
    out += struct.pack("<HHBB", len(positions), len(triangles), len(colors), len(baked))
    for c in colors:
        out += struct.pack("<I", c)
    for a, b, c, m in triangles:
        out += struct.pack("<HHHB", a, b, c, m)
    for name, seconds, frames in baked:
        flat = [v for f in frames for v in f]
        lo3 = [min(v[a] for v in flat) for a in range(3)]
        step = max(max(v[a] for v in flat) - lo3[a] for a in range(3)) / 65000
        out += struct.pack("<B", len(name)) + name.encode()
        out += struct.pack("<Hf3ff", len(frames), seconds, *lo3, step)
        for f in frames:
            for v in f:
                out += struct.pack("<3h", *(round((v[a] - lo3[a]) / step) - 32500 for a in range(3)))
    open(target, "wb").write(out)
    print("wrote", target, len(out), "bytes")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], "--report" in sys.argv)
