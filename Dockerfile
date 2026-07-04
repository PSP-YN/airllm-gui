# AirLLM Python Backend Docker Image
FROM python:3.10-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    git \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements
COPY requirements.txt .
COPY air_llm/setup.py air_llm/

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt
RUN cd air_llm && pip install -e .

# Set environment variables
ENV AIRLLM_CACHE_DIR=/app/.cache/huggingface
ENV AIRLLM_LOG_LEVEL=INFO
ENV PYTHONUNBUFFERED=1

# Create cache directory
RUN mkdir -p /app/.cache/huggingface

# Copy example script
COPY run_coding_model.py .

# Expose port (if running as API server)
# EXPOSE 8000

# Default command
CMD ["python", "run_coding_model.py"]
