import json
import locale
import subprocess
import sys
from pathlib import Path


REPO_ROOT = Path(r"F:\Context")
SCRIPT_PATH = REPO_ROOT / "_gitstatus_check.py"
PREFERRED_ENCODING = locale.getpreferredencoding(False)
INTERPRETERS = [
    "python",
    "python3",
    "py",
    str(Path(r"C:\Users\ASUS\AppData\Local\Programs\Python\Python311\python.exe")),
    str(Path(r"C:\Windows\py.exe")),
]


def read_message():
    headers = {}
    while True:
        line = sys.stdin.buffer.readline()
        if not line:
            return None
        if line in (b"\r\n", b"\n"):
            break
        key, value = line.decode("ascii").split(":", 1)
        headers[key.strip().lower()] = value.strip()
    length = int(headers["content-length"])
    body = sys.stdin.buffer.read(length)
    return json.loads(body.decode("utf-8"))


def send_message(message):
    payload = json.dumps(message, ensure_ascii=False).encode("utf-8")
    sys.stdout.buffer.write(f"Content-Length: {len(payload)}\r\n\r\n".encode("ascii"))
    sys.stdout.buffer.write(payload)
    sys.stdout.buffer.flush()


def run_command(command):
    try:
        completed = subprocess.run(
            command,
            cwd=str(REPO_ROOT),
            capture_output=True,
            text=True,
            encoding=PREFERRED_ENCODING,
            errors="replace",
        )
        return {
            "command": command,
            "exit_code": completed.returncode,
            "stdout": completed.stdout,
            "stderr": completed.stderr,
        }
    except FileNotFoundError as exc:
        return {
            "command": command,
            "exit_code": None,
            "stdout": "",
            "stderr": f"{type(exc).__name__}: {exc}\n",
        }


def detect_interpreter():
    attempts = []
    seen = set()
    for candidate in INTERPRETERS:
        if candidate in seen:
            continue
        seen.add(candidate)
        result = run_command([candidate, "--version"])
        attempts.append(result)
        if result["exit_code"] == 0:
            return candidate, attempts
    return None, attempts


def format_result(selected, exit_code, stdout, stderr, attempts):
    parts = [
        f"Selected interpreter: {selected if selected is not None else 'NONE'}",
        f"Exit code: {exit_code if exit_code is not None else 'NONE'}",
        "--- STDOUT BEGIN ---",
        stdout,
        "--- STDOUT END ---",
        "--- STDERR BEGIN ---",
        stderr,
        "--- STDERR END ---",
        "--- ATTEMPTS BEGIN ---",
    ]
    for attempt in attempts:
        command_text = subprocess.list2cmdline(attempt["command"])
        parts.extend(
            [
                f"Command: {command_text}",
                f"Exit code: {attempt['exit_code'] if attempt['exit_code'] is not None else 'NONE'}",
                "STDOUT:",
                attempt["stdout"],
                "STDERR:",
                attempt["stderr"],
                "---",
            ]
        )
    parts.append("--- ATTEMPTS END ---")
    return "\n".join(parts)


def handle_tool_call():
    selected, attempts = detect_interpreter()
    if selected is None:
        return format_result(None, None, "", "", attempts)
    execution = run_command([selected, str(SCRIPT_PATH)])
    return format_result(
        selected,
        execution["exit_code"],
        execution["stdout"],
        execution["stderr"],
        attempts,
    )


def main():
    while True:
        message = read_message()
        if message is None:
            break

        message_id = message.get("id")
        method = message.get("method")

        if method == "initialize":
            result = {
                "protocolVersion": message.get("params", {}).get("protocolVersion", "2024-11-05"),
                "capabilities": {"tools": {}},
                "serverInfo": {"name": "gitstatus-runner", "version": "1.0.0"},
            }
            send_message({"jsonrpc": "2.0", "id": message_id, "result": result})
        elif method == "tools/list":
            result = {
                "tools": [
                    {
                        "name": "run_gitstatus_script",
                        "description": "Run F:\\Context\\_gitstatus_check.py and return stdout/stderr.",
                        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
                    }
                ]
            }
            send_message({"jsonrpc": "2.0", "id": message_id, "result": result})
        elif method == "tools/call":
            tool_name = message.get("params", {}).get("name")
            if tool_name != "run_gitstatus_script":
                send_message(
                    {
                        "jsonrpc": "2.0",
                        "id": message_id,
                        "error": {"code": -32601, "message": f"Unknown tool: {tool_name}"},
                    }
                )
                continue
            output = handle_tool_call()
            result = {"content": [{"type": "text", "text": output}], "isError": False}
            send_message({"jsonrpc": "2.0", "id": message_id, "result": result})
        elif method == "ping":
            send_message({"jsonrpc": "2.0", "id": message_id, "result": {}})
        elif method == "notifications/initialized":
            continue
        elif message_id is not None:
            send_message(
                {
                    "jsonrpc": "2.0",
                    "id": message_id,
                    "error": {"code": -32601, "message": f"Unsupported method: {method}"},
                }
            )


if __name__ == "__main__":
    main()
