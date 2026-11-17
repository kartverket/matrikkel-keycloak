# Build stage: DHI dev-variant (inneholder shell og pip) for å installere avhengigheter
FROM dhi.io/python:3.15-debian13-dev@sha256:98add6f52fc7dca59a55fc0618b6d40266922b0b8482f017c082a3dbe4ab89ab AS builder

WORKDIR /app

RUN python -m venv --without-pip /app/venv \
    && pip --python /app/venv/bin/python install --no-cache-dir requests

# Runtime stage: minimal DHI runtime-variant (uten shell og pakkehåndterer)
FROM dhi.io/python:3.15-debian13@sha256:5f3efbd860ff18cf93c93e5812e300a17e6e06d35be4f973581f3129addeccac

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
