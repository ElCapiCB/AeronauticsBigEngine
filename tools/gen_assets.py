#!/usr/bin/env python3
"""Generates blockstates, models, loot tables, tags, recipes, Sable physics properties and the two
procedural textures. Run from the repo root:  python3 tools/gen_assets.py"""
import json, os, random, zlib, struct

MOD = "aeronauticsbigengine"
R = "src/main/resources"
A = f"{R}/assets/{MOD}"
D = f"{R}/data/{MOD}"

def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")

def face_all(tex, uv=None):
    return {d: ({"texture": tex, "uv": uv} if uv else {"texture": tex}) for d in ("north", "south", "east", "west", "up", "down")}

def box(frm, to, tex_side, tex_end=None, axis="y"):
    """Element with tex_end on the faces perpendicular to `axis`."""
    faces = {}
    ends = {"x": ("east", "west"), "y": ("up", "down"), "z": ("north", "south")}[axis]
    for d in ("north", "south", "east", "west", "up", "down"):
        faces[d] = {"texture": tex_end if (tex_end and d in ends) else tex_side}
    return {"from": frm, "to": to, "faces": faces}

def model(name, elements, textures, parent="block/block", extra=None):
    m = {"parent": parent, "textures": textures, "elements": elements}
    if extra: m.update(extra)
    w(f"{A}/models/block/{name}.json", m)

AXIS_ROT = {"x": {"x": 90, "y": 90}, "y": {}, "z": {"x": 90}}
FACING_ROT = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
              "west": {"x": 90, "y": 270}, "east": {"x": 90, "y": 90}}
HORIZ_ROT = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}

def axis_state(name, mdl=None):
    mdl = mdl or f"{MOD}:block/{name}"
    w(f"{A}/blockstates/{name}.json", {"variants": {f"axis={a}": {"model": mdl, **r} for a, r in AXIS_ROT.items()}})

def facing_state(name, mdl=None):
    mdl = mdl or f"{MOD}:block/{name}"
    w(f"{A}/blockstates/{name}.json", {"variants": {f"facing={f}": {"model": mdl, **r} for f, r in FACING_ROT.items()}})

def item_from_block(name, mdl=None):
    w(f"{A}/models/item/{name}.json", {"parent": mdl or f"{MOD}:block/{name}"})

AXIS = "create:block/axis"
AXIS_TOP = "create:block/axis_top"
IRON = "create:block/industrial_iron_block"
DARK = "create:block/dark_metal_block"
ANDESITE = "create:block/andesite_casing"
BRASS = "create:block/brass_casing"
COPPER = "create:block/copper_casing"
GEARBOX = "create:block/gearbox"
WEATHERED = "create:block/weathered_iron_block"

blocks = []

# ---------------------------------------------------------------- crankshaft (uses Create's shaft model)
axis_state("crankshaft", "create:block/shaft")
item_from_block("crankshaft", "create:block/shaft")
blocks.append("crankshaft")

