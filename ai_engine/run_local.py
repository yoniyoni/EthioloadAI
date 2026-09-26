"""Start the local AI service using dependencies installed in .runtime."""
from pathlib import Path
import sys

service_directory = Path(__file__).resolve().parent
sys.path.insert(0, str(service_directory / ".runtime"))
sys.path.insert(0, str(service_directory.parent))

if __name__ == "__main__":
    import uvicorn

    uvicorn.run("ai_engine.main:app", host="127.0.0.1", port=8000)
