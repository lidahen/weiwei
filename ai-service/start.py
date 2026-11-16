"""邻里二手 AI Service 启动入口。

用法：
    python start.py                    # 默认 0.0.0.0:8001
    python start.py --port 9000        # 指定端口
    python start.py --reload           # 开发模式（自动重载）

依赖：uvicorn（requirements.txt 已包含）
配置：.env（当前目录，已预填示例值，请按实际修改）
"""
import sys
import uvicorn

HOST = "0.0.0.0"
DEFAULT_PORT = 8001


def _parse_args(argv: list) -> tuple:
    """返回 (host, port, reload)。"""
    host = HOST
    port = DEFAULT_PORT
    reload = False

    i = 1
    while i < len(argv):
        arg = argv[i]
        if arg in ("-p", "--port") and i + 1 < len(argv):
            try:
                port = int(argv[i + 1])
            except ValueError:
                print(f"[ERROR] 无效端口: {argv[i + 1]}")
                sys.exit(1)
            i += 2
        elif arg in ("-h", "--host") and i + 1 < len(argv):
            host = argv[i + 1]
            i += 2
        elif arg == "--reload":
            reload = True
            i += 1
        else:
            print(f"[WARN] 未知参数: {arg}（忽略）")
            i += 1

    return host, port, reload


def main():
    host, port, reload = _parse_args(sys.argv)

    print("=" * 60)
    print("  邻里二手 AI Service")
    print(f"  Host:   {host}")
    print(f"  Port:   {port}")
    print(f"  Reload: {reload}")
    print("=" * 60)

    uvicorn_kwargs = dict(
        host=host,
        port=port,
        reload=reload,
    )

    if reload:
        # 开发模式需要指定目录，确保修改任意 .py 文件都触发热重载
        uvicorn_kwargs["reload_dirs"] = ["."]

    uvicorn.run("app.main:app", **uvicorn_kwargs)


if __name__ == "__main__":
    main()