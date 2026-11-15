# Plan: mod "Anvil Repair Cost" (Minecraft Java 26.2, Fabric)

**Estado:** fases 1 y 2 implementadas y compiladas (`build/libs/anvil-repair-cost-1.0.0.jar`). Rama: `anvil-repair-cost-26.2-fabric`.

Objetivo: reducir al 50 % la penalización por trabajos previos (Prior Work Penalty) del yunque. Nada más cambia: costes de encantamientos, reparación, renombrado, incompatibilidades y el límite de 39 niveles en Supervivencia.

Entregable: `build/libs/anvil-repair-cost-<version>.jar` para copiar a `mods/`.

## 1. Decisiones

| Tema | Decisión | Motivo |
|---|---|---|
| Técnica | **Mixin** sobre `AnvilMenu` (usa MixinExtras, que ya viene con Fabric Loader) | El cálculo está dentro de `AnvilMenu.createResult`; Fabric API no expone eventos para el yunque. |
| Fabric API | **Fase 1: no. Fase 2: sí** | La fase 1 solo necesita `fabricloader`, `minecraft` y `java`. El comando de la fase 2 usa `fabric-command-api-v2`, así que `fabric-api` pasa a ser obligatorio. |
| Mappings | Nombres de Mojang, sin remapeo | Desde 26.1 Minecraft va sin ofuscar y Fabric ya no mantiene Yarn. |
| Lado | Servidor (en un mundo individual corre el servidor integrado) | El coste se calcula en `AnvilMenu` en el servidor; el cliente recibe el resultado. `environment: "*"` por si el cliente predice el coste. |

Versiones (tomadas de la rama `26.2` de `fabric-example-mod`; confirmar al crear el proyecto):

- Minecraft `26.2`, Java **25**
- Fabric Loader `0.20.1`
- Loom `1.19-SNAPSHOT`, plugin `net.fabricmc.fabric-loom`
- Gradle `9.8.2`

## 2. Cómo funciona el Prior Work Penalty

Verificado en el código de 26.2 (`AnvilMenu`, vía `genSources`):

- Cada objeto guarda `DataComponents.REPAIR_COST`.
- Al combinar en `AnvilMenu.createResult`, el coste total suma el `REPAIR_COST` de los dos objetos, más los costes de encantamientos, reparación y renombrado.
- El resultado guarda un nuevo `REPAIR_COST` calculado con `calculateIncreasedRepairCost(int)`, que es `coste * 2 + 1` (0, 1, 3, 7, 15, 31...).
- Si el coste supera 39 en Supervivencia, aparece "Demasiado caro".

Por eso las botas del ejemplo tienen 31 de penalización: 3 (libro) + 31 + ... = 35. Renombrar cuesta 32 = 1 + 31.

## 3. Qué se modifica

Hay dos puntos posibles. El usuario debe elegir uno (recomendado: A):

- **A. Al leer (recomendada).** Al sumar la penalización en `createResult`, usar `penalización / 2` (redondeo hacia arriba para que 1 siga siendo 1). Efecto: las botas actuales con 31 pasan a costar ~16 de penalización, y también afecta a objetos que ya existen. Es lo que el usuario necesita para su caso real.
- **B. Al escribir.** Reducir lo que añade `calculateIncreasedRepairCost`. Un solo punto de inyección, pero los objetos ya penalizados no mejoran.

Con A, el valor guardado en el objeto no se toca (sigue creciendo como en vanilla). Solo se reduce lo que se cobra. Es reversible: al quitar el mod, todo vuelve a vanilla.

Redondeo: `(p + 1) / 2`. Con `p = 0` da 0; con `p = 1` da 1; con `p = 31` da 16.

## 4. Estructura del proyecto

```
anvil-repair-cost/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradlew / gradlew.bat / gradle/wrapper/
├── docs/PLAN.md
└── src/main/
    ├── java/com/luis/anvilrepaircost/
    │   ├── AnvilRepairCost.java          (ModInitializer vacío; opcional)
    │   └── mixin/AnvilMenuMixin.java
    └── resources/
        ├── fabric.mod.json
        └── anvilrepaircost.mixins.json
```

Base: copiar `build.gradle`, `gradle.properties`, `settings.gradle` y el wrapper de la rama `26.2` de `fabric-example-mod`, y cambiar id, grupo y nombre. Se elimina la dependencia de `fabric-api` y el código de cliente.

`fabric.mod.json`: `id: anvilrepaircost`, `environment: "*"`, `depends: fabricloader >=0.20.1, minecraft ~26.2, java >=25`, mixins `anvilrepaircost.mixins.json`.

## 5. Pasos

