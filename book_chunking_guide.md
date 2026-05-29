# 书本章节切分指导文档

> 适用于 Codex 处理各类书本格式时的章节边界识别与切分策略。
> 核心原则：**优先使用格式内置结构，逐级回退到启发式方法。**

---

## 总体回退优先级（所有格式通用）

```
L0 原生 TOC/样式  →  L1 视觉/字体特征  →  L2 正则模式  →  L3 语义嵌入  →  L4 LLM 判断
```

优先级高的方法准确率接近 100%，优先级低的方法误报率高、成本也高。**不要跳过 L0 直接用 L2/L3。**

---

## 一、EPUB（优先级最高的格式，内置完整章节结构）

### EPUB 内置了什么

EPUB 本质是 ZIP 压缩包，包含以下结构文件：

```
book.epub (ZIP)
├── META-INF/container.xml       ← 入口，指向 OPF 文件路径
└── OEBPS/
    ├── content.opf              ← manifest（文件清单）+ spine（阅读顺序）
    ├── toc.ncx                  ← EPUB 2 目录：XML navMap → navPoint 层级树
    ├── nav.xhtml                ← EPUB 3 目录：HTML <nav epub:type="toc">
    ├── chapter01.xhtml          ← 正文文件
    └── chapter02.xhtml
```

**toc.ncx 和 nav.xhtml 直接提供章节标题 + 起始位置，无需任何推断。**

### 切分流程

1. 将 `.epub` 重命名为 `.zip` 解压
2. 读取 `META-INF/container.xml`，找到 OPF 文件路径
3. 解析 `content.opf`：
   - `<spine>` → 有序文件列表（阅读顺序）
   - `<manifest>` → 文件 ID 到路径的映射
4. **优先**读取 `nav.xhtml`（EPUB3）或 `toc.ncx`（EPUB2），提取 `标题 + href（文件名#锚点）`
5. 按 href 定位到对应 xhtml 文件及段落锚点，即为章节切割点
6. **回退**：若 TOC 缺失或条目过少，按 Spine 顺序，每个 xhtml 文件视为一章

### 常见陷阱

| 问题 | 说明 |
|------|------|
| **多级嵌套 TOC** | navPoint 可嵌套（Part → Chapter → Section），需明确切到哪一级 |
| **TOC 与 Spine 不一致** | TOC 条目少于 Spine 文件数，前言/版权页会漏掉，需补充 |
| **单文件 EPUB** | 所有正文在同一个 xhtml 内，靠 `id` 锚点区分章节，不能按文件切，需用 `<h1>/<h2>` 标签 |
| **DRM 加密** | 解压后内容不可读，需给出明确错误信息而非输出乱码 |

---

## 二、数字版 PDF（有文本层，非扫描）

PDF 没有强制语义结构，可靠性取决于制作质量。按以下优先级依次尝试：

### 可用信号（按可靠性排序）

| 信号 | 可靠性 | 获取方式 |
|------|--------|----------|
| **PDF 书签 / Outline** | ★★★ 高 | `pymupdf: doc.get_toc()` 直接返回 `[level, title, page]` 列表 |
| **Tagged PDF (PDF/UA)** | ★★★ 高 | 结构树含 `<H1>/<H2>` 标签；`pymupdf get_toc()` 或 Adobe Extract API |
| **字体大小 + 粗体** | ★★☆ 中 | `pdfplumber/pymupdf` 提取每行字体 size；标题通常比正文大 2–4pt 且加粗 |
| **页眉规律** | ★★☆ 中 | 识别页面顶部固定位置的重复文本，通常含章节名 |
| **正则匹配** | ★★☆ 中 | `Chapter \d+` / `第[零一二三四五六七八九十百]+章` |
| **空白行 + 缩进** | ★☆☆ 低 | 误报极高，仅作最后兜底 |

### 工具选择

- **pymupdf4llm**：首选，0.12s，直接输出 Markdown 并自动识别标题层级
- **marker-pdf**：结构还原质量最高，但速度慢（~11s），适合高质量转换
- **unstructured**：1.3s，输出语义块，适合 RAG 流程
- **pypdf / pdfplumber**：速度快但无结构，需自行解析字体元数据

### Adobe Extract API 已知问题

标题识别会遗漏部分段落（已知 bug）。后处理建议：构建已识别 H1/H2 的字体属性 map，再扫描未标注段落做二次匹配。

---

## 三、TXT / Markdown

### Markdown

按标题层级直接切分，无歧义：

```
# H1 标题     → 顶级章节
## H2 标题    → 二级节
### H3 标题   → 三级节

下划线形式（Setext）：
标题文本
========   ← 等同 H1
--------   ← 等同 H2（注意：单独的 --- 也是水平线，需结合上下文判断）
```

### 纯文本 TXT

按优先级依次匹配：

| 优先级 | 模式 | 示例 |
|--------|------|------|
| 高 | 章节编号正则 | `Chapter 1`、`第一章`、`CHAPTER ONE`、`1. 标题` |
| 中 | 全大写独占行（前后有空行） | `INTRODUCTION` |
| 中 | 连续空行（≥2行） | 部分书籍用多空行替代标题 |
| 中 | 特殊分隔线 | `***`、`===`、`---`（独占一行） |
| 低 | 单数字/罗马数字独行 | `I`、`II`、`1`、`2`（假阳率高） |

