# Autenticación híbrida: estado y despliegue

Implementación de la guía `PASO_A_PASO_AUTENTICACION_HIBRIDA_MSAL_LOGIN_LOCAL_CARNIVARIO.md`, leída completa el 15 de septiembre de 2026.

Rama: `feature/auth-hibrida-msal-local`. Los cambios están en el directorio de trabajo; no se han publicado en GitHub ni desplegado en AWS.

## Estado del paso a paso

| Pasos de la guía | Resultado |
| --- | --- |
| 1–4: arquitectura y rama | Rama creada; login local y Microsoft separados por encabezado. |
| 5: Entra ID | Pendiente de confirmar en el portal. La credencial del `.env` obtiene acceso a Microsoft, pero la consulta de aplicaciones a Graph devuelve HTTP 403. |
| 6–17: backend | Implementados Java 17, Resource Server, JJWT local, conversión de roles/scopes, sincronización del usuario Microsoft, endpoints Azure, CORS y ejemplo de entorno. Compilación y pruebas locales realizadas. |
| 18–25: frontend | MSAL, inicialización, renovación del access token, Axios, sesiones, logout y eliminación del callback anterior implementados. |
| 26–29: API Gateway | Configuración pendiente de acceso AWS; instrucciones exactas abajo. |
| 30–31: HTTPS y Docker | Nginx HTTPS y build args implementados. Ambas imágenes construidas localmente. Nginx validado con certificado temporal, no con el certificado de EC2. |
| 32–33: despliegue | Scripts y workflow preparados; no ejecutados en EC2 por falta de conexión SSH/perfil AWS. |
| 34–41: pruebas | Casos locales automatizados y arranque Docker con MySQL verificados. Falta login Microsoft interactivo, validación del Gateway y pruebas en EC2. |
| 42: imágenes | Nginx envía `/images/` al Gateway. Debe existir la ruta pública `GET /images/{proxy+}` hacia el backend. |
| 43–50: revisión, evidencias y rollback | Pruebas y recuperación preparadas. Las capturas de Azure/AWS y las pruebas con usuarios reales siguen pendientes. Los secretos no se incorporaron al repositorio. |

## Comportamiento implementado

- Local: `POST /user/api/login` devuelve JWT HS512. Axios lo envía en `X-Local-Token`; el backend obtiene el rol vigente de MySQL.
- Microsoft: MSAL obtiene un access token para los scopes de Carnivario. Axios lo envía en `Authorization: Bearer`; Spring comprueba firma, emisor, expiración y audiencia.
- Una petición con ambos encabezados se rechaza. Un JWT local en Bearer, o un token Azure en `X-Local-Token`, se rechaza.
- `/user/api/me` crea o sincroniza el usuario Microsoft. Se conserva la contraseña de una cuenta existente.
- Según la guía, **si la cuenta local y la cuenta Microsoft tienen el mismo correo, el App Role de Azure actualiza el rol guardado en MySQL**. Esto también cambia los permisos de futuros accesos locales de esa cuenta. Usar cuentas de prueba controladas para demostrar ambos flujos.
- Las rutas Azure exigen realmente autenticación Azure; la ruta de lectura exige `Carnivario.Read` y la de administración exige `Carnivario.Write` más `ADMIN`, incluso si se accede directamente al backend.
- Compras y checkout requieren autenticación. La consulta global de compras requiere `ADMIN` porque devuelve compras de todos los usuarios.
- Una renovación Microsoft fallida no transforma la petición en una petición anónima. Si hace falta interacción, MSAL redirige a Microsoft; si falla la sincronización del perfil, se limpia la sesión anterior.

Se usan `@azure/msal-browser` 4.30 y `@azure/msal-react` 3.0, compatibles con el flujo y las URI de la guía. MSAL Browser 5 cambia el mecanismo de redirect/iframe; no actualizar de versión mayor sin adaptar ese flujo.

## Pruebas locales

Desde `backend`:

```powershell
mvn -B clean verify
```

Las pruebas usan H2 y claves RSA de prueba servidas localmente. No usan la base de datos del `.env`, ni solicitan tokens reales a Entra. Incluyen firma incorrecta, audiencia/emisor incorrectos, token expirado, permisos CLIENTE/ADMIN, CORS y sincronización del perfil.

Desde `frontend`:

```powershell
npm ci
npm run test:auth
npm run build
```

