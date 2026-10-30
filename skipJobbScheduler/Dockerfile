# Build stage: DHI dev-variant (inneholder shell og pip) for å installere avhengigheter
FROM dhi.io/python:3.15-debian13-dev@sha256:f4559efa9b6dddb0dfb398ac3c5e5c4fb92c67d4037d1b249fdf041db886e10c AS builder

WORKDIR /app

RUN python -m venv --without-pip /app/venv \
    && pip --python /app/venv/bin/python install --no-cache-dir requests

# Runtime stage: minimal DHI runtime-variant (uten shell og pakkehåndterer)
FROM dhi.io/python:3.15-debian13@sha256:b97c8f98d905efe5efb65df6a383e510580fc2f7fe9259f9e1f6f0fa551caaf6

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