**注意**：正文中可能引用"见第三章"，会造成正则误报，建议结合"独占行 + 前后空行"条件过滤。

---

## 四、DOCX / RTF

DOCX 含完整样式信息，可靠性仅次于 EPUB。

### 切分方法

```python
from docx import Document

doc = Document("book.docx")
chapters = []
for para in doc.paragraphs:
    style = para.style.name          # "Heading 1", "Heading 2", ...
    level = para.paragraph_format.outline_level   # 0=H1, 1=H2, None=正文
    if style.startswith("Heading") or (level is not None and level < 3):
        chapters.append({"title": para.text, "level": level, "style": style})
```

**注意**：部分作者使用自定义样式名（如"MyHeading"），需同时检查 `outline_level` 作为兜底。

RTF 格式：含 `\outlinelevel` 标记，建议先用 Calibre 或 LibreOffice 转为 DOCX 再处理。

---

## 五、MOBI / AZW（Kindle 格式）

MOBI/AZW 是亚马逊私有格式，无官方开源规范。

**推荐处理路径**（按优先级）：
1. 用 **Calibre**（`ebook-convert book.mobi book.epub`）转为 EPUB，再按 EPUB 策略处理
2. AZW3（KF8）本质是 EPUB 封装在 Mobipocket 容器内，可解包提取内部 HTML
3. 若只能处理原始 MOBI：用 `mobi` Python 库解压，提取 HTML，按 `<h1>...<h6>` 标签切分

**Kindle NCX 特殊要求**：NCX 中章节顺序必须与正文顺序严格一致，HTML TOC 应置于书的开头，否则影响"上次阅读位置"功能。

---

## 六、扫描版 PDF（无文本层）

扫描书籍无可直接读取的文本结构，必须经 OCR + 版面分析，误差率远高于数字格式。

### 处理流程

1. **版面检测**：用 LayoutParser / Surya / Tesseract（layout 模式）识别文本块、标题块位置
2. **OCR**：对标题候选区域单独 OCR（精度高于全文 OCR）；中文书推荐 **PaddleOCR**，英文推荐 Tesseract 5
3. **标题识别**：综合「字体像素高度 + 居中位置 + 与正文的垂直间距」判断
4. **正则验证**：对 OCR 结果匹配"第X章"/"Chapter N"二次确认
5. **置信度阈值**：低于阈值的候选点输出供人工审核，不要全量自动提交

**特殊情况**：
- 竖排版（日文/古籍）需额外处理文本方向
- DPI < 200 或有手写注解的扫描件，OCR 错误率极高，章节识别基本不可靠

---

## 七、格式 × 策略快速对照表

| 格式 | 首选方法 | 回退方法 | 最差情况 |
|------|---------|---------|---------|
| EPUB 2/3 | toc.ncx / nav.xhtml | Spine 文件分割 | HTML `<h1>` 正则 |
| 数字 PDF | PDF 书签 `get_toc()` | 字体大小分类 | 正则 + 语义切分 |
| 扫描 PDF | OCR + 版面分析 | 页码 + 空白间距 | LLM 判断 |
| DOCX | Heading 样式 / outline_level | 字体大小 | 正则 |
| TXT | 正则章节编号 | 空行聚类 | 语义切分 |
| Markdown | `#` ATX / Setext 标题 | `---` 分隔线 | 正则 |
| MOBI/AZW | 转 EPUB 再处理 | 提取 HTML 用 `<h*>` | 正则 |

---

## 八、粒度选择建议

| 使用场景 | 推荐粒度 | 说明 |
|---------|---------|------|
| RAG 检索 | H2 / 小节级（500–2000 token） | 太粗（H1整章）或太细（句子级）都影响检索质量 |
| 全文摘要 | H1 章节级 | 每章独立摘要后合并 |
| 叙事小说 | 章节内语义切分 | LumberChunker 风格，in-document 检索比固定切分好 7%+ |
| 教材/技术书 | 保留 H1→H2→H3 层级 | 每节附加父节标题作上下文前缀，避免孤立片段 |

---

## 九、切分效果差时的排查清单

- [ ] 确认输入格式：EPUB？数字 PDF？扫描版？
- [ ] EPUB：解压后是否读取了 `toc.ncx` 或 `nav.xhtml`？是否是单文件 EPUB？
- [ ] PDF：`doc.get_toc()` 是否返回了书签列表？返回为空才需要字体启发式
- [ ] TOC 存在但切分不准：检查 `href` 锚点解析是否正确（文件名 + `#id`）
- [ ] 正则不匹配：打印前 50 个"疑似标题行"，观察实际格式（注意全角/半角数字、中英文混排）
- [ ] 语义切分断点位置偏移：调小 embedding 距离阈值，或改用 LLM 逐组判断
- [ ] 单文件 EPUB：不能按 Spine 文件切，需用 `<h1>/<h2>` 标签或锚点切分
