"""文档文本提取：下载文件并解析为纯文本。

支持格式：
- 纯文本类：txt / md / csv / json / log 等（UTF-8 / GB18030 / UTF-16 自动识别）
- PDF：pypdf
- Word：python-docx

后续扩展：在 _PARSERS 中注册新的解析函数即可支持更多格式（epub、xlsx 等）。
"""
import io
import logging

import httpx

from app.config import settings

logger = logging.getLogger(__name__)

_TEXT_EXTENSIONS = {"txt", "md", "csv", "json", "log", "html", "htm", "xml", "yaml", "yml"}


def _parse_pdf(data: bytes) -> str:
    from pypdf import PdfReader

    reader = PdfReader(io.BytesIO(data))
    pages = []
    for page in reader.pages:
        text = page.extract_text() or ""
        pages.append(text)
    return "\n".join(pages)


def _parse_docx(data: bytes) -> str:
    from docx import Document

    doc = Document(io.BytesIO(data))
    parts = [p.text for p in doc.paragraphs if p.text]
    # 表格内容
    for table in doc.tables:
        for row in table.rows:
            cells = [c.text for c in row.cells]
            parts.append(" | ".join(cells))
    return "\n".join(parts)


def _parse_text(data: bytes) -> str:
    """按多种编码尝试解码纯文本。"""
    for encoding in ("utf-8", "gb18030", "utf-16"):
        try:
            return data.decode(encoding)
        except (UnicodeDecodeError, LookupError):
            continue
    return data.decode("utf-8", errors="ignore")


_PARSERS = {
    "pdf": _parse_pdf,
    "docx": _parse_docx,
}


def _resolve_url(file_url: str) -> str:
    """相对路径拼接静态资源前缀，完整 URL 原样返回。"""
    if file_url.startswith("http://") or file_url.startswith("https://"):
        return file_url
    base = settings.static_base_url.rstrip("/")
    return f"{base}/{file_url.lstrip('/')}"


async def fetch_and_extract(file_url: str, file_name: str = "") -> str:
    """下载文件并提取文本内容。"""
    url = _resolve_url(file_url)
    try:
        async with httpx.AsyncClient(
            timeout=settings.doc_download_timeout, follow_redirects=True
        ) as client:
            resp = await client.get(url)
            resp.raise_for_status()
            data = resp.content
    except Exception as e:
        logger.error("download file failed: url=%s error=%s", url, e)
        raise RuntimeError(f"文件下载失败: {e}")

    if not data:
        return ""

    ext = ""
    if file_name and "." in file_name:
        ext = file_name.lower().rsplit(".", 1)[-1]

    if ext == "pdf":
        return _parse_pdf(data)
    if ext == "docx":
        return _parse_docx(data)
    if ext in _TEXT_EXTENSIONS or not ext:
        return _parse_text(data)

    # 未知扩展名：先按文本尝试
    logger.warning("unsupported file extension: %s, try as text", file_name)
    return _parse_text(data)
