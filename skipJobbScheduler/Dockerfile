# Use an official Python runtime as the base image
FROM python:3.9-slim

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

# Run the script when the container launches
CMD ["python", "run_jobb.py"]
