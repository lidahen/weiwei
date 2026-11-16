"""RAG 服务单元测试（不依赖数据库）。"""

from app.schemas.common import ApiResponse
from app.schemas.doc import DocCreate
from app.schemas.knowledge import KnowledgeResponse
from app.services.rag_service import split_text


class TestSplitText:
    def test_empty(self):
        assert split_text("") == []
        assert split_text("   ") == []

    def test_short_text(self):
        text = "你好，世界"
        assert split_text(text, 3000) == [text]

    def test_long_text_chunks(self):
        text = "这是一个用于测试切片功能的段落。\n" * 500
        chunks = split_text(text, 100)
        assert len(chunks) > 1
        # 每个切片不超过 slice_size
        assert all(len(c) <= 100 for c in chunks)
        # 拼接后（含重叠）应覆盖原文全部内容
        joined = "".join(chunks)
        assert len(joined) >= len(text.strip())
        assert text.strip()[:50] in joined
        assert text.strip()[-50:] in joined

    def test_custom_slice_size(self):
        text = "abcdefghijklmnopqrstuvwxyz"
        chunks = split_text(text, 10)
        assert len(chunks) >= 2


class TestCamelSerialization:
    def test_knowledge_response_aliases(self):
        resp = KnowledgeResponse(
            knowledge_id=1,
            knowledge_name="测试库",
            welcome_msg="你好",
            multi_round=5,
            top_k=3,
            score=0.5,
            slice_size=3000,
        )
        dumped = resp.model_dump(by_alias=True)
        assert dumped["knowledgeId"] == 1
        assert dumped["knowledgeName"] == "测试库"
        assert dumped["welcomeMsg"] == "你好"
        assert dumped["multiRound"] == 5
        assert dumped["topK"] == 3
        assert dumped["sliceSize"] == 3000

    def test_doc_create_accepts_camel_case(self):
        body = DocCreate(
            knowledgeId=2,
            docName="手册",
            sourceType="UPLOAD",
            fileList=[{"fileName": "a.pdf", "fileUrl": "https://x/a.pdf"}],
        )
        assert body.knowledge_id == 2
        assert body.file_list[0].file_name == "a.pdf"

    def test_api_response_defaults(self):
        resp = ApiResponse(data={"records": [], "total": 0})
        assert resp.code == 200
        assert resp.msg == "success"
        assert resp.data == {"records": [], "total": 0}
