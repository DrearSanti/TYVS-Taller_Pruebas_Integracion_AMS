# Registro de Defectos

Defectos detectados durante las pruebas unitarias, de integración y de sistema
del proyecto **Registraduría**. Cada uno se documenta de forma estructurada para
facilitar su análisis, trazabilidad y corrección.

Los defectos del 1 al 5 son los del ejemplo de referencia. 06 y 07 son los que detectamos nosotros

---

## Formato 1: Lista detallada (narrativa)

### Defecto 01 — Falta de validación de edad negativa *(Prueba unitaria)*

- **Capa afectada:** Dominio (`Registry.registerVoter`)
- **Caso de prueba:** Registro de persona con edad `-1`.
- **Entrada:**
`Person(name="Juan", id=101, age=-1, gender=MALE, alive=true)`
- **Resultado esperado:** `INVALID_AGE` (una edad negativa es un dato imposible, no una persona menor)
- **Resultado obtenido:** `UNDERAGE`
- **Causa probable:** `Registry` evaluaba `age < MIN_AGE` sin distinguir entre "menor de edad" y "edad imposible". Una edad de `-1` caía en la misma rama que una de `17`.
- **Tipo de prueba:** Unitaria (dominio puro)
- **Estado:** **Resuelto** — se añadió `INVALID_AGE` al enum y la regla `age < 0 || age > MAX_AGE` **antes** de la de menor de edad. Verificado por `RegistryWithMockTest.shouldReturnInvalidAgeWhenAgeIsNegative()` y `shouldReturnInvalidAgeWhenAgeExceedsMaximum()`.
- **Prioridad:** Alta

> **Por qué no era un detalle cosmético.** Las dos clases de equivalencia se parecen en el código y no se parecen en nada para quien usa el sistema: a una persona de 17 años se le dice *"espere a cumplir 18"*, mientras que un registro con `-1` significa que **alguien capturó mal el dato** y hay que corregirlo. Devolver `UNDERAGE` en ambos casos le da al segundo un consejo inútil.
>
> El orden de las dos comprobaciones también importa. Si se pregunta primero `age < MIN_AGE`, el `-1` entra por esa rama y `INVALID_AGE` queda inalcanzable — el enum tendría la constante y el sistema no la usaría nunca.
>
> Este defecto era además una **inconsistencia entre talleres**: el de pruebas unitarias ya distinguía los dos casos y este no, de modo que la misma Registraduría se comportaba distinto según el taller desde el que se mirara. Los dos describen ahora el mismo dominio.

**Valor límite asociado:** la frontera entre las dos clases es la **edad 0** — un año menos es imposible, y `0` es el dato correcto de un recién nacido que no puede votar. Está cubierta por `RegistryWithMockTest.shouldReturnUnderageWhenAgeIsZero()`. Sin esa prueba, cambiar `< 0` por `<= 0` no rompe nada y la mutación sobrevive.

---

### Defecto 02 — Registro de persona fallecida *(Prueba unitaria)*

- **Capa afectada:** Dominio (`Registry.registerVoter`)
- **Caso de prueba:** Persona con `alive=false`.
- **Entrada:**
`Person(name="Ana", id=102, age=45, gender=FEMALE, alive=false)`
- **Resultado esperado:** `DEAD`
- **Resultado obtenido:** `VALID`
- **Causa probable:** No se valida correctamente la condición `alive=false`.
- **Tipo de prueba:** Unitaria (regla de negocio)
- **Estado:** **Resuelto** — `Registry.registerVoter` evalúa `if (!p.isAlive()) return RegisterResult.DEAD;`. Verificado por `RegistryWithMockTest.shouldReturnDeadWhenPersonIsNotAlive()`.
- **Prioridad:** Media

---

### Defecto 03 — No se detectan duplicados *(Prueba de integración con H2)*

- **Capa afectada:** Infraestructura (`RegistryRepository`)
- **Caso de prueba:** Dos registros con el mismo `id`.
- **Entradas:**
  - Persona 1 → `Person(name="Carlos", id=200, age=30, gender=MALE, alive=true)`
  - Persona 2 → `Person(name="Carla", id=200, age=25, gender=FEMALE, alive=true)`
- **Resultado esperado:**
  - Persona 1 → `VALID`
  - Persona 2 → `DUPLICATED`
- **Resultado obtenido:**
  - Persona 1 → `VALID`
  - Persona 2 → `VALID`
