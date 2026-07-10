FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1

RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    libsndfile1 \
    build-essential \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /worker

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

RUN mkdir -p /var/log/hearintent/sessions

EXPOSE 50051

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD python -c "import grpc; from app.generated import speech_pb2, speech_pb2_grpc; \
    channel = grpc.insecure_channel('localhost:50051'); \
    stub = speech_pb2_grpc.SpeechIntelligenceServiceStub(channel); \
    stub.HealthCheck(speech_pb2.HealthCheckRequest(requester='docker-healthcheck'), timeout=5)" \
    || exit 1

CMD ["python", "-m", "app.main"]
