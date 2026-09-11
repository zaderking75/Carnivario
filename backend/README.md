# Ejecución local

El proyecto compila para Java 17 y requiere MySQL para ejecutar la aplicación.
Ejecutar los comandos desde `backend/`.

La conexión MySQL, las credenciales y la clave JWT están configuradas en
`src/main/resources/application.yml`. La aplicación no importa `application-local.yml`.

```powershell
mvn spring-boot:run
```

Si se cambia la clave JWT, las sesiones anteriores dejan de ser válidas y hay que
iniciar sesión nuevamente. La base de datos `backend/db/` se conserva localmente,
pero no se versiona. La aplicación usa la conexión MySQL de `application.yml`.

## Compras y permisos

| Operación | Acceso |
| --- | --- |
| `POST /purchase/api` | Usuario autenticado; el servidor asigna comprador y estado `PENDIENTE`. |
| `GET /purchase/api` | Administrador. |
| `GET /purchase/api/{id}` | Propietario o administrador. |

Crear una compra requiere `idPlanta` y `quantity` positivos. El servidor comprueba
existencia y stock, bloquea la planta durante la transacción y guarda compra,
detalle y descuento de stock de forma atómica. `idUser` y `estado` enviados por
el cliente no determinan el comprador ni el estado guardados.

Respuestas: `201` al crear, `400` para datos inválidos o stock insuficiente,
`401` sin autenticación válida, `403` sin permisos y `404` para recursos inexistentes.

## Pruebas

```powershell
mvn test
```

Las pruebas usan el perfil `test` con H2 en memoria y una clave pública exclusiva
de pruebas. No necesitan MySQL ni modifican la base de datos local. Incluyen permisos,
firma y expiración de tokens, validaciones y compras simultáneas. El comportamiento
de concurrencia se verifica sobre H2; no se ha ejecutado esta prueba sobre MySQL.

Desde `frontend/`, `npm test` ejecuta las pruebas del componente Login real con
Vitest y React Testing Library; `npm run build` compila la aplicación. Las otras
pruebas históricas de Karma no forman parte de ese comando.
