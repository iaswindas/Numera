"""Train/validation/test data splitting utilities.

Provides stratified splitting for both zone classification and
semantic matching datasets, maintaining class distribution across splits.
"""

import json
import logging
import random
from collections import Counter
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)


def stratified_split(
    data: list[dict],
    label_key: str = "zone_type",
    train_ratio: float = 0.8,
    val_ratio: float = 0.1,
    test_ratio: float = 0.1,
    seed: int = 42,
) -> tuple[list[dict], list[dict], list[dict]]:
    """Split data into train/val/test with stratification by label.

    Args:
        data: List of data dicts.
        label_key: Key to use for stratification.
        train_ratio: Fraction for training set.
        val_ratio: Fraction for validation set.
        test_ratio: Fraction for test set.
        seed: Random seed for reproducibility.

    Returns:
        (train, val, test) splits.
    """
    assert abs(train_ratio + val_ratio + test_ratio - 1.0) < 1e-6

    rng = random.Random(seed)

    # Group by label
    groups: dict[str, list[dict]] = {}
    for item in data:
        label = item.get(label_key, "unknown")
        groups.setdefault(label, []).append(item)

    train, val, test = [], [], []

    for label, items in groups.items():
        rng.shuffle(items)
        n = len(items)
        n_train = int(n * train_ratio)
        n_val = int(n * val_ratio)

        train.extend(items[:n_train])
        val.extend(items[n_train:n_train + n_val])
        test.extend(items[n_train + n_val:])

    rng.shuffle(train)
    rng.shuffle(val)
    rng.shuffle(test)

    logger.info(
        "Split: train=%d, val=%d, test=%d (total=%d)",
        len(train), len(val), len(test), len(data),
    )

    # Log class distribution
    for split_name, split_data in [("train", train), ("val", val), ("test", test)]:
        dist = Counter(item.get(label_key, "unknown") for item in split_data)
        logger.info("  %s: %s", split_name, dict(dist))

    return train, val, test


def save_splits(
    train: list[dict],
    val: list[dict],
    test: list[dict],
    output_dir: str,
    prefix: str = "data",
):
    """Save splits to JSON files."""
    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)

    for name, data in [("train", train), ("val", val), ("test", test)]:
        path = out / f"{prefix}_{name}.json"
        with open(path, "w") as f:
            json.dump(data, f, indent=2)
        logger.info("Saved %d items to %s", len(data), path)
