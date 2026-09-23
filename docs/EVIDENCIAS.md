# Evidencias de ejecución

Ejecución completa del servicio contra la API real de
[Swagger Petstore](https://petstore.swagger.io/#/).

**Entorno:** Spring Boot 3.2.7 · Java 17.0.20.1 · Gradle 8.7 · `http://localhost:8080`

```
2026-09-22 18:30:27.379  INFO  com.example.exam.ExamApplication  : Starting ExamApplication using Java 17.0.20.1
2026-09-22 18:30:36.215  INFO  com.example.exam.ExamApplication  : Started ExamApplication in 12.203 seconds
```

> Las líneas de log muestran, entre corchetes, el identificador de correlación de la petición que
> las originó.

---

## 1. Alta de una mascota — `POST /api/pet`

Requisitos 8, 9 y 10 del enunciado.

**Petición**

```http
POST http://localhost:8080/api/pet
Content-Type: application/json

{
  "id": 10000023,
  "status": "available",
  "name": "testingPet1"
}
```

**Respuesta**

```http
HTTP/1.1 201
X-Request-Id: 18b01bf4-c46b-415e-8489-8de7d0b1ca75
Location: http://localhost:8080/api/pet/10000023
Content-Type: application/json

{
  "transactionId": "46b0c6e4-9713-4d80-9619-5eced446599e",
  "dateCreated": "2026-09-22T18:30:40.199685457",
  "status": true,
  "name": "testingPet1"
}
```

**Consola** — la capa de servicio imprime la información antes de devolverla:

```
2026-09-22 18:30:40.199  INFO [18b01bf4-c46b-415e-8489-8de7d0b1ca75] com.example.exam.service.PetService : Mascota registrada: id=10000023, transactionId=46b0c6e4-9713-4d80-9619-5eced446599e, dateCreated=2026-09-22T18:30:40.199685457, status=true, name=testingPet1
```

`transactionId` es un UUID versión 4 generado en la capa de servicio y `dateCreated` corresponde a
la fecha y hora del sistema en el momento del alta. La cabecera `Location` se construye con el
identificador que confirmó el sistema externo.

---

## 2. Consulta de una mascota — `GET /api/pet/{petId}`

Requisitos 4, 5 y 6 del enunciado.

**Petición**

```http
GET http://localhost:8080/api/pet/10000023
```

**Respuesta**

```http
HTTP/1.1 200
X-Request-Id: aefa2ab8-9f07-4a4c-b443-c3dba06949e8
Content-Type: application/json

{
  "id": 10000023,
  "name": "testingPet1",
  "status": "available"
}
```

**Consola**

```
2026-09-22 18:30:40.331  INFO [aefa2ab8-9f07-4a4c-b443-c3dba06949e8] com.example.exam.service.PetService : Mascota consultada: id=10000023, name=testingPet1, status=available
```

---

## 3. Mascota inexistente — `404 Not Found`

```http
GET http://localhost:8080/api/pet/999999999
```

```http
HTTP/1.1 404
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Mascota no encontrada",
  "status": 404,
  "detail": "La mascota 999999999 no existe en la tienda",
  "instance": "/api/pet/999999999",
  "petId": 999999999
}
```

---

## 4. Identificador no positivo — `400 Bad Request`

La validación se resuelve antes de consumir una llamada a la API externa.

```http
GET http://localhost:8080/api/pet/-1
```

```http
HTTP/1.1 400
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Solicitud inválida",
  "status": 400,
  "detail": "Los parámetros de la solicitud no son válidos.",
  "instance": "/api/pet/-1"
}
```

---

## 5. Identificador no numérico — `400 Bad Request`

```http
GET http://localhost:8080/api/pet/abc
```

```http
HTTP/1.1 400
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Solicitud inválida",
  "status": 400,
  "detail": "El parámetro de la solicitud no tiene un valor válido.",
  "instance": "/api/pet/abc"
}
```

---

## 6. Cuerpo incompleto en el alta — `400 Bad Request`

```http
POST http://localhost:8080/api/pet
Content-Type: application/json

{ "id": 0 }
```

```http
HTTP/1.1 400
Content-Type: application/problem+json

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

## 7. Correlación propuesta por el cliente

Cuando el consumidor envía su propio `X-Request-Id`, el servicio lo respeta y lo utiliza en las
trazas, lo que permite seguir una operación a través de varios sistemas.

```http
GET http://localhost:8080/api/pet/10000023
X-Request-Id: evidencia-0007
```

```http
HTTP/1.1 200
X-Request-Id: evidencia-0007
```

```
2026-09-22 18:30:40.775  INFO [evidencia-0007] com.example.exam.service.PetService : Mascota consultada: id=10000023, name=testingPet1, status=available
```

---

## 8. Pruebas automatizadas

```
$ ./gradlew clean build

BUILD SUCCESSFUL
```

| Clase de prueba | Pruebas | Fallos |
|---|---:|---:|
| `PetServiceTest` | 8 | 0 |
| `PetStoreClientTest` | 9 | 0 |
| `PetControllerTest` | 12 | 0 |
| `ExamApplicationTests` | 1 | 0 |
| `ConsoleLogPatternTest` | 1 | 0 |
| **Total** | **31** | **0** |

Cobertura reportada por JaCoCo:

| Métrica | Cobertura |
|---|---|
| Instrucciones | 99.2 % |
| Ramas | 100 % |
| Líneas | 98.3 % |
| Clases | 100 % |

El escenario de indisponibilidad del sistema externo (`502 Bad Gateway`) no se puede provocar
contra el servicio real; queda verificado en `PetStoreClientTest` y `PetControllerTest`.
