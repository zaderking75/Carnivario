"""Prueba las imagenes locales con MySQL desechable; no utiliza backend/.env."""
import base64
import json
import os
import secrets
import subprocess
import time
import urllib.error
import urllib.request


def docker(*args, env=None):
    result = subprocess.run(["docker", *args], env=env, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("Fallo Docker: " + result.stderr.strip())
    return result.stdout.strip()


def request(base, path, expected, method="GET", data=None, headers=None):
    headers = dict(headers or {})
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, method=method, headers=headers,
                                 data=json.dumps(data).encode() if data is not None else None)
    try:
        response = urllib.request.urlopen(req, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        status, content = response.status, response.read()
    if status != expected:
        raise AssertionError(f"{method} {path}: esperado {expected}, obtenido {status}")
    print(f"OK {method} {path}: {status}", flush=True)
    return json.loads(content) if content else None


def main():
    suffix = secrets.token_hex(4)
    network, db, backend = [f"carnivario-smoke-{name}-{suffix}" for name in ("net", "db", "api")]
    task_env = dict(os.environ, MYSQL_ROOT_PASSWORD=secrets.token_urlsafe(32),
                    MYSQL_DATABASE="carnivario", MYSQL_USER="carnivario", MYSQL_PASSWORD=secrets.token_urlsafe(32))
    task_env.update(DB_HOST=db, DB_USERNAME="carnivario", DB_PASSWORD=task_env["MYSQL_PASSWORD"],
                    JWT_SECRET=base64.b64encode(secrets.token_bytes(64)).decode(),
                    AZURE_TENANT_ID="3b914fe6-6475-4be6-8639-9de64b6ba898",
                    MAIL_USERNAME="test@example.invalid", MAIL_PASSWORD="test-only")
    created_network = False
    try:
        docker("network", "create", network)
        created_network = True
        docker("run", "-d", "--name", db, "--network", network,
               "-e", "MYSQL_ROOT_PASSWORD", "-e", "MYSQL_DATABASE", "-e", "MYSQL_USER", "-e", "MYSQL_PASSWORD",
               "mysql:8", env=task_env)
        for _ in range(60):
            logs = docker("logs", db)
            if "port: 3306" in logs:
                break
            # MySQL registra parte de su arranque en stderr; comprobar por comando.
            result = subprocess.run(["docker", "exec", db, "mysqladmin", "ping", "--host=127.0.0.1", "--silent"], capture_output=True)
            if result.returncode == 0:
                time.sleep(3)
                break
            time.sleep(2)
        env_args = [arg for key in ("DB_HOST", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "AZURE_TENANT_ID",
                                    "MAIL_USERNAME", "MAIL_PASSWORD") for arg in ("-e", key)]
        docker("run", "-d", "--name", backend, "--network", network, "-p", "127.0.0.1::8081",
               *env_args, "carnivario-backend:hybrid-auth", env=task_env)
        started = time.monotonic()
        port = docker("port", backend, "8081/tcp").rsplit(":", 1)[1]
        base = f"http://127.0.0.1:{port}"
        for _ in range(45):
            try:
                with urllib.request.urlopen(base + "/planta/api", timeout=2) as response:
                    if response.status == 200:
                        break
            except (OSError, urllib.error.URLError):
                time.sleep(2)
        request(base, "/planta/api", 200)
        request(base, "/user/api/me", 401)
        request(base, "/purchase/api", 401, "POST", {})
        request(base, "/user/api/register", 201, "POST", {
            "email": "smoke@example.test", "password": "test-local-password", "name": "Smoke", "lastname": "Test",
            "phone": "123", "address": "Test", "commune": "Test", "role": "ADMIN"})
        session = request(base, "/user/api/login", 200, "POST", {
            "email": "smoke@example.test", "password": "test-local-password"})
        assert session["user"]["role"] == "CLIENTE"
        headers = {"X-Local-Token": session["token"]}
        request(base, "/user/api/me", 200, headers=headers)
        request(base, "/planta/api", 403, "POST", {}, headers)
        request(base, "/auth/azure-check", 403, headers=headers)
        task_env["MYSQL_PWD"] = task_env["MYSQL_ROOT_PASSWORD"]
        docker("exec", "-e", "MYSQL_PWD", db, "mysql", "-uroot", "carnivario", "-e",
               "UPDATE usuario SET role='ADMIN' WHERE email='smoke@example.test';", env=task_env)
        request(base, "/user/api", 200, headers=headers)
        time.sleep(max(0, 15 - (time.monotonic() - started)))
        assert docker("inspect", "--format", "{{.State.Running}}", backend) == "true"
        print("OK backend activo durante al menos 15 segundos con MySQL real de prueba", flush=True)
    finally:
        for container in (backend, db):
            subprocess.run(["docker", "rm", "-f", "-v", container], capture_output=True)
        if created_network:
            subprocess.run(["docker", "network", "rm", network], capture_output=True)


if __name__ == "__main__":
    main()
