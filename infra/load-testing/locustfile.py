"""
Numera Platform Load Testing Suite

Usage:
    locust -f locustfile.py --config config/staging.yml
    locust -f locustfile.py --host https://staging.numera.io --users 50 --spawn-rate 5
"""

import os
import json
import random
import time
from locust import HttpUser, task, between, SequentialTaskSet, events, tag


# ─── Sample Data ─────────────────────────────────────────────────

SAMPLE_CREDENTIALS = [
    {"email": f"analyst{i}@numera.test", "password": "TestPassword1!"} for i in range(1, 21)
]

MANAGER_CREDENTIALS = [
    {"email": f"manager{i}@numera.test", "password": "TestPassword1!"} for i in range(1, 6)
]


class LoginMixin:
    """Shared login helper."""

    def do_login(self, creds: dict) -> str | None:
        resp = self.client.post(
            "/api/auth/login",
            json=creds,
            name="/api/auth/login",
        )
        if resp.status_code == 200:
            token = resp.json().get("accessToken")
            self.client.headers.update({"Authorization": f"Bearer {token}"})
            return token
        return None


# ─── Analyst Flow: Login → Upload → Spread → Submit ─────────────

class AnalystSpreadingFlow(SequentialTaskSet, LoginMixin):
    """Simulates a full analyst spreading cycle."""

    customer_id = None
    document_id = None
    spread_id = None

    def on_start(self):
        creds = random.choice(SAMPLE_CREDENTIALS)
        self.do_login(creds)

    @task
    def list_customers(self):
        resp = self.client.get(
            "/api/customers",
            params={"page": 0, "size": 20},
            name="/api/customers",
        )
        if resp.status_code == 200:
            customers = resp.json().get("content", [])
            if customers:
                self.customer_id = random.choice(customers)["id"]

    @task
    def upload_document(self):
        if not self.customer_id:
            return
        # Simulate a 500KB PDF upload
        fake_pdf = b"%PDF-1.4 " + os.urandom(500 * 1024)
        resp = self.client.post(
            f"/api/documents/upload",
            files={"file": ("financial_statement.pdf", fake_pdf, "application/pdf")},
            data={"customerId": str(self.customer_id)},
            name="/api/documents/upload",
        )
        if resp.status_code in (200, 201):
            self.document_id = resp.json().get("id")

    @task
    def wait_for_processing(self):
        if not self.document_id:
            return
        # Poll status up to 10 times
        for _ in range(10):
            resp = self.client.get(
                f"/api/documents/{self.document_id}",
                name="/api/documents/[id]",
            )
            if resp.status_code == 200:
                status = resp.json().get("status")
                if status in ("PROCESSED", "READY", "COMPLETED"):
                    break
            time.sleep(2)

    @task
    def create_spread(self):
        if not self.document_id:
            return
        resp = self.client.post(
            "/api/spreading/sessions",
            json={"documentId": self.document_id},
            name="/api/spreading/sessions",
        )
        if resp.status_code in (200, 201):
            self.spread_id = resp.json().get("id")

    @task
    def get_spread_data(self):
        if not self.spread_id:
            return
        self.client.get(
            f"/api/spreading/sessions/{self.spread_id}",
            name="/api/spreading/sessions/[id]",
        )

    @task
    def submit_spread(self):
        if not self.spread_id:
            return
        self.client.post(
            f"/api/spreading/sessions/{self.spread_id}/submit",
            name="/api/spreading/sessions/[id]/submit",
        )
        self.interrupt()


# ─── Manager Flow: Login → Queue → Approve ──────────────────────

class ManagerApprovalFlow(SequentialTaskSet, LoginMixin):
    """Simulates a manager reviewing and approving spreads."""

    def on_start(self):
        creds = random.choice(MANAGER_CREDENTIALS)
        self.do_login(creds)

    @task
    def list_pending_approvals(self):
        self.client.get(
            "/api/spreading/sessions",
            params={"status": "PENDING_REVIEW", "page": 0, "size": 20},
            name="/api/spreading/sessions?pending",
        )

    @task
    def review_and_approve(self):
        resp = self.client.get(
            "/api/spreading/sessions",
            params={"status": "PENDING_REVIEW", "page": 0, "size": 5},
            name="/api/spreading/sessions?pending",
        )
        if resp.status_code == 200:
            sessions = resp.json().get("content", [])
            for session in sessions[:3]:
                self.client.post(
                    f"/api/spreading/sessions/{session['id']}/approve",
                    json={"comment": "Approved via load test"},
                    name="/api/spreading/sessions/[id]/approve",
                )
        self.interrupt()


