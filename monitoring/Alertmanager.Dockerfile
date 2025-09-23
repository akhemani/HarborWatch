# Stage 1: fetch Alertmanager artifacts
FROM alpine:3.19 AS fetch
ARG AM_VERSION=0.27.0
RUN apk add --no-cache wget tar ca-certificates
RUN wget -q https://github.com/prometheus/alertmanager/releases/download/v${AM_VERSION}/alertmanager-${AM_VERSION}.linux-amd64.tar.gz \
 && tar -xzf alertmanager-${AM_VERSION}.linux-amd64.tar.gz \
 && mv alertmanager-${AM_VERSION}.linux-amd64 /am

# Stage 2: slim runtime with envsubst + non-root user
FROM alpine:3.19
RUN apk add --no-cache ca-certificates gettext \
 # create a dedicated user without hardcoding UID (avoid clash with 65534/nobody)
 && adduser -D -H alertmgr \
 # create and chown config + storage dirs so non-root can write rendered config and data
 && mkdir -p /etc/alertmanager /alertmanager \
 && chown -R alertmgr:alertmgr /etc/alertmanager /alertmanager

# Binaries
COPY --from=fetch /am/alertmanager /bin/alertmanager
COPY --from=fetch /am/amtool       /bin/amtool

# Template + startup script
COPY alertmanager.yml.tmpl /etc/alertmanager/alertmanager.yml.tmpl
COPY alertmanager-run.sh   /usr/local/bin/alertmanager-run.sh
RUN chmod +x /usr/local/bin/alertmanager-run.sh

USER alertmgr
EXPOSE 9093
ENTRYPOINT ["/usr/local/bin/alertmanager-run.sh"]
