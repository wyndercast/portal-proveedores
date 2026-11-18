#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "No existe ${ENV_FILE}. Copia .env.example a .env y completa solo las variables necesarias." >&2
  exit 1
fi

while IFS= read -r line || [[ -n "${line}" ]]; do
  [[ -z "${line//[[:space:]]/}" ]] && continue
  [[ "${line}" =~ ^[[:space:]]*# ]] && continue
  if [[ ! "${line}" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]]; then
    echo "Línea inválida en .env; se esperaba NOMBRE=VALOR." >&2
    exit 1
  fi
  export "${line}"
done < "${ENV_FILE}"

export SPRING_PROFILES_ACTIVE="local"
exec "${ROOT_DIR}/mvnw" -B spring-boot:run