# ---------------------------------------------------------------- crank throw: one model per axis & phase
VEC = {"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}
def cross(a, b):
    return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def pin_dir(axis, phase):
    # phase 0 pin points up (north for a vertical crank), each phase step = +90 deg around +axis (right hand)
    v = (0, 0, -1) if axis == "y" else (0, 1, 0)
    for _ in range(phase):
        v = cross(VEC[axis], v)
    return v

def axis_box(axis, lo, hi, a_lo, a_hi, centre=(8, 8, 8), offset=(0, 0, 0), half=None):
    """Box spanning [a_lo,a_hi] along axis and [lo,hi] across, shifted by offset (pixels)."""
    frm, to = [0, 0, 0], [0, 0, 0]
    ai = "xyz".index(axis)
    for i in range(3):
        if i == ai:
            frm[i], to[i] = a_lo, a_hi
        else:
            frm[i], to[i] = lo + offset[i], hi + offset[i]
    return frm, to

def clamp_box(frm, to):
    return [max(0, min(16, v)) for v in frm], [max(0, min(16, v)) for v in to]

throw_variants = {}
for axis in "xyz":
    for phase in range(4):
        p = pin_dir(axis, phase)
        off = tuple(c * 5 for c in p)          # pin 5 px off centre
        web_off = tuple(c * 2.5 for c in p)
        els = []
        f, t = axis_box(axis, 6, 10, 0, 3); els.append(box(f, t, AXIS, AXIS_TOP, axis))
        f, t = axis_box(axis, 6, 10, 13, 16); els.append(box(f, t, AXIS, AXIS_TOP, axis))
        for a_lo, a_hi in ((3, 5.5), (10.5, 13)):
            f, t = axis_box(axis, 3.5, 12.5, a_lo, a_hi, offset=web_off)
            f, t = clamp_box(f, t)
            els.append(box(f, t, DARK, DARK, axis))
        f, t = axis_box(axis, 6, 10, 5.5, 10.5, offset=off)
        f, t = clamp_box(f, t)
        els.append(box(f, t, IRON, IRON, axis))
        name = f"crank_throw_{axis}_{phase}"
        model(name, els, {"particle": DARK})
        throw_variants[f"axis={axis},phase={phase}"] = {"model": f"{MOD}:block/{name}"}
w(f"{A}/blockstates/crank_throw.json", {"variants": throw_variants})
item_from_block("crank_throw", f"{MOD}:block/crank_throw_x_0")
blocks.append("crank_throw")

# ---------------------------------------------------------------- flywheel / main bearing / power output
model("engine_flywheel", [
    box([6, 0, 6], [10, 16, 10], AXIS, AXIS_TOP),
    box([0, 4, 0], [16, 12, 16], IRON, DARK),
], {"particle": DARK})
axis_state("engine_flywheel"); item_from_block("engine_flywheel"); blocks.append("engine_flywheel")

model("main_bearing", [box([0, 0, 0], [16, 16, 16], ANDESITE, GEARBOX)], {"particle": ANDESITE})
axis_state("main_bearing"); item_from_block("main_bearing"); blocks.append("main_bearing")

model("power_output", [box([0, 0, 0], [16, 16, 16], BRASS, GEARBOX)], {"particle": BRASS})
axis_state("power_output"); item_from_block("power_output"); blocks.append("power_output")

# ---------------------------------------------------------------- reciprocating parts (built pointing UP)
model("connecting_rod", [
    box([5, 0, 5], [11, 3, 11], DARK),          # big end
    box([6.5, 3, 6.5], [9.5, 13, 9.5], IRON),   # beam
    box([5.5, 13, 5.5], [10.5, 16, 10.5], DARK) # small end
], {"particle": IRON})
facing_state("connecting_rod"); item_from_block("connecting_rod"); blocks.append("connecting_rod")

model("engine_piston", [
    box([7, 0, 7], [9, 4, 9], IRON),
    box([1, 4, 1], [15, 16, 15], IRON, "minecraft:block/piston_top"),
    box([0.9, 13, 0.9], [15.1, 14, 15.1], DARK),   # rings
    box([0.9, 9, 0.9], [15.1, 10, 15.1], DARK),
], {"particle": IRON})
facing_state("engine_piston"); item_from_block("engine_piston"); blocks.append("engine_piston")

# ---------------------------------------------------------------- static engine block
w(f"{A}/blockstates/cylinder_wall.json", {"variants": {"": {"model": f"{MOD}:block/cylinder_wall"}}})
w(f"{A}/models/block/cylinder_wall.json", {"parent": "minecraft:block/cube_column",
    "textures": {"side": IRON, "end": "create:block/industrial_iron_block_top"}})
item_from_block("cylinder_wall"); blocks.append("cylinder_wall")

model("cylinder_head", [
    box([0, 0, 0], [16, 12, 16], WEATHERED, DARK),
    box([2, 12, 2], [14, 16, 14], DARK, WEATHERED),
], {"particle": WEATHERED})
facing_state("cylinder_head"); item_from_block("cylinder_head"); blocks.append("cylinder_head")

# ---------------------------------------------------------------- ports (built pointing UP, base on the head)
model("fuel_injector", [
    box([3, 0, 3], [13, 6, 13], COPPER),
    box([5, 6, 5], [11, 12, 11], COPPER, "create:block/fluid_tank_top"),
    box([7, 12, 7], [9, 16, 9], IRON),
], {"particle": COPPER})
model("spark_plug", [
    box([5, 0, 5], [11, 3, 11], BRASS),
    box([6, 3, 6], [10, 8, 10], "minecraft:block/white_concrete"),
    box([7, 8, 7], [9, 10, 9], IRON),
], {"particle": BRASS})
model("air_intake", [
    box([2, 0, 2], [14, 4, 14], ANDESITE),
    box([1, 4, 1], [15, 12, 15], ANDESITE, "create:block/fan_casing"),
], {"particle": ANDESITE})
model("exhaust_manifold", [
    box([3, 0, 3], [13, 4, 13], COPPER),
    box([4, 4, 4], [12, 12, 12], COPPER, "minecraft:block/black_concrete"),
], {"particle": COPPER})
for n in ("fuel_injector", "spark_plug", "air_intake", "exhaust_manifold"):
    facing_state(n); item_from_block(n); blocks.append(n)

# ---------------------------------------------------------------- controller
w(f"{A}/models/block/engine_controller.json", {"parent": "minecraft:block/orientable",
    "textures": {"top": BRASS, "side": BRASS, "front": "minecraft:block/observer_front"}})
w(f"{A}/blockstates/engine_controller.json", {"variants": {f"facing={f}": {"model": f"{MOD}:block/engine_controller", **r} for f, r in HORIZ_ROT.items()}})
item_from_block("engine_controller"); blocks.append("engine_controller")

# ---------------------------------------------------------------- oil film (multiface like glow lichen)
w(f"{A}/models/block/oil_film.json", {"parent": "minecraft:block/glow_lichen",
    "textures": {"glow_lichen": f"{MOD}:block/oil_film", "particle": f"{MOD}:block/oil_film"},
    "render_type": "minecraft:translucent"})
for t in (1, 2):
    w(f"{A}/models/block/oil_film_{t}.json", {"parent": "minecraft:block/glow_lichen",
        "textures": {"glow_lichen": f"{MOD}:block/oil_film_{t}", "particle": f"{MOD}:block/oil_film"},
        "render_type": "minecraft:translucent"})
FACE_ROT = {"north": {}, "south": {"y": 180}, "east": {"y": 90}, "west": {"y": 270},
            "up": {"x": 270, "uvlock": True}, "down": {"x": 90, "uvlock": True}}
multipart = []
for face, rot in FACE_ROT.items():
    for t in (1, 2, 3):
        mdl = f"{MOD}:block/oil_film" if t == 3 else f"{MOD}:block/oil_film_{t}"
        multipart.append({"when": {face: "true", "thickness": str(t)}, "apply": {"model": mdl, **rot}})
w(f"{A}/blockstates/oil_film.json", {"multipart": multipart})

w(f"{A}/models/item/engine_oil.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/engine_oil"}})

