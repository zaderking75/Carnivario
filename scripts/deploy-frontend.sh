#!/usr/bin/env bash
# Ejecutar en la EC2 frontend: sudo bash scripts/deploy-frontend.sh IMAGEN
set -Eeuo pipefail
umask 077

image=${1:?Indica la imagen del frontend}
name=carnivero
backup="${name}-old-$(date +%Y%m%d%H%M%S)"
domain=3-212-230-250.sslip.io
previous=false
replacing=false

test -r "/etc/letsencrypt/live/$domain/fullchain.pem"
test -r "/etc/letsencrypt/live/$domain/privkey.pem"
docker pull "$image"
docker run --rm -v /etc/letsencrypt:/etc/letsencrypt:ro "$image" nginx -t

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
            echo "Se restauro el frontend anterior." >&2
        fi
    fi
    exit "$result"
}
trap rollback ERR

if docker container inspect "$name" >/dev/null 2>&1; then
    docker rename "$name" "$backup"
    previous=true
    replacing=true
    docker stop "$backup" >/dev/null
fi
replacing=true
docker run -d --name "$name" --restart always -p 80:80 -p 443:443 \
    -v /etc/letsencrypt:/etc/letsencrypt:ro "$image" >/dev/null

ready=false
for attempt in $(seq 1 15); do
    if curl --fail --silent --max-time 3 --resolve "$domain:443:127.0.0.1" "https://$domain/" >/dev/null; then
        ready=true
        break
    fi
    sleep 2
done
if ! "$ready"; then
    echo "El frontend no respondio por HTTPS; se recuperara el anterior." >&2
    false
fi
echo "Frontend disponible: https://$domain"
if "$previous"; then
    echo "Respaldo conservado: $backup"
fi