`npm run test:auth` ejecuta las pruebas nuevas de autenticación; no ejecuta los antiguos tests Jasmine de `src/__test__`.

Imágenes verificadas localmente:

```text
carnivario-backend:hybrid-auth
carnivario-frontend:hybrid-auth
```

Desde la raíz, con Docker disponible y la imagen `mysql:8`:

```powershell
python scripts/smoke-docker.py
```

Esta prueba crea una red, MySQL y backend temporales con contraseñas generadas. Verifica catálogo 200, perfil anónimo 401, compra anónima 401, registro 201, login 200, perfil local 200, CLIENTE en operación ADMIN 403, JWT local en ruta Azure 403 y listado de usuarios ADMIN 200. Comprueba que el backend siga activo al menos 15 segundos y elimina los contenedores, volúmenes anónimos y red de prueba.

## Configuración pendiente en Entra

En la aplicación `169c9415-d7d0-4892-8ccf-965f3f1a0dde`, tenant `3b914fe6-6475-4be6-8639-9de64b6ba898`:

1. Registrar plataforma SPA con `https://3-212-230-250.sslip.io` y, para desarrollo, `http://localhost:5173`.
2. Exponer `api://169c9415-d7d0-4892-8ccf-965f3f1a0dde` con `Carnivario.Read` y `Carnivario.Write`.
3. Comprobar que la API emita access tokens v2 (`api.requestedAccessTokenVersion: 2`), coherentes con el issuer configurado.
4. Crear/asignar App Roles `ADMIN` y `CLIENTE` a los usuarios de prueba y conceder el consentimiento necesario para los scopes.
5. Verificar que el access token incluya `preferred_username` o `email`; el backend no acepta una identidad sin ese dato.

El client secret del `.env` no sirve como acceso SSH ni como credencial AWS. La nueva autenticación del frontend tampoco lo necesita.

## Configuración pendiente en API Gateway

API base: `https://sioife5g10.execute-api.us-east-1.amazonaws.com/Deploy`.

Aplicar estos cambios **después de comprobar que el nuevo backend protege sus rutas**:

| Configuración | Valor |
| --- | --- |
| CORS origins | `https://3-212-230-250.sslip.io`, opcionalmente el HTTP de transición |
| CORS methods | GET, POST, PUT, DELETE, OPTIONS |
| CORS headers | Authorization, Content-Type, X-Local-Token |
| Credentials | OFF |
| Rutas compartidas `/user/api`, `/planta/api`, `/purchase/api`, `/uploads/api`, `/checkout/api` y sus subrutas | Authorization NONE en Gateway; Spring aplica la protección |
| `GET /images/{proxy+}` | Authorization NONE; integración hacia `/images/{proxy+}` en Spring |
| `GET /auth/azure-check` | AzureJWT; scope Carnivario.Read |
| `POST /auth/azure-admin-check` | AzureJWT; scope Carnivario.Write |

Reutilizar authorizer `AzureJWT` con identity source `$request.header.Authorization`, issuer `https://login.microsoftonline.com/3b914fe6-6475-4be6-8639-9de64b6ba898/v2.0` y audience `169c9415-d7d0-4892-8ccf-965f3f1a0dde`. Publicar cambios en stage `Deploy` cuando no tenga auto-deploy.

## Variables y despliegue del backend

Copiar `backend/.env.example` a `/home/ec2-user/backend.env` en EC2 y completar los valores privados. El formato de Docker es **`NOMBRE=valor`**, sin `export` ni comillas que deban quitarse. Un archivo de propiedades con `NOMBRE: valor` funciona de otra manera y no sirve directamente para `docker --env-file`.

Variables requeridas:

```text
DB_HOST
DB_USERNAME
DB_PASSWORD
JWT_SECRET
AZURE_TENANT_ID
MAIL_USERNAME
MAIL_PASSWORD
```

`DB_HOST` es el nombre real del MySQL accesible desde el contenedor; por ejemplo, `mysql-carnivario` dentro de `carnivario-net`. `JWT_SECRET` debe ser Base64 de al menos 64 bytes aleatorios. No generar otro secreto si se quieren conservar las sesiones locales existentes.

El `.env` proporcionado no contiene `MAIL_USERNAME` ni `MAIL_PASSWORD`; deben configurarse antes de desplegar. `AZURE_CLIENT_ID` puede configurarse para cambiar la audiencia; por defecto se utiliza el Client ID público de esta guía. `AZURE_CLIENT_SECRET` y `AZURE_ADMIN_GROUP_ID` ya no son necesarios para ejecutar el backend.