# ---------------------------------------------------------------- procedural textures
def png(path, pixels, wdt, hgt):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b"".join(b"\x00" + bytes([c for px in pixels[y*wdt:(y+1)*wdt] for c in px]) for y in range(hgt))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", wdt, hgt, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

def oil_texture(seed, coverage):
    rnd = random.Random(seed)
    px = [(0, 0, 0, 0)] * 256
    # drips running down the wall from the top, like vines of oil
    for x in range(16):
        if rnd.random() > coverage: continue
        length = rnd.randint(4, 16)
        for y in range(length):
            shade = rnd.randint(-10, 10)
            base = (58 + shade, 38 + shade, 10 + max(0, shade), 200 if y < length - 1 else 150)
            if rnd.random() < 0.12: base = (150, 110, 40, 220)  # glint
            px[y*16 + x] = base
    # film along the top edge
    for x in range(16):
        px[x] = (70, 46, 14, 210)
    return px

png(f"{A}/textures/block/oil_film.png", oil_texture(1, 0.95), 16, 16)
png(f"{A}/textures/block/oil_film_2.png", oil_texture(2, 0.6), 16, 16)
png(f"{A}/textures/block/oil_film_1.png", oil_texture(3, 0.3), 16, 16)

def oil_can():
    px = [(0, 0, 0, 0)] * 256
    def put(x, y, c): px[y*16 + x] = c
    body, dark, light = (150, 30, 30, 255), (90, 18, 18, 255), (200, 70, 60, 255)
    for y in range(6, 15):
        for x in range(3, 12):
            c = body
            if x == 3 or y == 14: c = dark
            if x == 10: c = light
            put(x, y, c)
    for x in range(4, 11): put(x, 5, dark)                  # top
    for i in range(5): put(11 + i if 11 + i < 16 else 15, 5 - i, (170, 170, 170, 255))  # spout
    for y in range(8, 12):
        for x in range(5, 10): put(x, y, (220, 180, 60, 255))  # label
    put(7, 9, (60, 40, 10, 255)); put(7, 10, (60, 40, 10, 255))  # drop
    return px
png(f"{A}/textures/item/engine_oil.png", oil_can(), 16, 16)

