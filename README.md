# Pet API

API REST desarrollada como ejercicio técnico para la posición **Ssr Backend Developer**.

Expone dos operaciones sobre mascotas que se apoyan en la API pública
[Swagger Petstore](https://petstore.swagger.io/#/) como sistema externo de almacenamiento.

| | |
|---|---|
| **Framework** | Spring Boot 3.2.7 |
| **Lenguaje** | Java 17 |
| **Construcción** | Gradle (Groovy DSL) · empaquetado `jar` |
| **Coordenadas** | `com.example:exam` · paquete base `com.example.exam` |

---

## Puesta en marcha

No se requiere instalar Gradle ni el JDK 17: el wrapper descarga Gradle 8.7 y la *toolchain* de
Gradle aprovisiona automáticamente un JDK 17 si no está presente en la máquina.

```bash
./gradlew clean build      # compila, ejecuta las pruebas y genera el reporte de cobertura
./gradlew bootRun          # levanta la aplicación en http://localhost:8080
```

Para importar en IntelliJ IDEA: *File · Open* sobre la carpeta raíz; el IDE detecta el proyecto
Gradle y sincroniza las dependencias.

### Configuración

Todos los parámetros de integración están externalizados en `src/main/resources/application.yml`
y pueden sobrescribirse por variable de entorno o argumento de arranque.

| Propiedad | Valor por defecto | Descripción |
|---|---|---|
| `petstore.base-url` | `https://petstore.swagger.io/v2` | Raíz de la API externa |
| `petstore.connect-timeout` | `3s` | Tiempo máximo para establecer la conexión |
| `petstore.read-timeout` | `5s` | Tiempo máximo de espera por la respuesta |

---

## Estructura del proyecto

La organización en paquetes responde uno a uno a la separación solicitada en el enunciado:

| Requisito | Paquete | Contenido |
|---|---|---|
| Clases para definir APIs REST | `com.example.exam.controller` | `PetController`, manejo de errores y filtro de correlación |
| Clases para implementar lógica de negocio | `com.example.exam.service` | `PetService` |
| Clases para conectarse a APIs de terceros | `com.example.exam.client` | `PetStoreClient` y su configuración de transporte |
| Clases para almacenamiento de datos en objetos | `com.example.exam.model` | Modelo de dominio, contratos de entrada/salida y contrato externo |

```
com.example.exam
├── ExamApplication
├── controller
│   ├── PetController              endpoints REST
│   ├── ApiExceptionHandler        traducción de errores a RFC 7807
│   └── RequestIdFilter            correlación de peticiones
├── service
│   └── PetService                 orquestación, trazas y datos de la transacción
├── client
│   ├── PetStoreClient             acceso a la API externa
│   ├── PetStoreClientConfiguration  base URL y timeouts
│   └── PetStoreProperties         configuración externalizada
├── model
│   ├── Pet                        modelo de dominio
│   ├── request/CreatePetRequest   contrato de entrada
│   ├── response/…                 contratos de salida
│   └── external/PetStorePayload   contrato de la API de terceros
└── exception
    ├── PetNotFoundException
    └── PetStoreUnavailableException
```

---

## Contrato de la API

### `GET /api/pet/{petId}`

Consulta una mascota en el sistema externo.

**Parámetro de entrada:** `petId` (*path parameter*, entero positivo).

```bash
curl http://localhost:8080/api/pet/10000023
```

```json
{
  "id": 10000023,
  "name": "testingPet1",
  "status": "available"
}
```

### `POST /api/pet`

Registra una mascota en el sistema externo y devuelve el comprobante de la transacción.

**Parámetros de entrada:** `id`, `status`, `name`.

```bash
curl -X POST http://localhost:8080/api/pet \
  -H 'Content-Type: application/json' \
  -d '{"id":10000023,"status":"available","name":"testingPet1"}'
```

```json
{
  "transactionId": "46b0c6e4-9713-4d80-9619-5eced446599e",
  "dateCreated": "2026-09-22T18:30:40.199685457",
  "status": true,
  "name": "testingPet1"
}
```

### Respuestas de error

Todos los errores se devuelven como `application/problem+json` conforme a la
[RFC 7807](https://www.rfc-editor.org/rfc/rfc7807).

| Situación | Código |
|---|---|
| La mascota no existe en el sistema externo | `404 Not Found` |
| `petId` no es un entero positivo | `400 Bad Request` |
| El cuerpo del `POST` no cumple las validaciones | `400 Bad Request` (incluye el detalle por campo) |
| El sistema externo no responde o falla | `502 Bad Gateway` |

```json
{
  "type": "about:blank",
  "title": "Solicitud inválida",
  "status": 400,
  "detail": "La solicitud contiene campos inválidos.",
  "instance": "/api/pet",
  "errors": {
    "id": "debe ser mayor que cero",
    "status": "es obligatorio",
    "name": "es obligatorio"
  }
}
```

---

## Decisiones de diseño

**Capa anticorrupción sobre la API externa.** El JSON de Petstore incluye atributos que este
servicio no necesita (`category`, `photoUrls`, `tags`). `PetStoreClient` los descarta y traduce la
respuesta al modelo de dominio `Pet`, de modo que el contrato público no queda acoplado al de un
tercero y un cambio aguas arriba no se propaga a los consumidores.

**Los errores se traducen en la frontera.** El cliente convierte un `404` remoto en
`PetNotFoundException` y cualquier otro fallo de comunicación en `PetStoreUnavailableException`.
La capa de servicio nunca manipula códigos HTTP ajenos, y `ApiExceptionHandler` es el único punto
que decide el código de respuesta. El detalle técnico del fallo se registra en el log, pero nunca
se expone al cliente.

**Timeouts explícitos.** Una llamada saliente sin límite de tiempo puede agotar el pool de hilos
del servidor ante un tercero degradado, por lo que los tiempos de conexión y lectura están
configurados y son ajustables por ambiente.

**`Clock` inyectado.** `dateCreated` se obtiene de un `Clock` gestionado por el contenedor en
lugar de invocar `LocalDateTime.now()` directamente. La fecha del sistema sigue siendo la real en
ejecución y las pruebas pueden fijarla para verificar el valor de forma determinista.

**Trazabilidad de extremo a extremo.** `RequestIdFilter` asigna un identificador a cada petición,
lo publica en el MDC —queda impreso en cada línea de log— y lo devuelve en la cabecera
`X-Request-Id`. Si el cliente propone uno propio se respeta, siempre que su formato sea seguro
para escribirse en la bitácora; de lo contrario se descarta y se genera uno nuevo.

**Salida a consola mediante SLF4J.** El requisito de imprimir la información en consola antes de
devolverla se cumple con el *logger* de la aplicación y no con `System.out.println`: se obtiene la
misma salida visible, con marca de tiempo, nivel y correlación, y conservando la posibilidad de
enrutar las trazas a un agregador sin tocar el código.

**Respuestas incompletas del tercero se tratan como fallo.** El sistema externo declara `id` y
`name` en toda mascota almacenada, de modo que una respuesta sin alguno de ellos se rechaza en
lugar de propagar un objeto sin identidad. `status` sí es opcional aguas arriba y se refleja tal
como llega.

**Las trazas no se pueden falsificar.** Los valores devueltos por el sistema externo proceden en
última instancia de la entrada del usuario, así que el patrón de consola colapsa los saltos de
línea de cada registro. De este modo un `name` con un salto de línea embebido no puede inyectar
una línea de bitácora falsa. La misma protección aplica al `X-Request-Id` propuesto por el
cliente, que sólo se acepta si su formato es seguro.

**Objetos inmutables.** Todos los modelos y contratos son `record` de Java 17, y las dependencias
se inyectan por constructor sobre campos `final`.

**`201 Created` en el alta.** El `POST` responde `201` con la cabecera `Location` apuntando al
recurso creado, que es la semántica correcta para una operación de creación en HTTP. La
ubicación se construye con el identificador que **confirma el sistema externo**, no con el que
envió el cliente: el cuerpo de la respuesta no incluye el `id`, así que la cabecera es el único
medio por el que el consumidor sabe dónde quedó el recurso y no puede apuntar a otro sitio.

**Sobre el campo `status` de la respuesta del `POST`.** El ejercicio define `status` como
parámetro de entrada de tipo texto (`"available"`) y, en la evidencia de salida, como un booleano
(`true`). Se respetó esa especificación: en la respuesta, `status` indica si el alta en el sistema
externo se completó correctamente, y no es un eco del valor recibido.

### Dependencias

El proyecto se generó con `Spring Web` como única dependencia funcional, tal como indica el
enunciado. Se añadieron, de forma deliberada:

- `spring-boot-starter-validation` — para validar las entradas de forma declarativa y rechazar
  peticiones inválidas antes de consumir una llamada de red.
- `spring-boot-starter-test` — JUnit 5, AssertJ, Mockito y el soporte de pruebas de Spring.
- `jacoco` — reporte de cobertura.

---

## Pruebas

```bash
./gradlew test
```

31 pruebas automatizadas, ninguna de las cuales depende de la disponibilidad de la API externa:
las llamadas salientes se simulan con `MockRestServiceServer`. Esto mantiene la construcción
reproducible y permite verificar escenarios de error que no se pueden provocar contra el servicio
real.

| Prueba | Alcance |
|---|---|
| `PetServiceTest` | Lógica de negocio con `Mockito` y un `Clock` fijo: mapeo, generación del `transactionId` en formato UUIDv4, fecha de la transacción y salida a consola |
| `PetStoreClientTest` | Integración con la API externa mediante `@RestClientTest`: URL y cuerpo de la petición, mapeo de la respuesta, respuestas incompletas y traducción de los fallos |
| `PetControllerTest` | Capa web con `@WebMvcTest`: códigos de estado, forma del JSON, validaciones, correlación y ausencia de fugas de información en los errores |
| `ExamApplicationTests` | Arranque del contexto de Spring |
| `ConsoleLogPatternTest` | El patrón de consola desplegado impide la inyección de líneas de bitácora |

Cobertura alcanzada: **99 % de instrucciones y 100 % de ramas**. El reporte se genera en
`build/reports/jacoco/test/html/index.html`.

---

## Evidencias

El detalle de la ejecución manual —peticiones, respuestas y salida de consola— está en
[`docs/EVIDENCIAS.md`](docs/EVIDENCIAS.md). En [`postman/`](postman) se incluye una colección lista
para importar con ambos endpoints y sus casos de error.
