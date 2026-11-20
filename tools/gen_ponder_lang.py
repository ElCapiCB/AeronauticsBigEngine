#!/usr/bin/env python3
"""Adds Ponder text to the lang files. English is read straight from EngineScenes.java (in call order, which is how
Ponder numbers text_N); Spanish translations live below. Run after gen_lang.py:  python3 tools/gen_ponder_lang.py"""
import json, re, sys

MOD = "aeronauticsbigengine"
JAVA = "src/main/java/dev/elcapi/bigengine/client/ponder/EngineScenes.java"
LANG = f"src/main/resources/assets/{MOD}/lang/"

ES = {
"engine_overview": ("Construyendo un Motor Grande", [
    "La línea del cigüeñal se arma en un solo eje: codos, tramos de cigüeñal, bancadas y un volante de inercia",
    "Una única Salida de Potencia va en un extremo de la línea. Por aquí el motor alimenta la red de Create",
    "Cada codo mueve un cilindro: una biela con un pistón encima",
    "Sobre cada pistón: un hueco vacío rodeado de paredes de cilindro, cerrado por una culata con sus puertos",
    "Por último, coloca la Unidad de Control del Motor tocando cualquier bloque de la línea del cigüeñal",
    "Haz clic derecho para abrir la pantalla de ajustes y pulsa Arrancar",
    "El motor de arranque lo hace girar, los cilindros explotan y la potencia sale por la Salida de Potencia"]),
"engine_cylinder": ("Anatomía de un Cilindro", [
    "Coloca una Biela sobre un codo, apuntando hacia fuera del cigüeñal",
    "Luego un Pistón de Motor encima de la biela, mirando hacia el mismo lado",
    "El bloque sobre el pistón es el hueco del cilindro. Debe quedar vacío: el pistón entra en él",
    "Rodea el hueco con Paredes de Cilindro por todos los lados",
    "Y ciérralo con una Culata. Sus caras libres llevan el inyector, la bujía, la admisión y el escape",
    "En marcha, el pistón recorre un bloque: desde su bloque hasta el hueco y vuelta"]),
"engine_big_bore": ("Pistones Grandes, Más Torque", [
    "Varios pistones juntos, hasta 3x3, forman un solo pistón grande movido por una única biela",
    "El hueco y la culata crecen con él. Las paredes deben rodear todo el hueco",
    "El torque depende de la masa en movimiento: cada kg de pistón y biela (masa de Sable) suma capacidad. Pistones más pesados, más SU",
    "Los pistones grandes necesitan más aire: añade admisiones extra, hasta una por bloque de pistón, para rendir al máximo",
    "El volante añade inercia: el motor sube de vueltas más lento pero aguanta mejor los golpes de carga"]),
"engine_ports": ("Puertos de la Culata", [
    "Inyector de Combustible (obligatorio): guarda 1000 mB de combustible. Llénalo con tubos de Create o con un cubo",
    "Bujía: sin ella el cilindro nunca enciende",
    "Admisión de Aire (obligatoria): necesita aire libre delante para respirar",
    "Escape: opcional, pero sin él el cilindro rinde solo un tercio de su potencia",
    "Humo negro y espeso significa mezcla rica: demasiado combustible para el aire"]),
"engine_oil": ("Lubricando los Cilindros", [
    "Usa la Lata de Aceite sobre las caras interiores de las paredes, dentro del hueco del cilindro",
    "El aceite se pega a las paredes como una liana. Usa la lata otra vez para apilar hasta 3 capas",
    "El pistón se desliza sobre esta película. Cada explosión la gasta un poco, así que repónla de vez en cuando",
    "Sin aceite la fricción se multiplica hasta por cuatro: el motor pierde potencia, se recalienta y acaba gripado",
    "Atajo: usa la lata sobre una Culata para aceitar de una vez todas las paredes del cilindro que hay debajo",
    "Las Gafas de Ingeniero sobre la ECU muestran qué tan bien lubricado está el motor"]),
"engine_controller": ("La Unidad de Control del Motor", [
    "La Unidad de Control (ECU) maneja el motor. Haz clic derecho para abrir su pantalla",
    "El Acelerador fija la velocidad objetivo entre el Ralentí y las RPM máx. Un gobernador añade o quita combustible para mantenerla",
    "Una señal de redstone analógica también puede accionar el acelerador",
    "Combustible limita la carga por ciclo. La mezcla Aire/Combustible (unos 13:1 para máxima potencia) y el Avance ajustan la eficiencia",
    "Demasiado avance hace que el motor detone: se calienta rápido. Redúcelo cuando la pantalla te avise",
    "Ensamblar (Sable) convierte el cigüeñal y cada pistón en sub-niveles de Sable que se mueven de verdad, guiados por articulaciones con servo",
    "En una nave de Aeronautics, las piezas en movimiento empujan a la propia nave. Desensamblar devuelve cada bloque a su sitio",
    "Las Gafas de Ingeniero muestran RPM, capacidad de stress, temperatura, aceite y combustible"]),
"crank_timing": ("Codos y Orden de Encendido", [
    "Cada codo tiene un ángulo de muñequilla: 0, 90, 180 o 270 grados",
    "Haz clic derecho en un codo con la Llave para girarlo 90 grados",
    "El ángulo decide cuándo llega cada pistón arriba. Ángulos opuestos (0 y 180) hacen que los pistones se muevan en sentidos contrarios",
    "En un motor de cuatro tiempos cada cilindro explota una vez cada dos vueltas. En la pantalla elige Vuelta 1 o Vuelta 2 para cada cilindro",
    "Auto-sincronizar reparte las explosiones uniformemente en los 720 grados del ciclo: motor suave con balance alto",
    "Un encendido desparejo hace vibrar el motor, y en una nave esa vibración pasa al casco"]),
}
TAG = {"en": ("Big Engines", "Block-built piston engines for Create Aeronautics"),
       "es": ("Motores Grandes", "Motores de pistones construidos bloque a bloque para Create Aeronautics")}

src = open(JAVA, encoding="utf-8").read()
scenes = {}
for m in re.finditer(r'scene\.title\("(\w+)", "([^"]+)"\)(.*?)(?=scene\.title\(|\Z)', src, re.S):
    scenes[m.group(1)] = (m.group(2), re.findall(r'\.text\("([^"]+)"\)', m.group(3)))

ok = True
for sid, (title, texts) in scenes.items():
    if sid not in ES or len(ES[sid][1]) != len(texts):
        print(f"Spanish text out of sync for scene {sid}: java has {len(texts)}", file=sys.stderr); ok = False
if not ok: sys.exit(1)

for lang in ("en_us", "es_es"):
    path = LANG + lang + ".json"
    data = json.load(open(path, encoding="utf-8"))
    data = {k: v for k, v in data.items() if ".ponder." not in k}
    for sid, (title, texts) in scenes.items():
        if lang == "es_es": title, texts = ES[sid]
        data[f"{MOD}.ponder.{sid}.header"] = title
        for i, t in enumerate(texts, 1): data[f"{MOD}.ponder.{sid}.text_{i}"] = t
    tname, tdesc = TAG["es" if lang == "es_es" else "en"]
    data[f"{MOD}.ponder.tag.big_engines"] = tname
    data[f"{MOD}.ponder.tag.big_engines.description"] = tdesc
    json.dump(data, open(path, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
print("ponder lang:", sum(len(t) for _, t in scenes.values()), "texts in", len(scenes), "scenes")
