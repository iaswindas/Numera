FROM python:3.11-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    sqlite3 && \
    rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir mlflow==2.18.0

WORKDIR /mlflow
EXPOSE 5000

CMD ["mlflow", "server", "--backend-store-uri", "sqlite:////mlflow/mlflow.db", "--default-artifact-root", "/mlflow/artifacts", "--host", "0.0.0.0", "--port", "5000"]
