"""Check refusal paths in an isolated temporary project; never changes live PID records."""

import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class LifecycleTest(unittest.TestCase):
    def test_partial_state_is_not_healthy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "scripts").mkdir()
            (root / ".runtime").mkdir()
            shutil.copyfile(ROOT / "scripts/dev.py", root / "scripts/dev.py")
            (root / ".runtime/services.json").write_text(
                json.dumps({"mock": {"pid": 1}})
            )
            result = subprocess.run(
                ["python3", str(root / "scripts/dev.py"), "status"],
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("不完整", result.stderr)

    def test_port_conflict_does_not_touch_listener(self):
        with tempfile.TemporaryDirectory() as directory, socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            listener.listen()
            root = Path(directory)
            (root / "scripts").mkdir()
            shutil.copyfile(ROOT / "scripts/dev.py", root / "scripts/dev.py")
            (root / ".env").write_text("DB_USERNAME=unused\nDB_PASSWORD=unused\n")
            result = subprocess.run(
                ["python3", str(root / "scripts/dev.py"), "start"],
                env={**os.environ, "MOCK_PORT": str(listener.getsockname()[1])},
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("被占用", result.stderr)
            self.assertGreater(listener.fileno(), -1)


if __name__ == "__main__":
    unittest.main()
