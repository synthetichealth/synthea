# =====================================
# Synthea official development container
# =====================================

# Use a lightweight Java image
FROM openjdk:21-jdk-slim AS base

# Set working directory
WORKDIR /app

# Install dependencies: git, gradle, curl, vim (optional)
RUN apt-get update && apt-get install -y \
    git \
    gradle \
    vim \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy all source code into the container
COPY . /app

# Build synthea using gradle
RUN ./gradlew build --no-daemon

# Default command when container runs:
# generate patients (10 by default)
CMD ["./run_synthea"]

# --- For developers ---
# You can override CMD to run interactively:
# docker run -it --rm -v $(pwd):/app synthea-dev bash
