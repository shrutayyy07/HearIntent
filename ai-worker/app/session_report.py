import os
import threading
from datetime import datetime, timezone

from app.config import config

_write_lock = threading.Lock()


def _report_path(session_id: str) -> str:
    safe_id = "".join(c for c in session_id if c.isalnum() or c in ("-", "_"))
    session_dir = os.path.join(config.log_dir, "sessions")
    os.makedirs(session_dir, exist_ok=True)
    return os.path.join(session_dir, f"session_{safe_id}.md")


def init_session_report(session_id: str) -> None:
    path = _report_path(session_id)
    if os.path.exists(path):
        return
    with _write_lock:
        with open(path, "w", encoding="utf-8") as f:
            f.write(f"# Session Report: {session_id}\n\n")
            f.write(f"- Started: {datetime.now(timezone.utc).isoformat()}\n")
            f.write(f"- Worker version: {config.worker_version}\n\n")
            f.write("## Transcript & Intent Timeline\n\n")
            f.write("| Time (UTC) | Type | Content |\n")
            f.write("|---|---|---|\n")


def append_session_event(session_id: str, event_type: str, content: str) -> None:
    path = _report_path(session_id)
    if not os.path.exists(path):
        init_session_report(session_id)
    timestamp = datetime.now(timezone.utc).strftime("%H:%M:%S")
    escaped = content.replace("|", "\\|").replace("\n", " ")
    with _write_lock:
        with open(path, "a", encoding="utf-8") as f:
            f.write(f"| {timestamp} | {event_type} | {escaped} |\n")


def finalize_session_report(session_id: str, summary: str) -> None:
    path = _report_path(session_id)
    with _write_lock:
        with open(path, "a", encoding="utf-8") as f:
            f.write("\n## Summary\n\n")
            f.write(summary + "\n")
            f.write(f"\n- Ended: {datetime.now(timezone.utc).isoformat()}\n")