En EC2:

```bash
chmod 600 /home/ec2-user/backend.env
sudo docker network inspect carnivario-net
sudo bash scripts/deploy-backend.sh voodoooq/carnivario-backend:TAG_PROBADO /home/ec2-user/backend.env
```

El script descarga primero la imagen, conserva el contenedor anterior con nombre fechado y copia sus imágenes subidas. Si la nueva API no responde, guarda logs privados en `/var/tmp` y recupera el contenedor anterior. El despliegue tiene una breve interrupción mientras se reemplaza el contenedor.

### Secretos de GitHub Actions

`docker-publish.yml` se ejecuta al hacer push a `Develop`, `main` o `feature/auth-hibrida-msal-local`, y también manualmente. Primero ejecuta `auth-checks.yml` como workflow reutilizable; solo publica ambas imágenes cuando las pruebas backend y frontend terminan correctamente. Actualiza los tags `latest` y el SHA del commit. La configuración pública Vite se transmite como build args, con valores por defecto del laboratorio y posibilidad de sustituirlos con repository variables `VITE_*`.

`auth-checks.yml` también se ejecuta por separado en pull requests o manualmente. Una ejecución independiente de **Check hybrid authentication** valida el código, pero no publica imágenes.

`deploy-backend.yml` agrega un despliegue **manual por SSH**, separado de la publicación. Pasa los siete secretos de backend al `docker` de EC2 a través de SSH, sin incorporarlos a la imagen ni imprimirlos.

Además de los siete secretos de backend, configurar:

```text
EC2_HOST            DNS o IP pública de la EC2 del backend
EC2_USERNAME        Opcional; por defecto ec2-user
EC2_SSH_KEY         Clave privada que permite conectarse al servidor
EC2_KNOWN_HOSTS     Entrada SSH known_hosts del servidor, verificada por un canal confiable
```

El usuario SSH debe poder ejecutar `sudo -n`. La EC2 debe poder descargar la imagen de Docker Hub; si es privada, su Docker debe estar autenticado. Ejecutar el workflow indicando un tag publicado, preferentemente el SHA del commit probado. No se ha configurado ni ejecutado este workflow en GitHub.

## Despliegue del frontend

Publicar la imagen con el workflow actualizado. En la EC2 del frontend, comprobar los certificados y los puertos 80/443:

```bash
sudo bash scripts/deploy-frontend.sh voodoooq/carnivario-frontend:TAG_PROBADO
```

El script monta `/etc/letsencrypt` como solo lectura, valida Nginx antes del cambio, conserva el contenedor anterior y prueba HTTPS. Si falla, lo recupera. No se ha verificado desde este equipo la vigencia del certificado de EC2 ni su renovación.

## Verificación final en la infraestructura

1. Abrir `https://3-212-230-250.sslip.io`: catálogo e imágenes visibles; solicitudes API a Gateway HTTPS.
2. Login local CLIENTE y ADMIN: comprobar `X-Local-Token`, perfil, compra y operaciones administrativas.
3. Login Microsoft: comprobar redirect a Microsoft y regreso al frontend HTTPS; ninguna llamada a `/oauth2/authorization/azure`.
4. En Network, comprobar Bearer access token Azure, sin copiar el token a capturas.
5. Gateway `/auth/azure-check`: 401 sin token o con token inventado y 200 con Azure válido.
6. Gateway `/auth/azure-admin-check`: 403 para CLIENTE y 200 para ADMIN con scope Write.
7. Conservar ambos contenedores anteriores hasta terminar las pruebas y capturas de los pasos 34–46 de la guía.

No se ha rotado ningún secreto ni borrado archivos `.env` existentes. Una rotación requiere coordinar los valores con las instancias que ya están en ejecución.

## Referencias técnicas consultadas

- [Spring Security: validación JWT y audiencia](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html).
- [Microsoft: inicialización y errores de MSAL](https://learn.microsoft.com/en-us/entra/msal/javascript/browser/errors).
- [Microsoft: cambios de MSAL Browser 4 a 5](https://github.com/AzureAD/microsoft-authentication-library-for-js/blob/dev/lib/msal-browser/docs/v4-migration.md).
- [GitHub: uso de secretos en workflows](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).