- **Causa probable:** El método `existsById()` del repositorio no verifica correctamente la existencia previa del registro.
- **Tipo de prueba:** Integración (H2 + capa de aplicación)
- **Estado:** **Resuelto** — `RegistryRepository.existsById` consulta la tabla antes de insertar. Verificado por `RegistryIT.shouldPersistValidVoterAndRejectDuplicates()` (H2) y `RegistryRepositoryPostgresIT.shouldPersistAndRejectDuplicate()` (PostgreSQL real).
- **Prioridad:** Alta

---

### Defecto 04 — Fallo en simulación con mock *(Prueba de integración con Mockito)*

- **Capa afectada:** Aplicación (`Registry`)
- **Caso de prueba:** Registro con `id` duplicado en un repositorio simulado.
- **Configuración:**

```java
when(repo.existsById(7)).thenReturn(true);
```

- **Resultado esperado:** `DUPLICATED`
- **Resultado obtenido:** `NullPointerException`
- **Causa probable:** Dependencia `RegistryRepositoryPort` no inicializada correctamente durante el mock.
- **Tipo de prueba:** Integración (mock)
- **Estado:** **Resuelto** — el escenario funciona; `RegistryWithMockTest.shouldWrapPersistenceFailure()` verifica que el fallo del puerto se traduce a `RegistryPersistenceException`.
- **Prioridad:** Media

---

### Defecto 05 — Error HTTP 500 no manejado *(Prueba de sistema REST)*

- **Capa afectada:** Delivery (`RegistryController`)
- **Caso de prueba:** Envío de JSON con campo `gender` inválido.
- **Entrada:**

```json
{ "name": "Laura", "id": 500, "age": 20, "gender": "OTHER", "alive": true }
```

- **Resultado esperado:** `HTTP 400` (Bad Request)
- **Resultado obtenido:** `HTTP 500` (Internal Server Error)
- **Causa probable:** Falta de validación o manejo de excepción `IllegalArgumentException` en el controlador.
- **Tipo de prueba:** Sistema (TestRestTemplate)
- **Estado:** **Resuelto** — `RegistryExceptionHandler` traduce `IllegalArgumentException` a **400 Bad Request**: un género fuera del enum es error del cliente, no del servidor. Verificado por `RegistryControllerIT.shouldReturnBadRequestWhenGenderIsNotValid()`.
- **Prioridad:** Alta

---

### Defecto 06 — Dos librerías aportan `org.json.JSONObject` al classpath *(Prueba de sistema)*

- **Capa afectada:** Configuración del proyecto (`pom.xml`)
- **Caso de prueba:** Cualquier arranque del contexto de Spring Boot durante `mvn verify`, por ejemplo `RegistryControllerIT` o `RegistraduriaProviderPactIT`.
- **Entrada:** Ejecución de `mvn clean verify` sobre el proyecto completo.
- **Resultado esperado:** Un único `org.json.JSONObject` en el classpath, de modo que el parseo de JSON sea determinista.
- **Resultado obtenido:** Dos implementaciones distintas de la misma clase. Spring Boot lo advierte al construir el contexto:

```
Found multiple occurrences of org.json.JSONObject on the class path:

  jar:file:/.../com/vaadin/external/google/android-json/0.0.20131108.vaadin1/android-json-0.0.20131108.vaadin1.jar!/org/json/JSONObject.class
  jar:file:/.../org/json/json/20240205/json-20240205.jar!/org/json/JSONObject.class

You may wish to exclude one of them to ensure predictable runtime behavior
```

- **Causa probable:** `spring-boot-starter-test` arrastra `android-json` mientras que las dependencias de Pact traen `org.json:json`. Cuál de las dos gana depende del orden de carga del classpath, que no está garantizado entre entornos.
- **Tipo de prueba:** Sistema (detectado por la construcción del contexto de Spring durante las pruebas)
- **Estado:** **Abierto** — se documenta antes de corregir. La solución sería excluir una de las dos en el `pom.xml`.
- **Prioridad:** Media

> **Por qué no es solo una advertencia.** Las dos implementaciones de `JSONObject` no son idénticas: `android-json` es una versión reducida pensada para Android y `org.json:json` es la de referencia. Si el orden del classpath cambia entre la máquina de un integrante, la de otro y el servidor de CI, el mismo JSON podría procesarse de forma distinta sin que nada falle de manera visible. Es la clase de defecto que produce un "en mi máquina funciona" y que solo aparece en producción.

---

