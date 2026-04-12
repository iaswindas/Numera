"""Tests for the RS-BSN Covenant Predictor."""

from __future__ import annotations

import math

import numpy as np
import pytest

from app.ml.rsbsn.models import RegimeState, RSBSNPrediction
from app.ml.rsbsn.regime_hmm import RegimeHMM
from app.ml.rsbsn.state_space import BayesianStateSpace
from app.ml.rsbsn.predictor import RSBSNPredictor


# ------------------------------------------------------------------ #
# Helpers
# ------------------------------------------------------------------ #

def _normal_series(n: int = 30, start: float = 2.0, noise: float = 0.05) -> list[float]:
    """Slowly drifting series typical of a healthy covenant ratio."""
    rng = np.random.default_rng(42)
    return [start + 0.01 * i + rng.normal(0, noise) for i in range(n)]


def _crisis_series(n: int = 30, start: float = 2.0, drop: float = 0.15) -> list[float]:
    """Sharply deteriorating series that should trigger CRISIS."""
    rng = np.random.default_rng(7)
    values = [start]
    for _ in range(n - 1):
        values.append(values[-1] - drop + rng.normal(0, 0.02))
    return values


def _ascending_series(n: int = 20, start: float = 1.0, step: float = 0.1) -> list[float]:
    rng = np.random.default_rng(99)
    return [start + step * i + rng.normal(0, 0.02) for i in range(n)]


def _descending_series(n: int = 20, start: float = 3.0, step: float = 0.1) -> list[float]:
    rng = np.random.default_rng(99)
    return [start - step * i + rng.normal(0, 0.02) for i in range(n)]


# ================================================================== #
# Regime HMM tests
# ================================================================== #

class TestRegimeHMM:
    def test_fit_and_predict_normal_data(self):
        hmm = RegimeHMM()
        data = _normal_series(40)
        hmm.fit(data)
        detection = hmm.predict_regime(data)
        assert detection.regime in list(RegimeState)
        assert 0.0 <= detection.probability <= 1.0
        assert len(detection.transition_matrix) == 3
        assert all(len(row) == 3 for row in detection.transition_matrix)

    def test_fit_and_predict_crisis_data(self):
        hmm = RegimeHMM()
        data = _crisis_series(40)
        hmm.fit(data)
        detection = hmm.predict_regime(data)
        # Crisis data should push toward STRESSED or CRISIS.
        assert detection.regime in (RegimeState.STRESSED, RegimeState.CRISIS)

    def test_short_sequence_fallback(self):
        hmm = RegimeHMM()
        data = [1.5, 1.6]
        hmm.fit(data)
        detection = hmm.predict_regime(data)
        assert detection.regime in list(RegimeState)
        assert 0.0 <= detection.probability <= 1.0

    def test_empty_sequence(self):
        hmm = RegimeHMM()
        detection = hmm.predict_regime([])
        assert detection.regime == RegimeState.NORMAL

    def test_decode_returns_correct_length(self):
        hmm = RegimeHMM()
        data = _normal_series(20)
        hmm.fit(data)
        decoded = hmm.decode(data)
        assert len(decoded) == len(data)

    def test_transition_matrix_row_stochastic(self):
        hmm = RegimeHMM()
        hmm.fit(_normal_series(30))
        for row in hmm.transition.tolist():
            assert abs(sum(row) - 1.0) < 1e-6


# ================================================================== #
# Bayesian State-Space tests
# ================================================================== #

class TestBayesianStateSpace:
    def test_fit_and_forecast(self):
        ss = BayesianStateSpace()
        data = _ascending_series(15)
        ss.fit(data)
        forecasts = ss.forecast(4, RegimeState.NORMAL)
        assert len(forecasts) == 4
        for f in forecasts:
            assert f.std > 0
            assert f.ci_lower < f.ci_upper

    def test_forecast_ascending_trend(self):
        ss = BayesianStateSpace()
        data = _ascending_series(20, start=1.0, step=0.2)
        ss.fit(data)
        forecasts = ss.forecast(3, RegimeState.NORMAL)
        # Mean should be above the last observed value for ascending data.
        assert forecasts[0].mean > data[-3]

    def test_crisis_regime_wider_ci(self):
        ss = BayesianStateSpace()
        ss.fit(_normal_series(20))
        normal_fc = ss.forecast(4, RegimeState.NORMAL)
        crisis_fc = ss.forecast(4, RegimeState.CRISIS)
        # Crisis regime should have wider confidence intervals.
        normal_width = normal_fc[-1].ci_upper - normal_fc[-1].ci_lower
        crisis_width = crisis_fc[-1].ci_upper - crisis_fc[-1].ci_lower
        assert crisis_width > normal_width

    def test_sample_paths_shape(self):
        ss = BayesianStateSpace()
        ss.fit(_normal_series(15))
        paths = ss.sample_paths(steps=6, regime=RegimeState.NORMAL, n_paths=100)
        assert paths.shape == (100, 6)

    def test_fit_single_value(self):
        ss = BayesianStateSpace()
        ss.fit([5.0])
        forecasts = ss.forecast(2, RegimeState.NORMAL)
        assert len(forecasts) == 2


