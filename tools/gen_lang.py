import json
M="aeronauticsbigengine"
def build(t):
    out={}
    for k,v in t.items(): out[k.replace("$",M)]=v
    return out
es={
"itemGroup.$":"Motores Grandes (Aeronautics)",
"block.$.crankshaft":"Cigüeñal","block.$.crank_throw":"Codo de Cigüeñal","block.$.engine_flywheel":"Volante de Inercia",
"block.$.main_bearing":"Bancada (Cojinete Principal)","block.$.power_output":"Salida de Potencia",
"block.$.connecting_rod":"Biela","block.$.engine_piston":"Pistón de Motor","block.$.cylinder_wall":"Pared de Cilindro",
"block.$.cylinder_head":"Culata","block.$.fuel_injector":"Inyector de Combustible","block.$.spark_plug":"Bujía",
"block.$.air_intake":"Admisión de Aire","block.$.exhaust_manifold":"Escape","block.$.engine_controller":"Unidad de Control del Motor (ECU)",
"block.$.oil_film":"Película de Aceite","item.$.engine_oil":"Lata de Aceite de Motor",
"message.$.crank_phase":"Ángulo del codo: %s°","message.$.too_hot":"El motor está demasiado caliente para arrancar",
"status.$.no_engine":"Sin motor","status.$.stopped":"Detenido","status.$.cranking":"Arrancando","status.$.running":"En marcha",
"status.$.stalled":"Calado","status.$.overheated":"Sobrecalentado / gripado","status.$.no_fuel":"Sin combustible",
"status.$.err.no_crank":"La ECU debe tocar el cigüeñal","status.$.err.no_output":"Falta la Salida de Potencia en el cigüeñal",
"status.$.err.many_outputs":"Solo puede haber una Salida de Potencia","status.$.err.output_not_at_end":"La Salida de Potencia debe ir en un extremo del cigüeñal",
"status.$.err.no_throws":"El cigüeñal necesita al menos un codo","status.$.err.two_rods":"Cilindro %s: dos bielas en el mismo codo",
"status.$.err.no_rod":"Cilindro %s: falta la biela sobre el codo","status.$.err.no_piston":"Cilindro %s: falta el pistón sobre la biela",
"status.$.err.bore_too_big":"Cilindro %s: el pistón es mayor que 3x3","status.$.err.bore_blocked":"Cilindro %s: el interior del cilindro debe estar vacío (o con aceite)",
"status.$.err.no_walls":"Cilindro %s: faltan paredes de cilindro alrededor","status.$.err.no_head":"Cilindro %s: falta la culata",
"status.$.err.no_injector":"Cilindro %s: falta un inyector en la culata","status.$.err.no_intake":"Cilindro %s: falta una admisión de aire en la culata",
"status.$.err.no_sable":"Sable no está activo en esta dimensión","status.$.err.assembly_failed":"Falló el ensamblado físico (ver log)",
"goggles.$.title":"Motor:","goggles.$.rpm":"Régimen: %s RPM","goggles.$.capacity":"Capacidad: %s su","goggles.$.temperature":"Temperatura: %s °C",
"goggles.$.oil":"Lubricación: %s%%","goggles.$.fuel":"Combustible: %s mB",
"gui.$.title":"Unidad de Control del Motor","gui.$.throttle":"Acelerador","gui.$.idle":"Ralentí","gui.$.max_rpm":"RPM máx.",
"gui.$.fuel_limit":"Combustible","gui.$.afr":"Mezcla A/C","gui.$.ignition":"Avance",
"gui.$.redstone":"Acelerador redstone: %s","gui.$.direction":"Giro: %s","gui.$.forward":"normal","gui.$.reverse":"inverso",
"gui.$.on":"Sí","gui.$.off":"No","gui.$.start":"Arrancar","gui.$.stop":"Parar","gui.$.assemble":"Ensamblar (Sable)",
"gui.$.disassemble":"Desensamblar","gui.$.auto_time":"Auto-sincronizar","gui.$.turn":"Vuelta %s",
"gui.$.cylinders":"Cilindros: %s  (%s/%s)","gui.$.cylinder":"#%s  %s°  aceite %s%%",
"gui.$.readout1":"%s RPM   %s su   carga %s%%","gui.$.readout2":"%s °C  aceite %s%%  comb. %s mB  balance %s%%",
"gui.$.knock":"¡Detonación! Reduce el avance",
}
en={
"itemGroup.$":"Big Engines (Aeronautics)",
"block.$.crankshaft":"Crankshaft","block.$.crank_throw":"Crank Throw","block.$.engine_flywheel":"Engine Flywheel",
"block.$.main_bearing":"Main Bearing","block.$.power_output":"Power Output",
"block.$.connecting_rod":"Connecting Rod","block.$.engine_piston":"Engine Piston","block.$.cylinder_wall":"Cylinder Wall",
"block.$.cylinder_head":"Cylinder Head","block.$.fuel_injector":"Fuel Injector","block.$.spark_plug":"Spark Plug",
"block.$.air_intake":"Air Intake","block.$.exhaust_manifold":"Exhaust","block.$.engine_controller":"Engine Control Unit",
"block.$.oil_film":"Oil Film","item.$.engine_oil":"Engine Oil Can",
"message.$.crank_phase":"Crank pin angle: %s°","message.$.too_hot":"The engine is too hot to start",
"status.$.no_engine":"No engine","status.$.stopped":"Stopped","status.$.cranking":"Cranking","status.$.running":"Running",
"status.$.stalled":"Stalled","status.$.overheated":"Overheated / seized","status.$.no_fuel":"Out of fuel",
"status.$.err.no_crank":"The ECU must touch the crank line","status.$.err.no_output":"The crank line needs a Power Output",
"status.$.err.many_outputs":"Only one Power Output allowed","status.$.err.output_not_at_end":"The Power Output must be at one end of the crank line",
"status.$.err.no_throws":"The crankshaft needs at least one crank throw","status.$.err.two_rods":"Cylinder %s: two rods on one throw",
"status.$.err.no_rod":"Cylinder %s: missing connecting rod on the throw","status.$.err.no_piston":"Cylinder %s: missing piston on the rod",
"status.$.err.bore_too_big":"Cylinder %s: piston larger than 3x3","status.$.err.bore_blocked":"Cylinder %s: the bore must be empty (or oiled)",
"status.$.err.no_walls":"Cylinder %s: missing cylinder walls around the bore","status.$.err.no_head":"Cylinder %s: missing cylinder head",
"status.$.err.no_injector":"Cylinder %s: needs a fuel injector on the head","status.$.err.no_intake":"Cylinder %s: needs an air intake on the head",
"status.$.err.no_sable":"Sable is not active in this dimension","status.$.err.assembly_failed":"Physical assembly failed (see log)",
"goggles.$.title":"Engine:","goggles.$.rpm":"Speed: %s RPM","goggles.$.capacity":"Capacity: %s su","goggles.$.temperature":"Temperature: %s °C",
"goggles.$.oil":"Lubrication: %s%%","goggles.$.fuel":"Fuel: %s mB",
"gui.$.title":"Engine Control Unit","gui.$.throttle":"Throttle","gui.$.idle":"Idle","gui.$.max_rpm":"Max RPM",
"gui.$.fuel_limit":"Fuel","gui.$.afr":"Air/Fuel","gui.$.ignition":"Advance",
"gui.$.redstone":"Redstone throttle: %s","gui.$.direction":"Rotation: %s","gui.$.forward":"forward","gui.$.reverse":"reverse",
"gui.$.on":"On","gui.$.off":"Off","gui.$.start":"Start","gui.$.stop":"Stop","gui.$.assemble":"Assemble (Sable)",
"gui.$.disassemble":"Disassemble","gui.$.auto_time":"Auto timing","gui.$.turn":"Turn %s",
"gui.$.cylinders":"Cylinders: %s  (%s/%s)","gui.$.cylinder":"#%s  %s°  oil %s%%",
"gui.$.readout1":"%s RPM   %s su   load %s%%","gui.$.readout2":"%s °C  oil %s%%  fuel %s mB  balance %s%%",
"gui.$.knock":"Knocking! Reduce ignition advance",
}
assert set(es)==set(en)
d="src/main/resources/assets/"+M+"/lang/"
json.dump(build(es),open(d+"es_es.json","w"),indent=2,ensure_ascii=False)
json.dump(build(en),open(d+"en_us.json","w"),indent=2,ensure_ascii=False)
