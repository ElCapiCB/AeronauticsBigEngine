# Aeronautics Big Engines

Addon de **Create Aeronautics** (Minecraft 1.21.1, NeoForge) para construir **motores de pistones grandes, bloque a bloque**.
El cigüeñal y cada pistón se convierten en **sub-niveles de Sable** que se mueven de verdad, con física.
Esta versión trae motores en línea (**I**).

## Requisitos
- Minecraft 1.21.1 + NeoForge ≥ 21.1.228
- Create 6.0.10+
- Sable 2.0+ (incluido como dependencia de Create Aeronautics)
- Create Aeronautics 1.3+ (opcional, recomendado)

## Cómo se arma un motor en I

```
   [culata][culata][culata]        <- Culata + puertos: inyector, bujía, admisión, escape
 [P]( vacío )[P]( vacío )[P]       <- Cilindro: hueco vacío rodeado de Paredes de Cilindro (P)
     [pistón]   [pistón]           <- Pistón de Motor (1x1 hasta 3x3 por cilindro)
     [ biela]   [ biela]           <- Biela
[SAL][codo][banc][codo][volante]   <- Línea del cigüeñal (todo en el mismo eje)
  [ECU]                            <- Unidad de Control tocando la línea del cigüeñal
```

1. **Línea del cigüeñal** (en un eje horizontal): `Codo de Cigüeñal` por cada cilindro, `Cigüeñal` para unir,
   `Bancada` (soporte fijo), `Volante de Inercia` (opcional, suaviza) y una `Salida de Potencia` en **un extremo**.
2. Sobre cada codo: `Biela` → `Pistón de Motor` (colócalos haciendo clic en la cara superior, así apuntan hacia arriba).
   Varios pistones juntos (hasta 3x3) forman un pistón más grande y más pesado.
3. Encima del pistón deja el **hueco del cilindro vacío** y rodéalo con `Pared de Cilindro`.
4. Cierra con `Culata` sobre el hueco. En las caras libres de la culata pon: `Inyector de Combustible` (obligatorio),
   `Admisión de Aire` (obligatoria, con aire libre delante), `Bujía` (obligatoria para encender) y `Escape`
   (sin escape el motor rinde ~35%).
5. **Aceite**: usa la `Lata de Aceite de Motor` sobre las paredes **dentro** del hueco del cilindro. Se pega como una
   liana (hasta 3 capas). El pistón se desliza sobre ese aceite; se va gastando con el uso.
   Sin aceite: mucha fricción, el motor se calienta y termina gripándose.
6. Llena los inyectores con combustible (tubos de Create o cubo). Por defecto acepta lava (rinde la mitad) y cualquier fluido
   de los tags `c:fuels/*`, `c:diesel`, `c:gasoline`, etc. (tag `aeronauticsbigengine:engine_fuel`).
7. Pon la `ECU` tocando la línea del cigüeñal, haz clic derecho y pulsa **Arrancar**.

## Física con Sable
En la ECU, **Ensamblar (Sable)** convierte:
- el grupo giratorio (cigüeñal, codos, volantes) en un sub-nivel unido a la bancada con una **articulación rotativa** y un servo;
- cada pistón + biela en su propio sub-nivel con una **articulación prismática** (solo se desliza a lo largo del cilindro), con servo.

Las colisiones entre las piezas móviles y el bloque del motor están desactivadas para que puedan solaparse.
Si el motor está sobre una nave (sub-nivel de Aeronautics), las fuerzas de las piezas se transmiten a la nave;
sin ensamblar, el motor aplica a la nave un par de reacción y vibración si está mal sincronizado.
**Desensamblar** devuelve todos los bloques a su sitio.

## Masa → torque → stress units
- Cada cilindro aporta `torquePerKpg × masa del pistón y la biela (masa de Sable)` de capacidad por RPM.
  Create multiplica esa capacidad por la velocidad, así que **SU = torque × RPM** (potencia).
  Pistones más grandes y pesados ⇒ más torque ⇒ más SU.
- El volante y los codos añaden **inercia**: el motor acelera más lento pero aguanta mejor los cambios de carga.
- La carga de la red de Create frena el motor; el gobernador de la ECU añade combustible para mantener las RPM.
- Las masas se definen en `data/aeronauticsbigengine/physics_block_properties/` (propiedad `sable:mass`),
  así que se pueden cambiar con un datapack.

## Ajustes de la ECU
| Ajuste | Qué hace |
|---|---|
| Acelerador | Fija las RPM objetivo entre el ralentí y las RPM máximas (también una señal de redstone analógica) |
| Ralentí / RPM máx. | Régimen en vacío y limitador de vueltas |
| Combustible | Inyección máxima por ciclo (limita el torque y el consumo) |
| Mezcla A/C | Relación aire/combustible (14.7 = estequiométrica; ~13 da más potencia; fuera de 9–23 no enciende) |
| Avance | Grados de encendido antes del PMS. Avance óptimo crece con las RPM; demasiado = detonación (calor) |
| Por cilindro | Activar/desactivar, **vuelta 1 o 2** del ciclo de 4 tiempos y ajuste fino del avance |
| Auto-sincronizar | Reparte las explosiones uniformemente en los 720° del ciclo |
| Giro | Invierte el sentido de giro de la salida |

Con la llave de Create sobre un **Codo de Cigüeñal** se cambia el ángulo del codo (0/90/180/270°) para definir el orden de encendido.
Las gafas de ingeniero muestran RPM, capacidad, temperatura, aceite y combustible.

## Configuración
`serverconfig/aeronauticsbigengine-server.toml`: torque por masa, fricción, consumo, desgaste de aceite, temperatura de gripado,
rigidez/amortiguación de los servos y `invertCrankMotor` / `invertPistonMotor` por si el giro físico sale al revés.

## Compilar
Requiere **Java 21** para Gradle (Minecraft 1.21.1). Si tu Java por defecto es más nuevo (25, 27...), no pasa nada:
`gradle/gradle-daemon-jvm.properties` hace que Gradle use un JDK 21 instalado o descargue Temurin 21 automáticamente.
```bash
./gradlew build
```
El jar queda en `build/libs/`. Los assets se regeneran con `python3 tools/gen_assets.py` y `python3 tools/gen_lang.py`.

## Estado
Versión 0.1.0: compila contra Create 6.0.10, Sable 2.0.6 y Create Aeronautics 1.3.2, pero **todavía no se ha probado en el juego**.
Sobre todo hay que comprobar el sentido de los servos de Sable y lo estable que es la física a muchas RPM.