# ================================================================== #
# RSBSNPredictor integration tests
# ================================================================== #

class TestRSBSNPredictor:
    def test_predict_normal_data(self):
        predictor = RSBSNPredictor(mc_paths=500)
        data = _normal_series(25, start=2.5)
        result = predictor.predict(data, threshold=1.0, direction="BELOW", periods_ahead=4)
        assert isinstance(result, RSBSNPrediction)
        assert 0.0 <= result.breach_probability <= 1.0
        assert result.confidence_interval["lower"] <= result.confidence_interval["upper"]
        assert len(result.forecasts) == 4
        assert len(result.regime_history) == len(data)

    def test_predict_descending_toward_threshold(self):
        predictor = RSBSNPredictor(mc_paths=500)
        data = _descending_series(20, start=3.0, step=0.12)
        result = predictor.predict(data, threshold=1.0, direction="BELOW", periods_ahead=4)
        # Should detect meaningful breach risk given downward trend.
        assert result.breach_probability > 0.0

    def test_predict_ascending_above_threshold(self):
        predictor = RSBSNPredictor(mc_paths=500)
        data = _ascending_series(20, start=3.0, step=0.15)
        result = predictor.predict(data, threshold=5.0, direction="ABOVE", periods_ahead=4)
        assert 0.0 <= result.breach_probability <= 1.0

    def test_fallback_on_short_history(self):
        predictor = RSBSNPredictor()
        result = predictor.predict([2.0, 2.1], threshold=1.5, direction="BELOW")
        assert isinstance(result, RSBSNPrediction)
        assert "insufficient_data" in [f["name"] for f in result.factors]

    def test_fallback_on_single_value(self):
        predictor = RSBSNPredictor()
        result = predictor.predict([3.0], threshold=2.0, direction="BELOW")
        assert isinstance(result, RSBSNPrediction)

    def test_factors_present(self):
        predictor = RSBSNPredictor(mc_paths=200)
        data = _normal_series(20)
        result = predictor.predict(data, threshold=1.0, direction="BELOW")
        assert len(result.factors) >= 2
        for f in result.factors:
            assert "name" in f
            assert "impact" in f

    def test_direction_above(self):
        predictor = RSBSNPredictor(mc_paths=300)
        data = _ascending_series(15, start=4.0, step=0.3)
        result = predictor.predict(data, threshold=8.0, direction="ABOVE", periods_ahead=6)
        assert 0.0 <= result.breach_probability <= 1.0
        assert len(result.forecasts) == 6

    def test_periods_ahead_capped(self):
        predictor = RSBSNPredictor(mc_paths=100)
        data = _normal_series(10)
        result = predictor.predict(data, threshold=1.0, direction="BELOW", periods_ahead=20)
        assert len(result.forecasts) == 12  # capped at 12


# ================================================================== #
# API endpoint tests
# ================================================================== #

class TestRSBSNAPI:
    @pytest.fixture
    def client(self):
        from fastapi.testclient import TestClient
        from app.main import app
        return TestClient(app)

    def test_single_prediction(self, client):
        payload = {
            "covenantId": "cov-001",
            "threshold": 1.5,
            "direction": "BELOW",
            "history": [{"period": f"Q{i+1}", "value": v} for i, v in enumerate(_normal_series(10))],
            "periodsAhead": 4,
        }
        resp = client.post("/api/ml/covenants/predict", json=payload)
        assert resp.status_code == 200
        body = resp.json()
        assert "breach_probability" in body
        assert "regime_detection" in body
        assert body["model"] in ("RS-BSN", "BASELINE_FALLBACK")

    def test_batch_prediction(self, client):
        items = []
        for i in range(3):
            items.append({
                "covenantId": f"cov-{i:03d}",
                "threshold": 1.0 + i * 0.5,
                "direction": "BELOW",
                "history": [{"period": f"Q{j+1}", "value": v} for j, v in enumerate(_normal_series(8))],
                "periodsAhead": 2,
            })
        resp = client.post("/api/ml/covenants/predict/batch", json={"requests": items})
        assert resp.status_code == 200
        body = resp.json()
        assert len(body["results"]) == 3

    def test_validation_empty_history(self, client):
        payload = {
            "covenantId": "cov-bad",
            "threshold": 1.0,
            "direction": "BELOW",
            "history": [],
            "periodsAhead": 4,
        }
        resp = client.post("/api/ml/covenants/predict", json=payload)
        assert resp.status_code == 422

    def test_validation_periods_ahead_bounds(self, client):
        payload = {
            "covenantId": "cov-002",
            "threshold": 2.0,
            "direction": "ABOVE",
            "history": [{"period": "Q1", "value": 2.0}],
            "periodsAhead": 0,
        }
        resp = client.post("/api/ml/covenants/predict", json=payload)
        assert resp.status_code == 422
