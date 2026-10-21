# FASE 9 — RabbitAdmin en la EC2 Back+DB existente

Preparación para ejecución manual; estos comandos **no se han ejecutado en AWS**. No requieren una tercera EC2 ni Compose de producción. No cambiar MySQL, JWT, Azure, el proxy público ni los security groups para publicar RabbitMQ o RabbitAdmin.

## Imagen y publicación

El workflow independiente `.github/workflows/docker-publish-rabbitadmin.yml` ejecuta los tests con Java 17 y un broker efímero, y publica:

```text
<DOCKERHUB_USERNAME>/carnivario-rabbitadmin:latest
<DOCKERHUB_USERNAME>/carnivario-rabbitadmin:<SHA_DEL_COMMIT>
```

Se activa por push a `Develop`, `main`, `feature/auth-hibrida-msal-local` y `feature/ep3-rabbitmq-rabbitadmin`, o manualmente. Todas esas ramas publican la etiqueta SHA; **solo `main` publica además `latest`**, incluso en ejecuciones manuales. Utiliza únicamente los secretos existentes `DOCKERHUB_USERNAME` y `DOCKERHUB_TOKEN`. Las credenciales del broker de CI son desechables, específicas de la ejecución y distintas de las locales. No hay acciones AWS.

El Dockerfile compila con Maven 3.9.11 y Java 17; ejecuta el JAR Spring Boot 4.0.8 con JRE 17 como usuario `10001:10001`. La imagen no contiene credenciales. El build estándar publica para `linux/amd64`; confirmar que la EC2 existente es x86_64 antes de descargarla.

## 1. Preparar la red y las credenciales privadas

Ejecutar por SSH **en la EC2 Back+DB existente**, usando Bash y un usuario autorizado para Docker. Revisar primero los contenedores existentes; si ya existe RabbitMQ, reutilizar su red, volumen, usuarios y nombre DNS en vez de crear otro broker.

```bash
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}'
docker network inspect carnivario-net >/dev/null 2>&1 ||
  docker network create carnivario-net
docker volume create carnivario-rabbitmq-data
```

`carnivario-net` es también la red que utiliza `scripts/deploy-backend.sh`. Los nombres siguientes son para una instalación nueva. `docker run` fallará si ya existen: no borrar ni reemplazar esos contenedores o sus volúmenes para repetir la guía.

Crear los archivos privados fuera del checkout, solo si aún no existen. Este bloque solicita las credenciales sin ponerlas en el historial ni imprimir la contraseña. Utilizar las credenciales del broker existente si se reutiliza un volumen inicializado; las variables de arranque no cambian sus usuarios.

```bash
umask 077
PRIVATE_DIR="$HOME/.config/carnivario"
mkdir -p "$PRIVATE_DIR"
test ! -e "$PRIVATE_DIR/rabbitmq.env" &&
  test ! -e "$PRIVATE_DIR/rabbitadmin.env" || {
    echo "Ya existen archivos privados: revisarlos sin sobrescribirlos."
    exit 1
  }
read -r -p 'Usuario RabbitMQ (distinto de guest): ' RABBITMQ_USER
read -r -s -p 'Contraseña RabbitMQ: ' RABBITMQ_PASSWORD
printf '\n'
test -n "$RABBITMQ_USER" && test "$RABBITMQ_USER" != guest &&
  test -n "$RABBITMQ_PASSWORD" || exit 1
printf 'RABBITMQ_DEFAULT_USER=%s\nRABBITMQ_DEFAULT_PASS=%s\n' \
  "$RABBITMQ_USER" "$RABBITMQ_PASSWORD" > "$PRIVATE_DIR/rabbitmq.env"
printf 'RABBITMQ_HOST=carnivario-rabbitmq\nRABBITMQ_PORT=5672\nRABBITMQ_USER=%s\nRABBITMQ_PASSWORD=%s\n' \
  "$RABBITMQ_USER" "$RABBITMQ_PASSWORD" > "$PRIVATE_DIR/rabbitadmin.env"
chmod 600 "$PRIVATE_DIR/rabbitmq.env" "$PRIVATE_DIR/rabbitadmin.env"
unset RABBITMQ_USER RABBITMQ_PASSWORD
```

