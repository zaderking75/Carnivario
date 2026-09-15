#!/usr/bin/env bash
# Ejecutar en EC2: sudo bash scripts/deploy-backend.sh IMAGEN /home/ec2-user/backend.env
set -Eeuo pipefail
umask 077

image=${1:?Indica la imagen del backend}
env_file=${2:?Indica el archivo privado backend.env}
name=CarniveroCrud
backup="${name}-old-$(date +%Y%m%d%H%M%S)"
previous=false
replacing=false

test -r "$env_file"
for key in DB_HOST DB_USERNAME DB_PASSWORD JWT_SECRET AZURE_TENANT_ID MAIL_USERNAME MAIL_PASSWORD; do
    if ! grep -qE "^${key}=.+" "$env_file"; then
        echo "Falta $key en backend.env (formato NOMBRE=valor)." >&2
        exit 1
    fi
done
docker network inspect carnivario-net >/dev/null
docker pull "$image"

rollback() {
    result=$?
    trap - ERR
    if "$replacing"; then
        failure_log="/var/tmp/${name}-failed-$(date +%Y%m%d%H%M%S).log"
        docker logs "$name" > "$failure_log" 2>&1 || true
        echo "Logs del arranque fallido: $failure_log" >&2
        docker rm -f "$name" >/dev/null 2>&1 || true
        if "$previous"; then
            docker rename "$backup" "$name"
            docker start "$name" >/dev/null
            echo "Se restauro el contenedor anterior." >&2
        fi
    fi
    exit "$result"
}
trap rollback ERR

if docker container inspect "$name" >/dev/null 2>&1; then
    old_workdir=$(docker inspect --format '{{.Config.WorkingDir}}' "$name")
    docker rename "$name" "$backup"
    previous=true
    replacing=true
    docker stop "$backup" >/dev/null
fi
replacing=true

docker create --name "$name" --restart always --network carnivario-net \
    -p 8081:8081 --env-file "$env_file" "$image" >/dev/null

# Las imagenes subidas viven en uploads; conservarlas al reemplazar el contenedor.
if "$previous"; then
    docker cp "$backup:${old_workdir%/}/uploads/." - | docker cp - "$name:/app/uploads"
fi
docker start "$name" >/dev/null

ready=false
for attempt in $(seq 1 30); do
    if curl --fail --silent --max-time 3 http://localhost:8081/planta/api >/dev/null; then
        ready=true
        break
    fi
    sleep 2
done
if ! "$ready"; then
    echo "El backend no respondio correctamente; se recuperara el anterior." >&2
    false
fi
echo "Backend disponible en puerto 8081."
if "$previous"; then
    echo "Respaldo conservado: $backup"
fi
