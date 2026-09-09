import uvicorn

from equipment_agent.api.app import create_app
from equipment_agent.config.settings import load_settings


app = create_app()


if __name__ == "__main__":
    uvicorn.run(
        "equipment_agent.__main__:app",
        host="0.0.0.0",
        port=load_settings().agent_port,
        reload=False,
    )