# ---------------------------------------------------------------- loot tables
for b in blocks:
    w(f"{D}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{b}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

# ---------------------------------------------------------------- tags
w(f"{R}/data/minecraft/tags/block/mineable/pickaxe.json", {"values": [f"{MOD}:{b}" for b in blocks]})
w(f"{D}/tags/fluid/engine_fuel.json", {"values": ["minecraft:lava"] + [{"id": t, "required": False} for t in (
    "#c:fuels", "#c:fuels/diesel", "#c:fuels/gasoline", "#c:fuels/biodiesel", "#c:fuels/ethanol", "#c:fuels/kerosene",
    "#c:diesel", "#c:gasoline", "#c:biodiesel", "#c:ethanol", "#c:fuel", "#c:kerosene")]})
w(f"{D}/tags/fluid/weak_engine_fuel.json", {"values": ["minecraft:lava"]})

# ---------------------------------------------------------------- Sable physics properties (mass in kpg)
MASS = {"crankshaft": 2.0, "crank_throw": 4.0, "engine_flywheel": 16.0, "main_bearing": 4.0, "power_output": 4.0,
        "connecting_rod": 2.0, "engine_piston": 4.0, "cylinder_wall": 6.0, "cylinder_head": 5.0,
        "fuel_injector": 0.5, "spark_plug": 0.1, "air_intake": 1.0, "exhaust_manifold": 1.0,
        "engine_controller": 1.0, "oil_film": 0.02}
for b, m in MASS.items():
    w(f"{D}/physics_block_properties/{b}.json", {"selector": f"{MOD}:{b}", "properties": {"sable:mass": m}})

# ---------------------------------------------------------------- recipes
def shaped(name, pattern, key, count=1):
    w(f"{D}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
        "key": {k: {"item": v} if not v.startswith("#") else {"tag": v[1:]} for k, v in key.items()},
        "result": {"id": f"{MOD}:{name}", "count": count}})

shaped("crankshaft", ["S", "I", "S"], {"S": "create:shaft", "I": "create:iron_sheet"}, 2)
shaped("crank_throw", [" I ", "SBS", " I "], {"S": "create:shaft", "I": "create:iron_sheet", "B": "minecraft:iron_block"})
shaped("engine_flywheel", ["III", "ISI", "III"], {"I": "minecraft:iron_ingot", "S": "create:shaft"})
shaped("main_bearing", ["A", "S", "C"], {"A": "create:andesite_alloy", "S": "create:shaft", "C": "create:andesite_casing"})
shaped("power_output", ["B", "S", "C"], {"B": "create:brass_ingot", "S": "create:shaft", "C": "create:brass_casing"})
shaped("connecting_rod", ["I", "R", "I"], {"I": "create:iron_sheet", "R": "minecraft:iron_ingot"}, 2)
shaped("engine_piston", ["III", "IBI", " R "], {"I": "create:iron_sheet", "B": "minecraft:iron_block", "R": "minecraft:iron_ingot"})
shaped("cylinder_wall", ["II", "II"], {"I": "create:industrial_iron_block"}, 4)
shaped("cylinder_head", ["III", "C C", "III"], {"I": "create:iron_sheet", "C": "create:industrial_iron_block"}, 2)
shaped("fuel_injector", ["C", "P", "N"], {"C": "create:copper_sheet", "P": "create:fluid_pipe", "N": "minecraft:iron_nugget"})
shaped("spark_plug", ["N", "Q", "B"], {"N": "minecraft:iron_nugget", "Q": "minecraft:quartz", "B": "create:brass_nugget"}, 2)
shaped("air_intake", ["B", "P", "C"], {"B": "minecraft:iron_bars", "P": "create:propeller", "C": "create:andesite_casing"})
shaped("exhaust_manifold", ["C", "P", "C"], {"C": "create:copper_sheet", "P": "create:fluid_pipe"})
shaped("engine_controller", ["RER", "BPB", "RCR"], {"R": "minecraft:redstone", "E": "create:electron_tube",
    "B": "create:brass_sheet", "P": "create:precision_mechanism", "C": "create:brass_casing"})
w(f"{D}/recipe/engine_oil.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
    "ingredients": [{"item": "minecraft:glass_bottle"}, {"item": "minecraft:dried_kelp"}, {"item": "minecraft:dried_kelp"},
                    {"tag": "minecraft:coals"}, {"item": "minecraft:slime_ball"}],
    "result": {"id": f"{MOD}:engine_oil", "count": 1}})
print("generated", len(blocks), "blocks")
