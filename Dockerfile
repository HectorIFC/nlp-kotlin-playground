# syntax=docker/dockerfile:1.7

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy Gradle wrapper + build scripts first to leverage layer cache when only sources change.
COPY gradlew gradlew.bat ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY config config

# Warm the wrapper distribution cache (download once, before COPY src).
RUN ./gradlew --no-daemon --version

# Pre-warm dependency cache before COPY src: if JitPack is slow or
# briefly down, the build still has Tessera/Mosaic in the local cache from this
# step on subsequent builds, and source-only changes don't invalidate the cache.
# `|| true` so a transient resolution failure doesn't kill the build outright.
RUN ./gradlew --no-daemon dependencies --refresh-dependencies || true

# Now copy the rest and build.
COPY src src
RUN ./gradlew --no-daemon installDist

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-jammy

LABEL org.opencontainers.image.title="nlp-kotlin-playground"
LABEL org.opencontainers.image.description="Interactive playground demonstrating the Tessera + Mosaic NLP pipeline in Kotlin."
LABEL org.opencontainers.image.source="https://github.com/HectorIFC/nlp-kotlin-playground"
LABEL org.opencontainers.image.licenses="MIT"
LABEL org.opencontainers.image.version="0.1.0"

# wget is needed by the HEALTHCHECK below; jre-jammy doesn't ship with it.
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user. The system account `nobody` already exists in
# the base image; we make sure /app and /data (SQLite volume mount point) are
# owned by it so the JVM can read app files and write SQLite WAL/db files
# without elevated privileges.
WORKDIR /app
COPY --from=builder --chown=nobody:nogroup /build/build/install/nlp-kotlin-playground /app/
RUN mkdir -p /data && chown -R nobody:nogroup /data

USER nobody:nogroup

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["/app/bin/nlp-kotlin-playground"]