No copiar estos archivos a Git ni a contextos Docker. No usar `set -x`, listar variables ni compartir un `docker inspect` completo: Docker conserva las variables del contenedor y los operadores del daemon pueden leerlas.

## 2. Arrancar RabbitMQ con persistencia

```bash
PRIVATE_DIR="$HOME/.config/carnivario"
docker pull rabbitmq:4.2-management
docker run -d --name carnivario-rabbitmq \
  --hostname carnivario-rabbitmq \
  --restart unless-stopped \
  --network carnivario-net \
  --env-file "$PRIVATE_DIR/rabbitmq.env" \
  --mount source=carnivario-rabbitmq-data,target=/var/lib/rabbitmq \
  -p 127.0.0.1:15672:15672 \
  --health-cmd 'rabbitmq-diagnostics -q ping' \
  --health-interval 10s --health-timeout 5s --health-retries 12 \
  rabbitmq:4.2-management
docker inspect --format '{{.State.Health.Status}}' carnivario-rabbitmq
```

Esperar `healthy` antes de continuar. AMQP 5672 queda accesible únicamente por la red Docker, sin publicación en el host. Management 15672 se publica solo en loopback. La etiqueta `4.2-management` puede cambiar de parche; no elimina ni migra deliberadamente datos existentes.

## 3. Descargar y arrancar RabbitAdmin

Una vez autorizado y completado el push/publicación, indicar el usuario Docker Hub y preferiblemente la etiqueta SHA publicada. Si el repositorio es privado, ejecutar antes `docker login` de forma interactiva.

```bash
read -r -p 'Usuario Docker Hub: ' DOCKERHUB_USERNAME
read -r -p 'Etiqueta SHA publicada (o latest): ' IMAGE_TAG
test -n "$DOCKERHUB_USERNAME" && test -n "$IMAGE_TAG" || exit 1
ADMIN_IMAGE="${DOCKERHUB_USERNAME}/carnivario-rabbitadmin:${IMAGE_TAG}"
docker pull "$ADMIN_IMAGE"
docker run -d --name carnivario-rabbitadmin \
  --restart unless-stopped \
  --network carnivario-net \
  --env-file "$PRIVATE_DIR/rabbitadmin.env" \
  -p 127.0.0.1:8082:8082 \
  "$ADMIN_IMAGE"
docker logs --tail 60 carnivario-rabbitadmin
```

Dentro del contenedor, `SERVER_ADDRESS=0.0.0.0` permite recibir tráfico de su red Docker. La publicación `127.0.0.1:8082:8082` restringe el acceso desde el host. No utilizar `-p 8082:8082`, `--network host`, un proxy público ni reglas de ingreso públicas para 8082, 5672 o 15672. La API no incorpora autenticación: todos los contenedores de la red compartida deben ser de confianza.

Fuera de Docker, los defaults continúan siendo `127.0.0.1:8082`. `SERVER_ADDRESS` y `SERVER_PORT` permiten overrides explícitos; `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER` y `RABBITMQ_PASSWORD` controlan la conexión. Un `.env` de Compose no se carga automáticamente en Java.

## 4. Conectar CarniveroCrud

Comprobar si el backend ya pertenece a la red, y conectarlo solo si falta. Esto no modifica MySQL ni sus redes:

```bash
docker inspect --format '{{json .NetworkSettings.Networks}}' CarniveroCrud
# Ejecutar solo si carnivario-net no aparece:
docker network connect carnivario-net CarniveroCrud
```

En el archivo **privado existente** `backend.env`, conservar todas las variables actuales de base de datos, JWT, Azure y correo. Añadir o actualizar, sin duplicarlas:

