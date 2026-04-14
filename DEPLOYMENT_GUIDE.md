# Numera Deployment Guide — Zero to Production

> **Audience:** First-time deployer with no prior infrastructure experience.
> **Architecture:** ML training on RunPod → ML/OCR hosting on HuggingFace Spaces → App hosting on Oracle Cloud Free Tier.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Prerequisites](#2-prerequisites)
3. [Phase 1 — Train ML Models on RunPod](#3-phase-1--train-ml-models-on-runpod)
4. [Phase 2 — Host ML & OCR on HuggingFace Spaces](#4-phase-2--host-ml--ocr-on-huggingface-spaces)
5. [Phase 3 — Deploy App on Oracle Cloud Free Tier](#5-phase-3--deploy-app-on-oracle-cloud-free-tier)
6. [Phase 4 — Connect Everything](#6-phase-4--connect-everything)
7. [Phase 5 — DNS, SSL & Go Live](#7-phase-5--dns-ssl--go-live)
8. [Maintenance & Monitoring](#8-maintenance--monitoring)
9. [Cost Summary](#9-cost-summary)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         NUMERA PRODUCTION ARCHITECTURE                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐    ONE-TIME      ┌───────────────────────────────────┐    │
│  │   RunPod      │   TRAINING      │    HuggingFace Spaces             │    │
│  │   (GPU)       │ ─────────────>  │    (Free / $0-9/mo)              │    │
│  │              │   Export models  │                                   │    │
│  │  • LayoutLM  │                  │  ┌─────────────┐ ┌────────────┐  │    │
│  │  • SBERT     │                  │  │ OCR Service  │ │ ML Service │  │    │
│  │  • Qwen3-VL  │                  │  │ (Port 7860)  │ │ (Port 7860)│  │    │
│  │  • GNN       │                  │  └──────┬───────┘ └─────┬──────┘  │    │
│  └──────────────┘                  │         │               │         │    │
│                                    └─────────┼───────────────┼─────────┘    │
│                                              │               │              │
│                                              ▼               ▼              │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │              Oracle Cloud Always-Free Tier                           │   │
│  │                                                                      │   │
│  │  ┌───────────┐  ┌──────────┐  ┌─────────┐  ┌───────┐  ┌─────────┐  │   │
│  │  │  Next.js   │  │ Spring   │  │ Postgres│  │ Redis │  │  MinIO  │  │   │
│  │  │ Frontend   │  │ Backend  │  │  (DB)   │  │(Cache)│  │(Storage)│  │   │
│  │  │ :3000      │  │ :8080    │  │ :5432   │  │ :6379 │  │ :9000   │  │   │
│  │  └───────────┘  └──────────┘  └─────────┘  └───────┘  └─────────┘  │   │
│  │                                                                      │   │
│  │  ARM Ampere A1 (4 OCPU, 24 GB RAM) — Always Free                   │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Cost Summary:**
| Component | Cost |
|-----------|------|
| RunPod (training, one-time) | ~$5-15 |
| HuggingFace Spaces (free tier) | $0 |
| Oracle Cloud (Always Free) | $0 |
| Domain name (optional) | ~$10/year |
| **Total ongoing** | **$0/month** |

---

## 2. Prerequisites

Before you start, you'll need:

- [ ] A computer with a web browser and terminal (Mac/Linux/Windows with WSL)
- [ ] A GitHub account (to host code) — sign up at https://github.com
- [ ] `git` installed locally — verify: `git --version`
- [ ] `docker` and `docker compose` installed — verify: `docker --version`
- [ ] A credit/debit card (for Oracle Cloud sign-up; you will NOT be charged)
- [ ] About 2-4 hours for the full setup

### Install Docker (if not installed)

**Mac:**
```bash
# Download Docker Desktop from https://www.docker.com/products/docker-desktop/
# Open the .dmg and drag Docker to Applications
# Launch Docker Desktop and wait for it to start
```

**Ubuntu/Debian Linux:**
```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin
sudo usermod -aG docker $USER
# Log out and back in for group changes to take effect
```

**Windows (WSL2):**
```bash
# 1. Install WSL2: Open PowerShell as Admin
wsl --install

# 2. Download Docker Desktop from https://www.docker.com/products/docker-desktop/
# 3. Enable WSL2 integration in Docker Desktop Settings → Resources → WSL Integration
```

---

## 3. Phase 1 — Train ML Models on RunPod

> **What:** Train 4 ML models on a rented GPU server. Takes 2-4 hours of GPU time.
> **Cost:** ~$5-15 one-time.

### Step 1: Sign Up for RunPod

1. Go to https://www.runpod.io
2. Click **Sign Up** → use your email or GitHub account
3. Go to **Billing** (left sidebar) → Add your credit card
4. Add **$25** credit (minimum; you'll use ~$5-15)

### Step 2: Create a GPU Pod

1. Click **Pods** in the left sidebar → **+ Deploy**
2. Select a GPU template:
   - **GPU Type:** NVIDIA RTX 4090 (24 GB VRAM) — best price/performance
   - **Alternative:** NVIDIA A40 (48 GB) if training Qwen3-VL full (not quantized)
3. Configure the pod:
   - **Container Image:** `runpod/pytorch:2.6.0-py3.11-cuda12.4.1-devel-ubuntu22.04`
   - **Container Disk:** 50 GB
   - **Volume Disk:** 100 GB (for datasets and model outputs)
   - **Volume Mount Path:** `/workspace`
4. Click **Deploy** → wait for pod to start (1-2 minutes)

### Step 3: Connect to the Pod

1. Once running, click the **Connect** button on your pod
2. Choose **Start Web Terminal** or **SSH** (either works)
3. In the terminal:

```bash
# Verify GPU is available
nvidia-smi

# You should see something like:
# NVIDIA RTX 4090, 24GB VRAM, CUDA 12.4
```

### Step 4: Clone the Repository and Install Dependencies

```bash
cd /workspace

# Clone your Numera repo (replace with your actual repo URL)
git clone https://github.com/YOUR_USERNAME/Numera.git
cd Numera

# Install Python dependencies for training
pip install -r ml-training/requirements.txt
pip install -r ml-service/requirements.txt

# Verify torch sees the GPU
python -c "import torch; print(f'CUDA available: {torch.cuda.is_available()}, Device: {torch.cuda.get_device_name(0)}')"
```

### Step 5: Download Training Data from SEC EDGAR

```bash
cd ml-training

# Configure and run the EDGAR downloader
# This downloads real 10-K filings from SEC (Meta, Alphabet, Apple, etc.)
python scripts/edgar_downloader.py \
  --output-dir /workspace/data/edgar \
  --num-companies 50 \
  --filing-types 10-K \
  --years 2022,2023

# Parse XBRL data for auto-labeling
python scripts/xbrl_parser.py \
  --input-dir /workspace/data/edgar \
  --output-dir /workspace/data/xbrl_labels \
  --taxonomy-path ../data/taxonomy/

# Split into train/val/test sets
python scripts/data_splitter.py \
  --input-dir /workspace/data/xbrl_labels \
  --output-dir /workspace/data/splits \
  --train-ratio 0.8 --val-ratio 0.1 --test-ratio 0.1
```

### Step 6: Train the Models

Train each model one at a time. Start MLflow for experiment tracking:

```bash
# Start MLflow tracking server in background
mlflow server --host 0.0.0.0 --port 5000 \
  --backend-store-uri sqlite:///mlflow.db \
  --default-artifact-root /workspace/mlflow-artifacts &
```

#### Model 1: LayoutLM Zone Classifier (~30-60 min)

```bash
# Open and run the training notebook, or run as script:
cd /workspace/Numera/ml-training

python -c "
import subprocess
# Convert notebook to script and run
subprocess.run(['jupyter', 'nbconvert', '--to', 'script', 
                'notebooks/07_layoutlm_zone_training.ipynb'])
" 

# Or run the training directly:
python notebooks/07_layoutlm_zone_training.py
```

**Training configuration** (edit in notebook or pass as args):
```python
MODEL_NAME = "microsoft/layoutlm-base-uncased"
NUM_LABELS = 8  # BALANCE_SHEET, INCOME_STATEMENT, CASH_FLOW, etc.
BATCH_SIZE = 8
EPOCHS = 10
LEARNING_RATE = 2e-5
DATA_DIR = "/workspace/data/splits"
OUTPUT_DIR = "/workspace/models/zone_classifier"
```

**Expected output:**
```
Epoch 10/10 — Train Loss: 0.12, Val Accuracy: 93.2%
Model saved to /workspace/models/zone_classifier/
```

#### Model 2: Sentence-BERT IFRS Matcher (~15-30 min)

```bash
python notebooks/09_sbert_finetuning.py
```

**Configuration:**
```python
BASE_MODEL = "sentence-transformers/all-MiniLM-L6-v2"
BATCH_SIZE = 32
EPOCHS = 5
OUTPUT_DIR = "/workspace/models/sbert_ifrs"
```

**Expected output:**
```
Training complete — Accuracy: 87.4%, F1: 0.86
Model saved to /workspace/models/sbert_ifrs/
```

#### Model 3: NG-MILP GNN (Covenant Predictor) (~20-40 min)

```bash
python scripts/12_train_ng_milp_gnn.py \
  --data-dir /workspace/data/splits \
  --output-dir /workspace/models/ng_milp_gnn \
  --epochs 40 \
  --batch-size 64
```

#### Model 4: Qwen3-VL Fine-tuning (Optional — ~2-4 hours)

> **Note:** This is optional. The base Qwen3-VL model works well without fine-tuning.
> Only do this if you have budget for extended GPU time.

```bash
python notebooks/03_vlm_fine_tuning.py
```

### Step 7: Export Trained Models

```bash
# Create a models archive
cd /workspace/models
tar -czf numera-models.tar.gz \
  zone_classifier/ \
  sbert_ifrs/ \
  ng_milp_gnn/

# Upload to HuggingFace Hub (we'll set this up in Phase 2)
# For now, download to your local machine:
# Option A: Use RunPod's file browser (click "Files" on pod page)
# Option B: SCP from pod
#   scp -P <PORT> root@<POD_IP>:/workspace/models/numera-models.tar.gz ./
```

### Step 8: Stop the Pod (IMPORTANT — stops billing)

1. Go back to RunPod dashboard → **Pods**
2. Click the **⋮** menu on your pod → **Stop Pod**
3. Once you've downloaded your models, click **Delete Pod** to free resources
4. Check **Billing** to confirm no more charges

---

## 4. Phase 2 — Host ML & OCR on HuggingFace Spaces

> **What:** Deploy the ML and OCR services as free API endpoints.
> **Cost:** $0 (free tier: 2 vCPU, 16 GB RAM per Space).

### Step 1: Sign Up for HuggingFace

1. Go to https://huggingface.co
2. Click **Sign Up** → create account with email or GitHub
3. Verify your email address

### Step 2: Upload Your Trained Models

1. Click your profile icon → **New Model**
2. Create a model repository:
   - **Name:** `numera-zone-classifier`
   - **Visibility:** Private (or Public if you prefer)
3. Click **Create Model**

```bash
# Install HuggingFace CLI (on your local machine)
pip install huggingface_hub

# Login
huggingface-cli login
# Paste your token from https://huggingface.co/settings/tokens

# Upload zone classifier model
huggingface-cli upload YOUR_USERNAME/numera-zone-classifier \
  /path/to/models/zone_classifier/ .

# Upload SBERT IFRS model
huggingface-cli upload YOUR_USERNAME/numera-sbert-ifrs \
  /path/to/models/sbert_ifrs/ .

# Upload GNN model
huggingface-cli upload YOUR_USERNAME/numera-ng-milp-gnn \
  /path/to/models/ng_milp_gnn/ .
```

### Step 3: Create the ML Service Space

1. Go to https://huggingface.co/spaces
2. Click **Create new Space**
3. Configure:
   - **Name:** `numera-ml-service`
   - **License:** Choose appropriate license
   - **SDK:** Docker
   - **Hardware:** CPU basic (free) — 2 vCPU, 16 GB RAM
   - **Visibility:** Private
4. Click **Create Space**

Now push the ML service code:

```bash
# Clone the empty Space repo
git clone https://huggingface.co/spaces/YOUR_USERNAME/numera-ml-service
cd numera-ml-service

# Copy ML service files
cp -r /path/to/Numera/ml-service/* .

# Create a Dockerfile for HuggingFace Spaces
cat > Dockerfile << 'DOCKERFILE'
FROM python:3.11-slim

# HuggingFace Spaces requires port 7860
ENV PORT=7860
ENV HOME=/home/user
ENV PATH=/home/user/.local/bin:$PATH

# Create non-root user (required by HF Spaces)
RUN useradd -m -u 1000 user
WORKDIR /home/user/app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Switch to non-root user
USER user

# HuggingFace Spaces health check
HEALTHCHECK --interval=30s --timeout=10s CMD curl -f http://localhost:7860/api/ml/health || exit 1

# Start the ML service on port 7860
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "7860"]
DOCKERFILE

# Create a .env file for Spaces secrets (set via HF UI, not committed)
cat > .env.example << 'ENV'
ML_DEVICE=cpu
ML_MODEL_CACHE_DIR=/home/user/app/models
HF_MODEL_ZONE_CLASSIFIER=YOUR_USERNAME/numera-zone-classifier
HF_MODEL_SBERT_IFRS=YOUR_USERNAME/numera-sbert-ifrs
HF_MODEL_NG_MILP_GNN=YOUR_USERNAME/numera-ng-milp-gnn
ML_API_KEY=your-secure-api-key-here
ENV

# Push to HuggingFace
git add .
git commit -m "Deploy ML service"
git push
```

**Configure Secrets in HuggingFace UI:**

1. Go to your Space → **Settings** → **Variables and secrets**
2. Add these as **Secrets** (they'll be available as environment variables):

| Secret | Value |
|--------|-------|
| `ML_API_KEY` | Generate a random key: `openssl rand -hex 32` |
| `HF_TOKEN` | Your HuggingFace token (for downloading private models) |
| `ML_PG_HOST` | Your Oracle Cloud PostgreSQL IP (set in Phase 3) |
| `ML_PG_PASSWORD` | Your database password |

3. The Space will auto-build and deploy. Watch the **Logs** tab for progress.

### Step 4: Create the OCR Service Space

1. Go to https://huggingface.co/spaces → **Create new Space**
2. Configure:
   - **Name:** `numera-ocr-service`
   - **SDK:** Docker
   - **Hardware:** CPU basic (free) — 2 vCPU, 16 GB RAM
     > **Important:** Free tier has 16 GB RAM. Qwen3-VL quantized needs ~8-10 GB. This is tight but works. If you experience OOM, upgrade to the $9/mo tier (8 vCPU, 32 GB RAM).
   - **Visibility:** Private
3. Click **Create Space**

```bash
# Clone the empty Space repo
git clone https://huggingface.co/spaces/YOUR_USERNAME/numera-ocr-service
cd numera-ocr-service

# Copy OCR service files
cp -r /path/to/Numera/ocr-service/* .

# Create Dockerfile for HuggingFace Spaces
cat > Dockerfile << 'DOCKERFILE'
FROM python:3.11-slim

ENV PORT=7860
ENV HOME=/home/user
ENV PATH=/home/user/.local/bin:$PATH

# Create non-root user
RUN useradd -m -u 1000 user

# Install system dependencies for image processing
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 libglib2.0-0 poppler-utils curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /home/user/app

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Create model cache directory
RUN mkdir -p /home/user/app/models && chown -R user:user /home/user

USER user

HEALTHCHECK --interval=30s --timeout=10s CMD curl -f http://localhost:7860/api/ocr/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "7860"]
DOCKERFILE

# Push to HuggingFace
git add .
git commit -m "Deploy OCR service"
git push
```

**Configure Secrets (same as ML service):**

| Secret | Value |
|--------|-------|
| `OCR_API_KEY` | Generate: `openssl rand -hex 32` |
| `OCR_PROCESSOR_BACKEND` | `qwen3vl` |
| `OCR_VLM_MODEL_ID` | `Qwen/Qwen3-VL-8B-Instruct` |
| `OCR_VLM_QUANTIZE` | `true` |
| `OCR_VLM_DEVICE` | `cpu` |
| `HF_TOKEN` | Your HuggingFace token |

### Step 5: Verify Both Spaces Are Running

After deployment (5-15 minutes for first build):

```bash
# Test ML service
curl https://YOUR_USERNAME-numera-ml-service.hf.space/api/ml/health

# Test OCR service
curl https://YOUR_USERNAME-numera-ocr-service.hf.space/api/ocr/health

# Both should return: {"status":"healthy"}
```

**Note your Space URLs:**
- ML: `https://YOUR_USERNAME-numera-ml-service.hf.space`
- OCR: `https://YOUR_USERNAME-numera-ocr-service.hf.space`

You'll need these in Phase 3.

### HuggingFace Spaces Notes

| Feature | Free Tier | Upgraded ($9/mo) |
|---------|-----------|-------------------|
| CPU | 2 vCPU | 8 vCPU |
| RAM | 16 GB | 32 GB |
| Storage | 50 GB | 50 GB |
| Sleep after inactivity | 48 hours | Never |
| GPU available | No | Yes ($$$) |

> **Tip:** On the free tier, Spaces sleep after 48 hours of no traffic. The first request after sleep takes 30-60 seconds (cold start). For continuous availability, consider the $9/mo upgrade.

---

## 5. Phase 3 — Deploy App on Oracle Cloud Free Tier

> **What:** Host the backend, frontend, database, cache, and storage on Oracle Cloud's Always-Free resources.
> **Cost:** $0/month (permanently free).

### Step 1: Sign Up for Oracle Cloud

1. Go to https://cloud.oracle.com
2. Click **Sign Up** → **Start for Free**
3. Fill in your details:
   - **Cloud Account Name:** Choose a short name (e.g., `numera-prod`)
   - **Home Region:** Choose the closest to your users
     - US East (Ashburn) — `us-ashburn-1`
     - US West (Phoenix) — `us-phoenix-1`
     - UK South (London) — `uk-london-1`
     - etc.
4. Enter your credit card (Oracle will NOT charge you; it's verification only)
5. Complete the sign-up. You'll get:
   - **$300 free credit** (expires in 30 days)
   - **Always-Free** resources that never expire

### Step 2: Understand What's Always Free

Oracle Cloud Always-Free includes:

| Resource | Allocation | What We'll Use It For |
|----------|------------|----------------------|
| ARM Ampere A1 VMs | 4 OCPUs, 24 GB RAM total | Everything! |
| Boot volume | Up to 200 GB | OS + Docker images |
| Block volume | 2 × 50 GB | Data storage |
| Object Storage | 10 GB | Backups |
| Load Balancer | 1 (10 Mbps) | SSL termination + routing |
| Outbound data | 10 TB/month | More than enough |

### Step 3: Create an ARM VM Instance

1. In Oracle Cloud Console, go to **Compute** → **Instances** → **Create Instance**
2. Configure:
   - **Name:** `numera-server`
   - **Image:** Ubuntu 24.04 (aarch64/ARM)
     - Click "Change image" → Ubuntu → 24.04 Minimal aarch64
   - **Shape:** VM.Standard.A1.Flex
     - **OCPUs:** 4 (max free)
     - **Memory:** 24 GB (max free)
   - **Networking:**
     - Create a new VCN or use default
     - Create a new public subnet
     - **Assign public IPv4 address:** YES
   - **SSH Keys:**
     - **Generate a key pair** → Download both private and public keys
     - Save the private key as `~/.ssh/oracle_key` on your local machine
3. Click **Create** → wait 2-5 minutes for provisioning

> **Common Issue:** "Out of capacity" error. This means the region is full.
> **Solution:** Try a different Availability Domain, or try again in a few hours, or try a different region.

### Step 4: Configure Firewall Rules

1. Go to **Networking** → **Virtual Cloud Networks** → Click your VCN
2. Click your **Public Subnet** → Click the **Security List**
3. Click **Add Ingress Rules** and add:

| Source CIDR | Protocol | Dest Port | Description |
|-------------|----------|-----------|-------------|
| `0.0.0.0/0` | TCP | 80 | HTTP |
| `0.0.0.0/0` | TCP | 443 | HTTPS |
| `0.0.0.0/0` | TCP | 3000 | Frontend (temporary, remove after setting up reverse proxy) |
| `0.0.0.0/0` | TCP | 8080 | Backend API (temporary) |

> **Security Note:** Ports 3000 and 8080 are temporary for testing. Remove them after setting up the Nginx reverse proxy in Phase 5.

### Step 5: SSH into the VM

```bash
# Set permissions on the private key
chmod 600 ~/.ssh/oracle_key

# SSH into the VM (replace with your VM's public IP)
ssh -i ~/.ssh/oracle_key ubuntu@<YOUR_VM_PUBLIC_IP>

# You should see:
# Welcome to Ubuntu 24.04 LTS (GNU/Linux 6.x.x aarch64)
```

### Step 6: Install Docker on the VM

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Docker
sudo apt install -y docker.io docker-compose-plugin

# Add your user to docker group
sudo usermod -aG docker $USER

# Apply group change (or log out and back in)
newgrp docker

# Verify
docker --version
docker compose version
```

### Step 7: Install Additional Tools

```bash
# Install git, curl, htop for monitoring
sudo apt install -y git curl htop nano

# Install Java 21 JRE (for building the backend if needed)
sudo apt install -y openjdk-21-jre-headless
```

### Step 8: Clone and Configure the Project

```bash
# Clone your repository
cd ~
git clone https://github.com/YOUR_USERNAME/Numera.git
cd Numera
```

Create the production environment file:

```bash
cat > .env.production << 'EOF'
# ============================================
# NUMERA PRODUCTION ENVIRONMENT CONFIGURATION
# ============================================

# --- Database ---
POSTGRES_USER=numera
POSTGRES_PASSWORD=CHANGE_ME_TO_A_STRONG_PASSWORD
POSTGRES_DB=numera
DB_URL=jdbc:postgresql://postgres:5432/numera

# --- JWT Security ---
# Generate with: openssl rand -base64 48
JWT_SECRET=CHANGE_ME_GENERATE_WITH_openssl_rand_base64_48
JWT_ACCESS_EXPIRATION_MS=3600000
JWT_REFRESH_EXPIRATION_MS=604800000

# --- MinIO Storage ---
MINIO_ROOT_USER=numera-admin
MINIO_ROOT_PASSWORD=CHANGE_ME_TO_A_STRONG_PASSWORD
MINIO_ENDPOINT=http://minio:9000
MINIO_BUCKET=numera-documents

# --- Redis Cache ---
REDIS_HOST=redis
REDIS_PORT=6379

# --- ML & OCR Services (HuggingFace Spaces URLs) ---
OCR_SERVICE_URL=https://YOUR_USERNAME-numera-ocr-service.hf.space/api
ML_SERVICE_URL=https://YOUR_USERNAME-numera-ml-service.hf.space/api
OCR_API_KEY=YOUR_OCR_API_KEY_FROM_PHASE_2
ML_API_KEY=YOUR_ML_API_KEY_FROM_PHASE_2
ML_TIMEOUT_MS=120000

# --- Server ---
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod

# --- Frontend ---
NEXT_PUBLIC_API_BASE_URL=http://YOUR_VM_PUBLIC_IP:8080/api
EOF
```

**IMPORTANT:** Generate real secrets:

```bash
# Generate JWT secret
echo "JWT_SECRET=$(openssl rand -base64 48)"

# Generate database password
echo "POSTGRES_PASSWORD=$(openssl rand -hex 24)"

# Generate MinIO password
echo "MINIO_ROOT_PASSWORD=$(openssl rand -hex 24)"

# Copy these values into your .env.production file!
nano .env.production
```

### Step 9: Create Production Docker Compose

```bash
cat > docker-compose.prod.yml << 'YAML'
version: "3.8"

services:
  # --- PostgreSQL Database ---
  postgres:
    image: postgres:17-alpine
    restart: unless-stopped
    environment:
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: ${POSTGRES_DB}
    volumes:
      - pg-data:/var/lib/postgresql/data
    ports:
      - "127.0.0.1:5432:5432"  # Only accessible from localhost
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 4G

  # --- Redis Cache ---
  redis:
    image: redis:7-alpine
    restart: unless-stopped
    command: redis-server --maxmemory 512mb --maxmemory-policy allkeys-lru
    volumes:
      - redis-data:/data
    ports:
      - "127.0.0.1:6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    deploy:
      resources:
        limits:
          cpus: "0.5"
          memory: 512M

  # --- MinIO Object Storage ---
  minio:
    image: minio/minio:latest
    restart: unless-stopped
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    volumes:
      - minio-data:/data
    ports:
      - "127.0.0.1:9000:9000"
      - "127.0.0.1:9001:9001"
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 30s
      timeout: 10s
      retries: 3
    deploy:
      resources:
        limits:
          cpus: "0.5"
          memory: 1G

  # --- MinIO Bucket Init ---
  minio-init:
    image: minio/mc:latest
    depends_on:
      minio:
        condition: service_healthy
    entrypoint: >
      /bin/sh -c "
      mc alias set local http://minio:9000 $${MINIO_ROOT_USER} $${MINIO_ROOT_PASSWORD};
      mc mb --ignore-existing local/${MINIO_BUCKET};
      echo 'Bucket created successfully';
      "
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
      MINIO_BUCKET: ${MINIO_BUCKET}

  # --- Spring Boot Backend ---
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      minio:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}
      SPRING_DATASOURCE_URL: ${DB_URL}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      JWT_SECRET: ${JWT_SECRET}
      JWT_ACCESS_EXPIRATION_MS: ${JWT_ACCESS_EXPIRATION_MS}
      JWT_REFRESH_EXPIRATION_MS: ${JWT_REFRESH_EXPIRATION_MS}
      MINIO_ENDPOINT: ${MINIO_ENDPOINT}
      MINIO_ACCESS_KEY: ${MINIO_ROOT_USER}
      MINIO_SECRET_KEY: ${MINIO_ROOT_PASSWORD}
      MINIO_BUCKET: ${MINIO_BUCKET}
      OCR_SERVICE_URL: ${OCR_SERVICE_URL}
      ML_SERVICE_URL: ${ML_SERVICE_URL}
      OCR_API_KEY: ${OCR_API_KEY}
      ML_API_KEY: ${ML_API_KEY}
      ML_TIMEOUT_MS: ${ML_TIMEOUT_MS}
      SERVER_PORT: ${SERVER_PORT}
    ports:
      - "8080:8080"
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:8080/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    deploy:
      resources:
        limits:
          cpus: "2.0"
          memory: 4G

  # --- Next.js Frontend ---
  frontend:
    build:
      context: ./numera-ui
      dockerfile: Dockerfile
      args:
        NEXT_PUBLIC_API_BASE_URL: ${NEXT_PUBLIC_API_BASE_URL}
    restart: unless-stopped
    depends_on:
      backend:
        condition: service_healthy
    environment:
      NODE_ENV: production
      NEXT_PUBLIC_API_BASE_URL: ${NEXT_PUBLIC_API_BASE_URL}
    ports:
      - "3000:3000"
    deploy:
      resources:
        limits:
          cpus: "0.5"
          memory: 512M

volumes:
  pg-data:
    driver: local
  redis-data:
    driver: local
  minio-data:
    driver: local

networks:
  default:
    name: numera-prod
YAML
```

### Step 10: Build and Start Everything

```bash
# Load environment variables
set -a && source .env.production && set +a

# Build and start all services
docker compose -f docker-compose.prod.yml up -d --build

# Watch the logs (Ctrl+C to stop watching)
docker compose -f docker-compose.prod.yml logs -f

# Check all services are healthy
docker compose -f docker-compose.prod.yml ps
```

**Expected output:**
```
NAME              STATUS                    PORTS
postgres          Up (healthy)              127.0.0.1:5432->5432/tcp
redis             Up (healthy)              127.0.0.1:6379->6379/tcp
minio             Up (healthy)              127.0.0.1:9000-9001->9000-9001/tcp
backend           Up (healthy)              0.0.0.0:8080->8080/tcp
frontend          Up                        0.0.0.0:3000->3000/tcp
```

### Step 11: Verify the Deployment

```bash
# From inside the VM:
curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}

curl http://localhost:3000
# Should return HTML content
```

From your **local machine:**
```bash
# Replace with your Oracle VM's public IP
curl http://<YOUR_VM_PUBLIC_IP>:8080/actuator/health
# Should return: {"status":"UP"}

# Open in browser:
# http://<YOUR_VM_PUBLIC_IP>:3000
```

### Step 12: Configure Ubuntu Firewall (iptables)

Oracle Cloud firewall rules aren't enough — Ubuntu also has iptables:

```bash
# Allow HTTP, HTTPS, and app ports
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 3000 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 8080 -j ACCEPT

# Save the rules so they persist after reboot
sudo apt install -y iptables-persistent
sudo netfilter-persistent save
```

---

## 6. Phase 4 — Connect Everything

### Step 1: Update HuggingFace Spaces with Oracle Cloud IP

Go back to your HuggingFace Spaces and update the secrets:

**For ML Service Space (`numera-ml-service` → Settings → Secrets):**
| Secret | Value |
|--------|-------|
| `ML_PG_HOST` | `<YOUR_VM_PUBLIC_IP>` |
| `ML_PG_PORT` | `5432` |
| `ML_PG_DATABASE` | `numera` |
| `ML_PG_USER` | `numera` |
| `ML_PG_PASSWORD` | Your generated password |

> **Note:** If your ML service doesn't need direct database access (it gets data via the backend API), skip these database settings.

### Step 2: Verify End-to-End Flow

```bash
# 1. Login
TOKEN=$(curl -s http://<YOUR_VM_IP>:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@numera.ai","password":"Admin@123"}' \
  | python3 -c "import sys,json; print(json.loads(sys.stdin.read())['accessToken'])")

echo "Token: ${TOKEN:0:20}..."

# 2. Upload a test PDF
curl -s -X POST http://<YOUR_VM_IP>:8080/api/documents/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@test_document.pdf" \
  -F "customerId=9475346e-d7b0-4ab3-8b9f-7884b779bf80" \
  -F "statementDate=2023-12-31"

# 3. Check document status (should eventually reach READY)
curl -s http://<YOUR_VM_IP>:8080/api/documents/<DOC_ID> \
  -H "Authorization: Bearer $TOKEN"
```

### Step 3: Change Default Admin Password

```bash
# This is critical for production!
# Login to the app at http://<YOUR_VM_IP>:3000
# Go to Settings → Change Password
# Change from Admin@123 to a strong password
```

---

## 7. Phase 5 — DNS, SSL & Go Live

### Option A: Free SSL with Let's Encrypt + Nginx (Recommended)

#### Install Nginx Reverse Proxy

```bash
# SSH into Oracle Cloud VM
ssh -i ~/.ssh/oracle_key ubuntu@<YOUR_VM_PUBLIC_IP>

# Install Nginx and Certbot
sudo apt install -y nginx certbot python3-certbot-nginx
```

#### Configure Nginx

```bash
sudo nano /etc/nginx/sites-available/numera
```

Paste this configuration:

```nginx
# Rate limiting zone
limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s;

server {
    listen 80;
    server_name your-domain.com;  # Replace with your domain

    # Redirect HTTP to HTTPS (Certbot will add this)
    # For now, serve directly

    # Frontend
    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Backend API
    location /api/ {
        limit_req zone=api burst=20 nodelay;
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # File upload support
        client_max_body_size 50M;
    }

    # WebSocket support
    location /ws/ {
        proxy_pass http://127.0.0.1:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Health check endpoint (no rate limiting)
    location /health {
        proxy_pass http://127.0.0.1:8080/actuator/health;
    }
}
```

```bash
# Enable the site
sudo ln -sf /etc/nginx/sites-available/numera /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default

# Test config
sudo nginx -t

# Restart Nginx
sudo systemctl restart nginx
sudo systemctl enable nginx
```

#### Get a Free Domain (Optional)

If you don't have a domain, get a free one:

1. **DuckDNS** (free): https://www.duckdns.org
   - Sign in with GitHub
   - Create a subdomain (e.g., `numera-app.duckdns.org`)
   - Point it to your Oracle Cloud VM's public IP

2. **Freenom** (free .tk/.ml domains): https://www.freenom.com

#### Set Up SSL Certificate

```bash
# Replace with your actual domain
sudo certbot --nginx -d your-domain.com

# Follow the prompts:
# - Enter your email
# - Agree to terms
# - Choose to redirect HTTP to HTTPS (option 2)

# Verify auto-renewal
sudo certbot renew --dry-run
```

#### Remove Temporary Firewall Rules

Now that Nginx handles routing, remove direct access to ports 3000 and 8080:

1. Go to Oracle Cloud Console → **Networking** → **VCN** → **Security List**
2. Remove the ingress rules for ports 3000 and 8080
3. On the VM:
```bash
sudo iptables -D INPUT -m state --state NEW -p tcp --dport 3000 -j ACCEPT
sudo iptables -D INPUT -m state --state NEW -p tcp --dport 8080 -j ACCEPT
sudo netfilter-persistent save
```

### Option B: Without a Custom Domain (IP only)

If you don't want a domain, access the app directly via IP. Skip Nginx and SSL. Not recommended for production but works for testing:

- Frontend: `http://<YOUR_VM_IP>:3000`
- Backend API: `http://<YOUR_VM_IP>:8080/api`

---

## 8. Maintenance & Monitoring

### Auto-Start on Reboot

```bash
# Create a systemd service for Docker Compose
sudo nano /etc/systemd/system/numera.service
```

```ini
[Unit]
Description=Numera Application Stack
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/home/ubuntu/Numera
ExecStart=/usr/bin/docker compose -f docker-compose.prod.yml up -d
ExecStop=/usr/bin/docker compose -f docker-compose.prod.yml down
EnvironmentFile=/home/ubuntu/Numera/.env.production

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable numera
sudo systemctl start numera
```

### Database Backups

```bash
# Create a backup script
cat > ~/backup-db.sh << 'SCRIPT'
#!/bin/bash
BACKUP_DIR=/home/ubuntu/backups
mkdir -p $BACKUP_DIR
DATE=$(date +%Y%m%d_%H%M%S)

# Dump PostgreSQL
docker exec numera-prod-postgres-1 pg_dump -U numera numera | gzip > "$BACKUP_DIR/numera_db_$DATE.sql.gz"

# Keep only last 7 days of backups
find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete

echo "Backup completed: numera_db_$DATE.sql.gz"
SCRIPT

chmod +x ~/backup-db.sh

# Schedule daily backups at 2 AM
(crontab -l 2>/dev/null; echo "0 2 * * * /home/ubuntu/backup-db.sh >> /home/ubuntu/backup.log 2>&1") | crontab -
```

### Monitoring Commands

```bash
# Check all service statuses
docker compose -f docker-compose.prod.yml ps

# View logs for a specific service
docker compose -f docker-compose.prod.yml logs -f backend --tail=100

# Check resource usage
docker stats --no-stream

# Check disk usage
df -h

# Check memory
free -h

# Monitor system resources
htop
```

### Updating the Application

```bash
# SSH into the VM
ssh -i ~/.ssh/oracle_key ubuntu@<YOUR_VM_PUBLIC_IP>

cd ~/Numera

# Pull latest code
git pull origin main

# Rebuild and restart
docker compose -f docker-compose.prod.yml up -d --build

# Verify health
docker compose -f docker-compose.prod.yml ps
```

---

## 9. Cost Summary

| Service | Free Tier | Usage | Monthly Cost |
|---------|-----------|-------|-------------|
| **Oracle Cloud** | 4 OCPU ARM, 24 GB RAM, 200 GB disk | Backend, Frontend, DB, Cache, Storage | **$0** |
| **HuggingFace Spaces** | 2× CPU basic (2 vCPU, 16 GB each) | ML Service + OCR Service | **$0** |
| **RunPod** | Pay-per-use GPU | One-time model training | **~$10 one-time** |
| **Let's Encrypt** | Free SSL certificates | HTTPS | **$0** |
| **DuckDNS** | Free subdomain | DNS | **$0** |
| **Total** | | | **$0/month** |

### If You Outgrow Free Tier

| Upgrade Path | Cost | When |
|--------------|------|------|
| HuggingFace Spaces CPU Upgraded | $9/mo each | If OCR OOMs or need no cold starts |
| Oracle Cloud block storage | ~$0.0255/GB/mo | If 200 GB boot volume isn't enough |
| HuggingFace GPU Space | $0.60-$4.50/hr | If OCR needs GPU for speed |
| RunPod Serverless | Pay-per-request | If you need GPU-accelerated OCR permanently |

---

## 10. Troubleshooting

### Common Issues

#### "Out of capacity" when creating Oracle Cloud VM

**Problem:** Oracle's free ARM instances are popular and often sold out.

**Solutions:**
1. Try different Availability Domains (AD-1, AD-2, AD-3)
2. Try at off-peak hours (early morning, weekends)
3. Try a different region
4. Use the OCI CLI to auto-retry:

```bash
# Install OCI CLI
bash -c "$(curl -L https://raw.githubusercontent.com/oracle/oci-cli/master/scripts/install/install.sh)"

# Create a retry script
while true; do
  oci compute instance launch \
    --availability-domain "YOUR_AD" \
    --compartment-id "YOUR_COMPARTMENT_OCID" \
    --shape "VM.Standard.A1.Flex" \
    --shape-config '{"ocpus": 4, "memoryInGBs": 24}' \
    --image-id "YOUR_IMAGE_OCID" \
    --subnet-id "YOUR_SUBNET_OCID" \
    --ssh-authorized-keys-file ~/.ssh/oracle_key.pub \
    && break
  echo "Retrying in 60 seconds..."
  sleep 60
done
```

#### HuggingFace Space keeps crashing (OOM)

**Problem:** Qwen3-VL model exceeds 16 GB RAM.

**Solutions:**
1. Ensure `OCR_VLM_QUANTIZE=true` is set (reduces to ~8-10 GB)
2. Add `MALLOC_TRIM_THRESHOLD_=65536` env var to reduce memory fragmentation
3. Upgrade to $9/mo tier (32 GB RAM)
4. Switch to `paddleocr` backend as fallback: `OCR_PROCESSOR_BACKEND=paddleocr`

#### Backend won't connect to HuggingFace Spaces

**Problem:** ML/OCR API calls from Oracle Cloud to HuggingFace timeout.

**Solutions:**
1. Check the Space is awake (not sleeping from inactivity):
   ```bash
   curl -v https://YOUR_USERNAME-numera-ml-service.hf.space/api/ml/health
   ```
2. First request after cold start takes 30-60 seconds. Increase timeout:
   ```
   ML_TIMEOUT_MS=180000
   ```
3. Set up a keep-alive cron job on Oracle Cloud:
   ```bash
   # Ping both services every 30 minutes to prevent sleep
   (crontab -l 2>/dev/null; echo "*/30 * * * * curl -sf https://YOUR_USERNAME-numera-ml-service.hf.space/api/ml/health > /dev/null") | crontab -
   (crontab -l 2>/dev/null; echo "*/30 * * * * curl -sf https://YOUR_USERNAME-numera-ocr-service.hf.space/api/ocr/health > /dev/null") | crontab -
   ```

#### Database migration fails on startup

**Problem:** Flyway migration errors in backend logs.

**Solution:**
```bash
# Check backend logs
docker compose -f docker-compose.prod.yml logs backend | grep -i flyway

# If migration is corrupt, reset (WARNING: destroys data):
docker compose -f docker-compose.prod.yml exec postgres psql -U numera -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
docker compose -f docker-compose.prod.yml restart backend
```

#### Docker build fails on ARM

**Problem:** Some images don't support `linux/arm64`.

**Solution:** Most official images support ARM. If a specific image doesn't:
```bash
# Build with multi-platform support
docker buildx build --platform linux/arm64 -t numera/backend .
```

#### Can't access the app from browser

**Checklist:**
1. ✅ Oracle Cloud Security List has ingress rules for ports 80/443
2. ✅ Ubuntu iptables allows the ports
3. ✅ Docker containers are running: `docker compose ps`
4. ✅ Backend health check passes: `curl http://localhost:8080/actuator/health`
5. ✅ Frontend is serving: `curl http://localhost:3000`
6. ✅ Nginx is running: `sudo systemctl status nginx`

---

## Quick Reference Card

```
┌────────────────────────────────────────────────────────────┐
│                    NUMERA QUICK REFERENCE                   │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  Default Login:  admin@numera.ai / Admin@123               │
│  (CHANGE THIS IMMEDIATELY IN PRODUCTION!)                  │
│                                                            │
│  Service URLs (replace <IP> with your VM's public IP):     │
│  ─────────────────────────────────────────────────         │
│  Frontend:   http://<IP>:3000 (or https://your-domain.com) │
│  Backend:    http://<IP>:8080/api                          │
│  Health:     http://<IP>:8080/actuator/health              │
│  MinIO:      http://127.0.0.1:9001 (SSH tunnel only)      │
│                                                            │
│  HuggingFace Spaces:                                       │
│  ML:   https://USER-numera-ml-service.hf.space             │
│  OCR:  https://USER-numera-ocr-service.hf.space            │
│                                                            │
│  Common Commands:                                          │
│  ─────────────────────────────────────────────────         │
│  Start:   docker compose -f docker-compose.prod.yml up -d  │
│  Stop:    docker compose -f docker-compose.prod.yml down   │
│  Logs:    docker compose -f docker-compose.prod.yml logs -f │
│  Status:  docker compose -f docker-compose.prod.yml ps     │
│  Rebuild: docker compose -f docker-compose.prod.yml up     │
│           -d --build                                       │
│  Backup:  ~/backup-db.sh                                   │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

---

## Appendix A: All Environment Variables Reference

### Backend (.env)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `POSTGRES_USER` | Yes | `numera` | Database username |
| `POSTGRES_PASSWORD` | Yes | — | Database password |
| `POSTGRES_DB` | Yes | `numera` | Database name |
| `JWT_SECRET` | Yes | — | JWT signing key (min 32 bytes) |
| `JWT_ACCESS_EXPIRATION_MS` | No | `3600000` | Access token TTL (1 hour) |
| `JWT_REFRESH_EXPIRATION_MS` | No | `604800000` | Refresh token TTL (7 days) |
| `MINIO_ENDPOINT` | Yes | `http://minio:9000` | MinIO API URL |
| `MINIO_ROOT_USER` | Yes | — | MinIO access key |
| `MINIO_ROOT_PASSWORD` | Yes | — | MinIO secret key |
| `MINIO_BUCKET` | Yes | `numera-documents` | S3 bucket name |
| `OCR_SERVICE_URL` | Yes | — | OCR service base URL |
| `ML_SERVICE_URL` | Yes | — | ML service base URL |
| `OCR_API_KEY` | Yes | — | OCR service API key |
| `ML_API_KEY` | Yes | — | ML service API key |
| `ML_TIMEOUT_MS` | No | `120000` | ML call timeout (ms) |
| `REDIS_HOST` | No | `redis` | Redis hostname |
| `REDIS_PORT` | No | `6379` | Redis port |

### ML Service (HuggingFace Secrets)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `ML_DEVICE` | No | `cpu` | `cpu` or `cuda` |
| `ML_API_KEY` | Yes | — | API authentication key |
| `HF_TOKEN` | Yes | — | HuggingFace access token |

### OCR Service (HuggingFace Secrets)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `OCR_PROCESSOR_BACKEND` | No | `qwen3vl` | `qwen3vl` or `paddleocr` |
| `OCR_VLM_MODEL_ID` | No | `Qwen/Qwen3-VL-8B-Instruct` | VLM model ID |
| `OCR_VLM_QUANTIZE` | No | `true` | Enable model quantization |
| `OCR_API_KEY` | Yes | — | API authentication key |
| `HF_TOKEN` | Yes | — | HuggingFace access token |

---

## Appendix B: Estimated Resource Usage on Oracle Cloud

The ARM Ampere A1 VM (4 OCPU, 24 GB RAM) allocation:

| Service | CPU | RAM | Disk |
|---------|-----|-----|------|
| PostgreSQL 17 | 1.0 | 4 GB | ~10-50 GB |
| Spring Boot Backend | 2.0 | 4 GB | ~200 MB |
| Next.js Frontend | 0.5 | 512 MB | ~100 MB |
| Redis | 0.5 | 512 MB | ~100 MB |
| MinIO | 0.5 | 1 GB | ~50 GB |
| Nginx | — | 50 MB | — |
| OS overhead | — | 1 GB | ~5 GB |
| **Total** | **4.5** | **~11 GB** | **~65 GB** |
| **Available** | **4.0** | **24 GB** | **200 GB** |

> You have headroom. The system will run comfortably with 13 GB free RAM.
