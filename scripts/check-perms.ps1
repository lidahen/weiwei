# ============================================================================
# 权限漂移检查：后端 @SaCheckPermission 注解 ↔ sys_menu.perms
#
# 背景：admin 角色持有 "*" 超级权限，任何「代码要的权限菜单里没有」的漂移都会被
#       静默放行；一旦给普通角色授权就会 403。这个脚本把漂移显式暴露出来。
#
# 用法（仓库根目录）：
#   powershell -File scripts/check-perms.ps1
#   powershell -File scripts/check-perms.ps1 -DbUser root -DbPassword 123456
#
# 退出码：
#   0 = 一致
#   1 = 存在漂移（代码要求但菜单没有）
#   2 = 环境问题（找不到 mysql 客户端 / 连不上库）
# ============================================================================
param(
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$DbUser = 'root',
    [string]$DbPassword = '123456',
    [string]$DbName = 'twelvet-fast'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot 'backend'

if (-not (Test-Path $backendDir)) {
    Write-Host "找不到 backend 目录：$backendDir" -ForegroundColor Red
    exit 2
}

if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
    Write-Host "找不到 mysql 客户端，无法读取 sys_menu" -ForegroundColor Red
    exit 2
}

# 1) 代码侧：所有 @SaCheckPermission("xxx")
$codePerms = Get-ChildItem $backendDir -Recurse -Include *.java |
    Select-String -Pattern '@SaCheckPermission\("([^"]+)"\)' -AllMatches |
    ForEach-Object { $_.Matches } |
    ForEach-Object { $_.Groups[1].Value } |
    Sort-Object -Unique

# 2) 菜单侧：sys_menu.perms
$prevPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = $DbPassword
try {
    $menuPerms = & mysql "--host=$DbHost" "--port=$DbPort" "--user=$DbUser" `
        "--database=$DbName" --batch --skip-column-names `
        --execute="SELECT DISTINCT perms FROM sys_menu WHERE perms IS NOT NULL AND perms <> ''"
}
finally {
    $env:MYSQL_PWD = $prevPwd
}

if ($LASTEXITCODE -ne 0 -or -not $menuPerms) {
    Write-Host "读取 sys_menu 失败（$DbName @ ${DbHost}:$DbPort）" -ForegroundColor Red
    exit 2
}

$menuPerms = $menuPerms | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Sort-Object -Unique

# 3) 差集
$missing = $codePerms | Where-Object { $menuPerms -notcontains $_ }
$menuOnly = $menuPerms | Where-Object { $codePerms -notcontains $_ }

Write-Host ("代码权限 {0} 个 / 菜单权限 {1} 个" -f $codePerms.Count, $menuPerms.Count)

if ($missing) {
    Write-Host ""
    Write-Host ("漂移 {0} 个（代码要求、菜单缺失 —— 非超管角色调用会 403）：" -f $missing.Count) -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    Write-Host ""
    Write-Host "处理方式：菜单侧补按钮权限，或把注解改成菜单已存在的权限（二者选一，别用兜底通配符）。"
    exit 1
}

Write-Host "无漂移 ✅" -ForegroundColor Green

# 菜单独有权限仅作提示：C 端页面级 perms 用于侧边栏可见性，不一定有接口校验
if ($menuOnly) {
    Write-Host ("（提示：{0} 个菜单权限没有对应注解，多为 C 端页面可见性用）" -f $menuOnly.Count) -ForegroundColor DarkGray
}
exit 0
