#!/usr/bin/env python3
"""Writes the Ponder schematics (.nbt structure files). Run from the repo root: python3 tools/gen_ponder.py"""
import gzip, io, os, struct

MOD = "aeronauticsbigengine"
OUT = f"src/main/resources/assets/{MOD}/ponder"
DATA_VERSION = 3955  # 1.21.1

# ---------------------------------------------------------------- minimal NBT writer
class Int(int): pass
class IntList(list): pass

def _name(buf, s):
    b = s.encode("utf-8"); buf.write(struct.pack(">H", len(b))); buf.write(b)

def _payload(buf, v):
    if isinstance(v, dict):
        for k, x in v.items():
            buf.write(bytes([_tag(x)])); _name(buf, k); _payload(buf, x)
        buf.write(b"\x00")
    elif isinstance(v, str):
        _name(buf, v)
    elif isinstance(v, int):
        buf.write(struct.pack(">i", v))
    elif isinstance(v, list):
        t = 3 if isinstance(v, IntList) else (_tag(v[0]) if v else 0)
        buf.write(bytes([t])); buf.write(struct.pack(">i", len(v)))
        for x in v: _payload(buf, x)

def _tag(v):
    if isinstance(v, dict): return 10
    if isinstance(v, str): return 8
    if isinstance(v, int): return 3
    if isinstance(v, list): return 9
    raise TypeError(v)

def write_nbt(path, root):
    buf = io.BytesIO(); buf.write(b"\x0a"); _name(buf, ""); _payload(buf, root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, "wb") as f: f.write(buf.getvalue())

# ---------------------------------------------------------------- structure builder
class Structure:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz); self.blocks = {}
        for x in range(sx):
            for z in range(sz):
                self.set(x, 0, z, "minecraft:white_concrete" if (x + z) % 2 == 0 else "minecraft:light_gray_concrete")

    def set(self, x, y, z, name, **props):
        if ":" not in name: name = f"{MOD}:{name}"
        self.blocks[(x, y, z)] = (name, tuple(sorted((k, str(v)) for k, v in props.items())))

    def save(self, name):
        palette, index, blocks = [], {}, []
        for pos, state in sorted(self.blocks.items(), key=lambda e: (e[0][1], e[0][2], e[0][0])):
            if state not in index:
                index[state] = len(palette)
                entry = {"Name": state[0]}
                if state[1]: entry["Properties"] = {k: v for k, v in state[1]}
                palette.append(entry)
            blocks.append({"pos": IntList(list(pos)), "state": index[state]})
        write_nbt(f"{OUT}/{name}.nbt", {"DataVersion": DATA_VERSION, "size": IntList(list(self.size)),
                                        "palette": palette, "blocks": blocks, "entities": []})

# ---------------------------------------------------------------- 2 cylinder inline engine (7x7x7)
s = Structure(7, 7, 7)
s.set(0, 1, 3, "create:shaft", axis="x")
s.set(1, 1, 3, "power_output", axis="x")
s.set(2, 1, 3, "main_bearing", axis="x")
s.set(3, 1, 3, "crank_throw", axis="x", phase=2)   # pin up: piston A at TDC
s.set(4, 1, 3, "main_bearing", axis="x")
s.set(5, 1, 3, "crank_throw", axis="x", phase=0)   # pin down: piston B at BDC
s.set(6, 1, 3, "engine_flywheel", axis="x")
s.set(2, 1, 4, "engine_controller", facing="south")
for x in (3, 5):
    s.set(x, 2, 3, "connecting_rod", facing="up")
    s.set(x, 3, 3, "engine_piston", facing="up")
    for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        s.set(x + dx, 4, 3 + dz, "cylinder_wall")
    s.set(x, 5, 3, "cylinder_head", facing="up")
    s.set(x, 6, 3, "fuel_injector", facing="up")
    s.set(x, 5, 2, "air_intake", facing="north")
    s.set(x, 5, 4, "exhaust_manifold", facing="south")
s.set(2, 5, 3, "spark_plug", facing="west")
s.set(6, 5, 3, "spark_plug", facing="east")
s.save("engine_i2")

# ---------------------------------------------------------------- one big 3x3 bore cylinder (5x7x5)
b = Structure(5, 7, 5)
b.set(0, 1, 2, "power_output", axis="x")
b.set(1, 1, 2, "main_bearing", axis="x")
b.set(2, 1, 2, "crank_throw", axis="x", phase=0)
b.set(3, 1, 2, "main_bearing", axis="x")
b.set(4, 1, 2, "engine_flywheel", axis="x")
b.set(2, 2, 2, "connecting_rod", facing="up")
for x in range(1, 4):
    for z in range(1, 4):
        b.set(x, 3, z, "engine_piston", facing="up")
        b.set(x, 5, z, "cylinder_head", facing="up")
for x in range(5):
    for z in range(5):
        if x in (0, 4) or z in (0, 4):
            b.set(x, 4, z, "cylinder_wall")
b.set(2, 6, 2, "fuel_injector", facing="up")
b.set(1, 6, 1, "spark_plug", facing="up")
b.set(1, 6, 3, "air_intake", facing="up")
b.set(3, 6, 1, "air_intake", facing="up")
b.set(3, 6, 3, "exhaust_manifold", facing="up")
b.save("engine_big_bore")
print("schematics written")
