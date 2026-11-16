import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app


@pytest.mark.asyncio
async def test_chat_endpoint_exists():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/ai/chat", json={"messages": [{"role": "user", "content": "你好"}]}
        )
    assert response.status_code != 404
    assert response.status_code != 405


@pytest.mark.asyncio
async def test_chat_endpoint_empty_messages():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post("/ai/chat", json={"messages": []})
    assert response.status_code == 200
    data = response.json()
    assert data["code"] == 400


@pytest.mark.asyncio
async def test_chat_stream_endpoint_exists():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.post(
            "/ai/chat/stream",
            json={"messages": [{"role": "user", "content": "你好"}]},
        )
    assert response.status_code != 404
    assert response.status_code != 405
