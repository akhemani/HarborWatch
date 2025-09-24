#!/usr/bin/env sh
set -e

# Render from template using container env vars
if [ ! -f /etc/alertmanager/alertmanager.yml.tmpl ]; then
  echo "Template missing: /etc/alertmanager/alertmanager.yml.tmpl" >&2
  exit 1
fi

envsubst < /etc/alertmanager/alertmanager.yml.tmpl > /etc/alertmanager/alertmanager.yml
echo "[am] Rendered /etc/alertmanager/alertmanager.yml"
echo "----- rendered config (first lines) -----"
head -n 20 /etc/alertmanager/alertmanager.yml || true
echo "-----------------------------------------"

# Run Alertmanager
exec /bin/alertmanager \
  --config.file=/etc/alertmanager/alertmanager.yml \
  --storage.path=/alertmanager
