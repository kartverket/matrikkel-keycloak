# Build stage: DHI dev-variant (inneholder shell og pip) for å installere avhengigheter
FROM dhi.io/python:3.15-debian13-dev@sha256:646b70e80c246911c8e31f732a8ba0c014c90abef5a1459a9c9f8a2ca0227bb1 AS builder

WORKDIR /app

RUN python -m venv --without-pip /app/venv \
    && pip --python /app/venv/bin/python install --no-cache-dir requests

# Runtime stage: minimal DHI runtime-variant (uten shell og pakkehåndterer)
FROM dhi.io/python:3.15-debian13@sha256:d90203ad5dd6c0d9fe58efd6347795375b3f5f2430be2bf61573510452f718e5

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
