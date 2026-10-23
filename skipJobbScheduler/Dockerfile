# Use an official Python runtime as the base image
FROM python:3.14-slim-trixie@sha256:0514bc76712d64f8bb48adcb95b1ab0d88c69fee95d62fe4c18ee948bee9b1e1

# Link rep to pushed image
LABEL org.opencontainers.image.source=https://github.com/kartverket/matrikkel-keycloak

# Set the working directory in the container
WORKDIR /app

# Copy the current directory contents into the container at /app
COPY . /app

# Install any needed dependencies specified in requirements.txt
RUN pip install --no-cache-dir requests
# Set the timezone to Oslo
ENV TZ=Europe/Oslo

USER 150

# Run the script when the container launches
CMD ["python", "run_jobb.py"]
