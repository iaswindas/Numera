"""Storage client — abstracts MinIO/S3 and local filesystem access.

For demo/development, set OCR_LOCAL_STORAGE_PATH to read files from a local
directory. In production, files are read from MinIO via boto3.
"""

import logging
from pathlib import Path

logger = logging.getLogger("ocr-service.services.storage")


class StorageClient:
    """Unified file access: MinIO in production, local fallback for demo."""

    def __init__(self, settings):
        self._local_path = settings.local_storage_path
        self._s3_client = None

        if not self._local_path:
            try:
                import boto3
                from botocore.config import Config

                self._s3_client = boto3.client(
                    "s3",
                    endpoint_url=f"http{'s' if settings.minio_secure else ''}://{settings.minio_endpoint}",
                    aws_access_key_id=settings.minio_access_key,
                    aws_secret_access_key=settings.minio_secret_key,
                    config=Config(signature_version="s3v4"),
                    region_name="us-east-1",  # MinIO default
                )
                self._bucket = settings.minio_bucket
                logger.info("MinIO client ready (endpoint=%s)", settings.minio_endpoint)
            except Exception:
                logger.exception("Failed to initialise MinIO client")
        else:
            logger.info("Using local storage fallback: %s", self._local_path)

    async def download(self, path: str) -> bytes:
        """Download a file by its storage path.

        Args:
            path: Object key in MinIO, or relative path when using local fallback.

        Returns:
            Raw file bytes.

        Raises:
            FileNotFoundError: If the file doesn't exist.
        """
        # --- Local fallback ---
        if self._local_path:
            root = Path(self._local_path).resolve()
            local_file = (root / path).resolve()
            if not str(local_file).startswith(str(root)):
                raise FileNotFoundError("Invalid storage path")
            if not local_file.exists():
                raise FileNotFoundError(f"Local file not found: {local_file}")
            return local_file.read_bytes()

        # --- MinIO / S3 ---
        if self._s3_client is None:
            raise RuntimeError("MinIO client not initialised and no local fallback configured")

        bucket = self._bucket
        object_key = path
        if "/" in path:
            bucket_prefix, remainder = path.split("/", 1)
            if bucket_prefix and remainder:
                bucket = bucket_prefix
                object_key = remainder

        try:
            response = self._s3_client.get_object(
                Bucket=bucket, Key=object_key
            )
            body = response["Body"]
            try:
                return body.read()
            finally:
                body.close()
        except self._s3_client.exceptions.NoSuchKey:
            raise FileNotFoundError(f"Object not found in MinIO: {path}")
        except Exception as exc:
            raise RuntimeError(f"MinIO download failed: {exc}") from exc

    async def upload(self, path: str, data: bytes, content_type: str = "application/octet-stream"):
        """Upload raw bytes to storage."""
        if self._local_path:
            local_file = Path(self._local_path) / path
            local_file.parent.mkdir(parents=True, exist_ok=True)
            local_file.write_bytes(data)
            return

        if self._s3_client is None:
            raise RuntimeError("MinIO client not initialised")

        self._s3_client.put_object(
            Bucket=self._bucket,
            Key=path,
            Body=data,
            ContentType=content_type,
        )
