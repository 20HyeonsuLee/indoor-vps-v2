# Legacy Python Bridge Runtime

This folder vendors the Python code still used by the Java bridge.

Java owns HTTP, persistence, storage bookkeeping, and build-job lifecycle. This
runtime remains only for Python-only workloads:

- `localize`: SuperPoint/LightGlue SLAM localization code under
  `src/indoor_server/application/slam`
- `merge_scan`: RTAB-Map multi-scan reprocess wrapper under
  `src/indoor_server/application/building/multiscan_rtabmap_merge.py`

Legacy build pipeline code that generated route graphs from walkable-grid
skeletons is intentionally not included. Java now builds route graph rows from
RTAB-Map `Node`/`Link` SQLite artifacts.

Spring defaults `indoor.python.backend-source` to `./python/legacy_backend/src`.
Override with `PYTHON_BACKEND_SRC` only when testing another checkout.

Device selection is explicit:

- local/default: `INDOOR_ML_DEVICE=cpu`
- prod profile: `INDOOR_ML_DEVICE=cuda`

The Python code does not auto-upgrade to CUDA based on
`torch.cuda.is_available()`.