1. **Crear el proyecto** desde la plantilla de la rama `26.2` y comprobar que `./gradlew build` compila sin cambios.
2. **Leer el código real.** Ejecutar `./gradlew genSources` y abrir `net.minecraft.world.inventory.AnvilMenu`. Anotar: nombre exacto de la clase y de `createResult`, dónde se suma `REPAIR_COST`, la firma de `calculateIncreasedRepairCost` y dónde se aplica el límite de 39. **No se escribe ningún mixin antes de este paso.**
3. **Escribir `AnvilMenuMixin`** con `@ModifyVariable` en el 2.º `STORE` de `tax` dentro de `createResult` (la línea `tax += REPAIR_COST(input) + REPAIR_COST(addition)`), aplicando `(tax * porcentaje + 99) / 100`. Se eligió en lugar de `@WrapOperation` porque `getOrDefault(REPAIR_COST)` se llama también al guardar el resultado. Con `@Mixin(AnvilMenu.class)` y `defaultRequire: 1` en el JSON: si el objetivo no existe, el juego falla al arrancar en vez de ignorarlo en silencio.
4. **Probar en un mundo de pruebas** (`./gradlew runClient`), con `/give` de botas con `minecraft:repair_cost=31`:
   - Botas con penalización 31 + libro: coste menor que 35.
   - Renombrar botas con penalización 31: ~17 en lugar de 32.
   - Botas nuevas + libro de Caída de pluma II: sigue costando 3.
   - Reparar con material y combinar dos objetos: el coste base no cambia.
   - Encantamientos incompatibles: siguen rechazados.
   - Supervivencia: "Demasiado caro" a partir de 40; en Creativo no hay límite.
5. **Comprobar que no se toca nada más.** Comparar costes con y sin el mod en 5-6 casos fijos (tabla en este documento) y revisar que el mixin solo envuelve lecturas de `REPAIR_COST`.
6. **Generar el JAR.** `./gradlew build` y usar `build/libs/anvil-repair-cost-<version>.jar` (no el `-sources`). Copiarlo a `mods/` con Fabric Loader 0.20.1 o superior y probarlo junto con el resto de mods.
7. **No Anvil Penalty:** quitarlo de `mods/`. Ambos modifican el mismo cálculo y no se pueden combinar de forma predecible.

Los pasos 1-7 son la **fase 1**: porcentaje fijo al 50 %. Genera un `.jar` utilizable por sí solo.

## 5b. Fase 2: comando para cambiar el porcentaje en el juego

Se hace solo cuando la fase 1 funciona y está probada.

**Comando**
- `/anvilcost`: muestra el porcentaje actual.
- `/anvilcost <0-100>`: lo cambia. 100 equivale a vanilla, 0 elimina la penalización.
- Brigadier registrado con `CommandRegistrationCallback` (Fabric API, módulo `fabric-command-api-v2`).
- Permiso: nivel de operador, como los comandos vanilla. En un mundo individual requiere "Permitir trucos".

**Persistencia**
- Archivo `config/anvilrepaircost.json` con `{ "percent": 50 }`, común a todos los mundos.
- Se lee al arrancar, se escribe al ejecutar el comando. Si falta, está corrupto o el valor está fuera de 0-100, se usa 50.
- Por mundo queda descartado salvo que se pida: requiere guardar el dato dentro del mundo.

**Cambios en el código**
- Nueva clase `AnvilConfig` (cargar, guardar, `percent`), y `AnvilRepairCostCommand` (registro del comando).
- `AnvilRepairCost.java` deja de estar vacío: carga la config y registra el comando.
- El mixin cambia el `50` fijo por `AnvilConfig.percent`, con la fórmula `(p * percent + 99) / 100` (redondeo hacia arriba).
- `build.gradle` añade `fabric-api`. `fabric.mod.json` añade `"fabric-api": "*"` a `depends`.

**Pruebas**
- `/anvilcost 100`: los costes coinciden con vanilla.
- `/anvilcost 0`: la penalización no suma nada; el resto de costes no cambia.
- `/anvilcost 50`: igual que la fase 1.
- Valores fuera de rango (`-1`, `101`, texto): el comando los rechaza y no cambia nada.
- Sin permisos: el comando no se ejecuta.
- Reiniciar el juego: el valor se conserva.
- El cambio se aplica al instante, sin reiniciar el mundo.

**A verificar con `genSources`**: la API de comandos y el sistema de permisos de 26.2 (cambiaron en versiones recientes), y los nombres exactos de las clases.

## 5c. Verificado

- `genSources` con JDK 25 (Loom exige Java 25; si `JAVA_HOME` apunta a otro JDK, la compilación falla).
- El mixin se aplica: con `-Dmixin.debug.export=true` se comprobó en el bytecode que la llamada está justo después de `tax += ...` y es la única modificación.
- El mod carga en un servidor de desarrollo (`runServer`) sin errores de mixin.
- **No probado en el juego:** costes reales en un yunque y el comando `/anvilcost` (requiere cliente y aceptar el EULA). Seguir la lista de pruebas de los pasos 4 y 5b.

## 6. Riesgos

- Los nombres de método y la estructura de `createResult` en 26.2 no están verificados. Se resuelve en el paso 2.
- Otros mods que modifiquen `AnvilMenu` (p. ej. mods de yunques o de encantamientos) pueden chocar. El mixin solo envuelve lecturas, lo que minimiza el conflicto, pero hay que probarlo con tu lista de mods.
- El cliente puede mostrar un coste distinto si el mod no está instalado en él. En mundos individuales no ocurre.
- `loom 1.19-SNAPSHOT` es una versión de desarrollo; si falla la resolución, usar la versión estable indicada en https://fabricmc.net/develop/.

## 7. Fuera de alcance

Porcentaje distinto por mundo, interfaz gráfica de configuración, versión para servidores con clientes sin mod, otras versiones de Minecraft.

## Fuentes

- https://fabricmc.net/2026/06/15/262.html
- https://docs.fabricmc.net/develop/porting/
- https://github.com/FabricMC/fabric-example-mod/tree/26.2