### Defecto 07 — Divergencia de dialecto SQL entre H2 y PostgreSQL *(Prueba de integración)*

- **Capa afectada:** Infraestructura (`RegistryRepository`) y estrategia de pruebas
- **Caso de prueba:** La misma consulta ejecutada contra los dos motores, sobre el esquema que crea `RegistryRepository.initSchema()`.
- **Entrada:**

```sql
SELECT "name" FROM registry WHERE id = 400
```

- **Resultado esperado:** Si H2 sustituyera a PostgreSQL en pruebas, la misma consulta debería funcionar en ambos motores.
- **Resultado obtenido:** Funciona en PostgreSQL y falla en H2:

```
H2 rechazo la consulta: Columna "name" no encontrada
Column "name" not found; SQL statement:
SELECT "name" FROM registry WHERE id = 400 [42122-224]
```

- **Causa probable:** PostgreSQL pliega los identificadores sin comillas a minúsculas, de modo que la columna queda como `name` y `"name"` la resuelve. H2 los pliega a mayúsculas, así que la columna queda como `NAME` y `"name"` no existe. El entrecomillado vuelve el identificador sensible a la caja y expone la divergencia.
- **Tipo de prueba:** Integración. Verificado con código en ambos motores: `RegistryRepositoryH2DialectIT.shouldFailOnQuotedLowercaseIdentifierInH2()` y `RegistryRepositoryPostgresIT.shouldResolveQuotedLowercaseIdentifier()` (esta última requiere Docker).
- **Estado:** **Resuelto** — el código de producción no usa identificadores entrecomillados, de modo que no hay defecto activo. Las dos pruebas quedan como constancia del riesgo para que nadie los introduzca más adelante.
- **Prioridad:** Media

> **Por qué este defecto es el argumento del taller.** Sustituir PostgreSQL por H2 en las pruebas es cómodo: arranca en milisegundos y no necesita Docker. Pero es una aproximación, no una equivalencia. Una prueba de integración que pase contra H2 no garantiza que el código funcione contra el motor de producción, y aquí está demostrado con dos pruebas que ejecutan la misma sentencia y obtienen resultados opuestos, en vez de afirmarlo en un comentario.
>
> Es también la razón concreta por la que vale la pena tener Docker disponible: las 5 pruebas de `RegistryRepositoryPostgresIT` se omiten sin él, y son justamente las que ejercitan el motor real.

---


## Formato 2: Tabla de defectos (bug tracking)

| ID | Caso de Prueba | Capa | Resultado Esperado | Resultado Obtenido | Tipo | Estado | Prioridad |
|----|----------------|------|--------------------|--------------------|------|----------|------------|
| 01 | Edad negativa | Dominio | `INVALID_AGE` | `UNDERAGE` | Unitaria | Resuelto | Alta |
| 02 | Persona muerta | Dominio | `DEAD` | `VALID` | Unitaria | Resuelto | Media |
| 03 | Duplicado por ID | Infraestructura | `DUPLICATED` | `VALID` | Integración | Resuelto | Alta |
| 04 | Fallo de persistencia | Aplicación | `RegistryPersistenceException` | `NullPointerException` | Unitaria (mock) | Resuelto | Media |
| 05 | Error HTTP 500 | Delivery | `HTTP 400` | `HTTP 500` | Sistema (REST) | Resuelto | Alta |
| 06 | `JSONObject` duplicado en classpath | Configuración (`pom.xml`) | Una sola implementación | Dos JAR aportan la misma clase | Sistema | Abierto | Media |
| 07 | Identificador entrecomillado en minúsculas | Infraestructura | Misma consulta válida en H2 y PostgreSQL | Válida en PostgreSQL, `Column "name" not found` en H2 | Integración | Resuelto | Media |


---

## Convenciones de Estado

| Estado | Significado |
|---------|-------------|
| **Abierto** | El defecto fue detectado pero no corregido. |
| **En progreso** | El defecto se encuentra en análisis o corrección. |
| **Resuelto** | El defecto fue corregido y validado mediante pruebas. |

---

## Observaciones

- Los defectos detectados evidencian la importancia de **mantener pruebas unitarias robustas** antes de pasar a integración.
- La validación cruzada entre pruebas con mocks e integración real (H2) permitió identificar inconsistencias en el flujo de persistencia.
- Los errores en las pruebas REST destacan la necesidad de implementar **manejadores globales de excepciones (ControllerAdvice)** para mejorar la estabilidad del sistema.
