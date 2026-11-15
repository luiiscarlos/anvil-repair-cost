# Anvil Repair Cost

Mod de Fabric para Minecraft Java 26.2 que reduce la penalización por trabajos previos del yunque (por defecto al 50 %). No cambia los costes de encantamientos, reparación ni renombrado, ni las incompatibilidades, ni el límite de 39 niveles.

Requisitos: Fabric Loader 0.20.1 o superior, Fabric API y Java 25. No es compatible con mods que modifiquen el mismo cálculo (p. ej. No Anvil Penalty).

## Comando

- `/anvilcost`: muestra el porcentaje actual.
- `/anvilcost <0-100>`: lo cambia (100 = vanilla, 0 = sin penalización). Requiere nivel de operador.

El valor se guarda en `config/anvilrepaircost.json`.

## Compilar y probar

Hace falta JDK 25 (`JAVA_HOME` debe apuntar a él):

```
./gradlew build
```

El `.jar` queda en `build/libs/anvil-repair-cost-<versión>.jar`.

## Publicar una versión

1. Cambia `version` en `gradle.properties` (por ejemplo `1.0.1`) y haz commit.
2. `git tag v1.0.1 && git push origin v1.0.1`
3. GitHub Actions compila, ejecuta los tests y, si pasan, crea el release con el `.jar`.

El tag debe coincidir con `version`; si no, el workflow falla.
