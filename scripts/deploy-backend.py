"""Entrega los secretos al Docker remoto por SSH/STDIN, sin incluirlos en la imagen."""
import os
from pathlib import Path
import re
import shlex
import subprocess
import tempfile


def main():
    required = ["EC2_HOST", "EC2_USERNAME", "EC2_SSH_KEY", "EC2_KNOWN_HOSTS", "BACKEND_IMAGE",
                "DB_HOST", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "AZURE_TENANT_ID",
                "MAIL_USERNAME", "MAIL_PASSWORD"]
    missing = [name for name in required if not os.environ.get(name)]
    if missing:
        raise SystemExit("Faltan secretos/configuracion: " + ", ".join(missing))
    host, user = os.environ["EC2_HOST"], os.environ["EC2_USERNAME"]
    if not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9.-]*", host) or not re.fullmatch(r"[a-z_][a-z0-9_-]*", user):
        raise SystemExit("EC2_HOST o EC2_USERNAME no tiene un formato valido")
    keys = ["DB_HOST", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "AZURE_TENANT_ID",
            "MAIL_USERNAME", "MAIL_PASSWORD"]
    values = {name: os.environ[name] for name in keys}
    for name, value in values.items():
        if any(char in value for char in ("\n", "\r", "\0")):
            raise SystemExit("Valor con saltos de linea no admitidos: " + name)
    if "${" in values["DB_HOST"]:
        raise SystemExit("DB_HOST contiene un marcador sin resolver")

    env_text = "".join(f"{name}={value}\n" for name, value in values.items())
    deploy_script = Path(__file__).with_suffix(".sh").read_text(encoding="utf-8")
    # El contenido va por stdin de SSH; shlex.quote conserva $, comillas y espacios.
    payload = "set -e\numask 077\nauth_env=$(mktemp)\ntrap 'rm -f -- \"$auth_env\"' EXIT\n"
    payload += "printf %s " + shlex.quote(env_text) + ' > "$auth_env"\n'
    payload += "set -- " + shlex.quote(os.environ["BACKEND_IMAGE"]) + ' "$auth_env"\n'
    payload += deploy_script
    with tempfile.TemporaryDirectory(prefix="carnivario-ssh-") as temporary:
        private_key = Path(temporary) / "key"
        known_hosts = Path(temporary) / "known_hosts"
        private_key.write_text(os.environ["EC2_SSH_KEY"] + "\n", encoding="utf-8")
        private_key.chmod(0o600)
        known_hosts.write_text(os.environ["EC2_KNOWN_HOSTS"] + "\n", encoding="utf-8")
        command = ["ssh", "-i", str(private_key), "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes",
                   "-o", "StrictHostKeyChecking=yes", "-o", f"UserKnownHostsFile={known_hosts}",
                   "-o", "ConnectTimeout=15", f"{user}@{host}", "sudo -n bash -s"]
        subprocess.run(command, input=payload, text=True, check=True)


if __name__ == "__main__":
    main()
