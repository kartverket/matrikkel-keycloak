# Build stage: DHI dev-variant (inneholder shell og pip) for å installere avhengigheter
FROM dhi.io/python:3.15-debian13-dev@sha256:3e432059c740e935dab8baac6baafb9c611315ace74cd283c86ac9e11e296c23 AS builder

WORKDIR /app

RUN python -m venv --without-pip /app/venv \
    && pip --python /app/venv/bin/python install --no-cache-dir requests

# Runtime stage: minimal DHI runtime-variant (uten shell og pakkehåndterer)
FROM dhi.io/python:3.15-debian13@sha256:0f22f5388fbb8e2b6457c4f0b9964d99000a288bae0f845e0f5989ed7033c7bf

# Link rep to pushed image
LABEL org.opencontainers.image.source=https://github.com/kartverket/matrikkel-keycloak

WORKDIR /app

COPY --from=builder /app/venv /app/venv
COPY run_jobb.py /app/run_jobb.py

ENV PATH="/app/venv/bin:$PATH"
# Set the timezone to Oslo
ENV TZ=Europe/Oslo

USER nonroot

# Run the script when the container launches
CMD ["python", "run_jobb.py"]