# ─── Covenant Monitoring Flow ────────────────────────────────────

class CovenantMonitoringFlow(SequentialTaskSet, LoginMixin):
    """Simulates covenant monitoring operations."""

    def on_start(self):
        creds = random.choice(SAMPLE_CREDENTIALS)
        self.do_login(creds)

    @task
    def list_monitoring_items(self):
        self.client.get(
            "/api/covenants/monitoring",
            params={"page": 0, "size": 50},
            name="/api/covenants/monitoring",
        )

    @task
    def view_covenant_detail(self):
        resp = self.client.get(
            "/api/covenants/monitoring",
            params={"page": 0, "size": 10},
            name="/api/covenants/monitoring",
        )
        if resp.status_code == 200:
            items = resp.json().get("content", [])
            for item in items[:3]:
                self.client.get(
                    f"/api/covenants/monitoring/{item['id']}",
                    name="/api/covenants/monitoring/[id]",
                )

    @task
    def check_risk_heatmap(self):
        self.client.get(
            "/api/covenants/risk-heatmap",
            name="/api/covenants/risk-heatmap",
        )
        self.interrupt()


# ─── Dashboard Heavy Load ────────────────────────────────────────

class DashboardHeavyLoad(SequentialTaskSet, LoginMixin):
    """Simulates heavy dashboard usage — multiple API calls per page view."""

    def on_start(self):
        creds = random.choice(SAMPLE_CREDENTIALS)
        self.do_login(creds)

    @task
    def load_dashboard(self):
        # Parallel-ish requests that a dashboard page would make
        self.client.get("/api/customers", params={"page": 0, "size": 10}, name="/api/customers")
        self.client.get("/api/documents", params={"page": 0, "size": 10}, name="/api/documents")
        self.client.get("/api/spreading/sessions", params={"page": 0, "size": 10}, name="/api/spreading/sessions")
        self.client.get("/api/covenants/monitoring", params={"page": 0, "size": 10}, name="/api/covenants/monitoring")

    @task
    def load_reports(self):
        self.client.get("/api/reports", params={"page": 0, "size": 10}, name="/api/reports")

    @task
    def load_audit_log(self):
        self.client.get("/api/audit/events", params={"page": 0, "size": 20}, name="/api/audit/events")
        self.interrupt()


# ─── User Classes ────────────────────────────────────────────────

class AnalystUser(HttpUser):
    """Simulates analyst users performing spreading workflows."""
    tasks = [AnalystSpreadingFlow]
    wait_time = between(3, 8)
    weight = 5


class ManagerUser(HttpUser):
    """Simulates manager users performing approvals."""
    tasks = [ManagerApprovalFlow]
    wait_time = between(5, 15)
    weight = 1


class CovenantUser(HttpUser):
    """Simulates covenant monitoring users."""
    tasks = [CovenantMonitoringFlow]
    wait_time = between(5, 10)
    weight = 2


class DashboardUser(HttpUser):
    """Simulates heavy dashboard usage."""
    tasks = [DashboardHeavyLoad]
    wait_time = between(2, 5)
    weight = 3


class ConcurrentUploadUser(HttpUser):
    """Simulates concurrent document uploads (burst scenario)."""
    wait_time = between(1, 3)
    weight = 2

    def on_start(self):
        creds = random.choice(SAMPLE_CREDENTIALS)
        resp = self.client.post("/api/auth/login", json=creds, name="/api/auth/login")
        if resp.status_code == 200:
            token = resp.json().get("accessToken")
            self.client.headers.update({"Authorization": f"Bearer {token}"})

    @task(5)
    @tag("upload", "burst")
    def upload_document(self):
        fake_pdf = b"%PDF-1.4 " + os.urandom(300 * 1024)
        self.client.post(
            "/api/documents/upload",
            files={"file": ("burst_upload.pdf", fake_pdf, "application/pdf")},
            data={"customerId": "1"},
            name="/api/documents/upload [burst]",
        )

    @task(1)
    def check_documents(self):
        self.client.get(
            "/api/documents",
            params={"page": 0, "size": 10},
            name="/api/documents",
        )
