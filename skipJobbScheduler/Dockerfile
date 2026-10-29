# Build stage: DHI dev-variant (inneholder shell og pip) for å installere avhengigheter
FROM dhi.io/python:3.14-debian13-dev@sha256:d596be714808bdbaff280777fb66bd0d018c07a60b2f04558960900690ddb438 AS builder

WORKDIR /app

RUN python -m venv --without-pip /app/venv \
    && pip --python /app/venv/bin/python install --no-cache-dir requests

# Runtime stage: minimal DHI runtime-variant (uten shell og pakkehåndterer)
FROM dhi.io/python:3.14-debian13@sha256:3b96bee5f18a1978b7093b46f9320b9f55e266339ac75d6fca5ebe0328c553c6

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