| Variable | Valor |
|---|---|
| `RABBITMQ_HOST` | `carnivario-rabbitmq` |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USER` | El usuario privado del mismo broker |
| `RABBITMQ_PASSWORD` | Su contraseña privada |

Un `docker restart` no aplica nuevas variables. Para el despliegue estándar existente, desde el checkout en EC2, usar el script manual que ya carga `backend.env` y conserva el contenedor anterior y `uploads`. Seleccionar una imagen del backend que incluya las fases de mensajería y comprobar antes que la topología ya exista:

```bash
BACKEND_ENV=/home/ec2-user/backend.env
test -r "$BACKEND_ENV"
chmod 600 "$BACKEND_ENV"
read -r -p 'Imagen backend publicada con las fases de mensajería: ' BACKEND_IMAGE
test -n "$BACKEND_IMAGE" || exit 1
sudo bash scripts/deploy-backend.sh "$BACKEND_IMAGE" "$BACKEND_ENV"
```

Este paso es un despliegue manual futuro y reinicia el consumidor real; requiere que las credenciales y dependencias actuales estén disponibles. Si el contenedor existente usa volúmenes, redes u opciones adicionales al despliegue estándar, conservarlas en su procedimiento original antes de recrearlo. No ejecutar `deploy-backend.py` ni el workflow antiguo de despliegue para este paso: actualmente no transmiten las variables RabbitMQ. No se modificaron esos archivos.

## 5. Verificar sin consumir mensajes

```bash
docker inspect --format '{{.State.Health.Status}}' carnivario-rabbitmq
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker exec carnivario-rabbitmq rabbitmqctl list_exchanges -p / name type durable
docker exec carnivario-rabbitmq rabbitmqctl list_queues -p / name durable messages consumers arguments
docker exec carnivario-rabbitmq rabbitmqctl list_bindings -p / source_name destination_name routing_key
docker exec carnivario-rabbitmq rabbitmqctl list_connections name user vhost state
curl --fail --silent http://127.0.0.1:8081/planta/api >/dev/null
```

Deben existir `carnivario.eventos`, `carnivario.dlx`, `notificaciones.confirmacion.queue`, `notificaciones.confirmacion.dlq` y los tres bindings explícitos documentados en el [README](README_EP3.md). La cola principal conserva sus argumentos DLX originales. Con el backend iniciado debe aparecer su consumidor/conexión; RabbitAdmin también establece una conexión administrativa.

Prueba REST sobre una cola temporal propia, sin publicar mensajes ni tocar recursos de negocio:

```bash
DEMO_QUEUE="demo.aws.check.$(date +%s).$$"
curl --fail-with-body -sS -o /dev/null -w 'POST %{http_code}\n' \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"$DEMO_QUEUE\",\"durable\":true}" \
  http://127.0.0.1:8082/api/rabbit/queues &&
curl --fail-with-body -sS \
  "http://127.0.0.1:8082/api/rabbit/queues/$DEMO_QUEUE" &&
curl --fail-with-body -sS -o /dev/null -w '\nDELETE %{http_code}\n' \
  -X DELETE "http://127.0.0.1:8082/api/rabbit/queues/$DEMO_QUEUE"
```

Esperar 201, una consulta 200 con cero mensajes, y 204. No enviar compras reales para esta comprobación. Para acceder desde un equipo autorizado, utilizar túneles SSH al loopback de la EC2; no abrir puertos públicos.

## Validación local de esta preparación

Resultados del 20/10/2026:

- `mvn -B clean test`: 77 pruebas exitosas, cero fallos, errores u omisiones.
- `mvn -B -DskipTests compile`: `BUILD SUCCESS`.
- Imagen local `carnivario-rabbitadmin:phase9-check` construida y arrancada con Spring Boot 4.0.8, Java 17 y usuario `10001:10001`.
- Conexión real al broker por DNS Docker; API publicada exclusivamente en `127.0.0.1:8082`.
- Cola temporal propia: POST 201, GET 200, DELETE 204 y posterior GET 404. Consulta de cola protegida: 403.
- Broker healthy; conservados los dos exchanges, dos colas y tres bindings originales. Los cuatro mensajes de la DLQ aislada de FASE 6 siguen presentes.
- Contextos Docker de los tres proyectos comprobados; el workflow nuevo pasó actionlint. Los workflows anteriores permanecen sin modificaciones.
- Contenedor temporal retirado; imagen local conservada. No se publicaron imágenes ni se hicieron commits o despliegues.

Estas pruebas no equivalen a una ejecución del workflow en GitHub ni a un despliegue AWS. El archivo ya versionado `backend/bin/src/main/resources/application.yml` requiere revisión privada de posibles secretos antes del push: permanece en el índice por instrucción expresa, aunque `backend/bin/` está excluido de Docker.
