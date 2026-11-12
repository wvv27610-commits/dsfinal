# dsfinal 搜尋專案

輸入關鍵字 → 向搜尋服務（Google、Brave…）拿結果 → 用**自己寫的計分算法**重新排序 → 顯示在網頁上。

不需要開瀏覽器自動化，全部透過 API 取得結果。

---

## 目錄

1. [需要準備的東西](#1-需要準備的東西)
2. [申請 API key](#2-申請-api-key)
3. [把 key 放進專案](#3-把-key-放進專案)
4. [啟動程式](#4-啟動程式)
5. [專案結構](#5-專案結構)
6. [修改計分算法](#6-修改計分算法)
7. [新增其他搜尋來源](#7-新增其他搜尋來源)
8. [上傳到 GitHub（GitHub Desktop）](#8-上傳到-githubgithub-desktop)
9. [常見問題](#9-常見問題)

---

## 1. 需要準備的東西

- **Java 21 以上**（在終端機輸入 `java -version` 確認）
- **VS Code**，並安裝 *Extension Pack for Java* 和 *Spring Boot Extension Pack*（或用 IntelliJ）
- 至少一把搜尋服務的 **API key**（下一步教你申請）

Maven 不用另外裝，專案裡的 `mvnw` 會自動處理。

---

## 2. 申請 API key

API key 是搜尋服務給你的一串「通行密碼」，程式用它去拿搜尋結果。
**兩個選一個就能用**，推薦先用 SerpApi。

### SerpApi（Google 搜尋結果）— 推薦

1. 到 <https://serpapi.com> 按 **Register** 註冊（Email 或 Google 帳號）
2. 去信箱收驗證信（可能還要驗證手機）
3. 登入後打開 <https://serpapi.com/manage-api-key>，複製 **Private API Key**

免費方案每月有固定搜尋次數，每搜尋一次用掉 1 次，剩餘次數在 Dashboard 看得到。

### Brave Search API（選用，第二個來源）

1. 到 <https://brave.com/search/api/> 註冊
2. 選免費方案（可能要填信用卡驗證，免費方案不會扣款）
3. 在 Dashboard 的 **API Keys** 建立並複製 key

---

## 3. 把 key 放進專案

1. 在專案資料夾（和 `pom.xml` 同一層）找到 `.env.example`
2. **複製一份，改名為 `.env`**（前面有一個點，沒有 `.txt`）
3. 打開 `.env`，把 key 貼在等號後面（不要加引號）：

   ```
   SERPAPI_KEY=你複製的那串key
   BRAVE_API_KEY=
   ```

   沒申請的就留空，程式會自動略過那個來源。

> ⚠️ **key 等於密碼**：不要寫進程式碼、不要截圖、不要貼到群組。
> `.env` 已經被 `.gitignore` 排除，上傳 GitHub 時不會被傳上去。
> 組員請各自申請，或私下傳給他們放進自己的 `.env`。

---

## 4. 啟動程式

**方法 A：VS Code**

打開 `src/main/java/com/example/dsfinal/DsfinalApplication.java`，按 `main` 上方的 **Run**。

**方法 B：終端機**（在專案資料夾執行）

```bash
./mvnw spring-boot:run
```

Windows 的 cmd / PowerShell 用 `mvnw.cmd spring-boot:run`。

看到 `Started DsfinalApplication` 後，用瀏覽器打開 <http://localhost:8080> 就可以搜尋了。
每筆結果下方會顯示 `score`、`來源`、`原名次`。

---

## 5. 專案結構

```
src/main/java/com/example/dsfinal/
├── controller/SearchController.java   網址 /api/search 的入口
├── service/SearchService.java         流程：搜尋 → 合併 → 計分 → 排序
├── provider/                          ★ 搜尋來源
│   ├── SearchProvider.java              接口（所有來源都要實作它）
│   ├── SerpApiGoogleProvider.java       Google（透過 SerpApi）
│   └── BraveSearchProvider.java         Brave
├── scoring/                           ★ 計分算法
│   ├── ResultScorer.java                接口
│   ├── MyScorer.java                    ← 你自己的算法寫這裡
│   ├── DefaultScorer.java               簡單版算法
│   └── TextUtil.java                    小工具（算關鍵字出現次數）
└── model/                             資料格式（SearchResult、SearchResponse）

src/main/resources/
├── application.properties             設定檔
└── static/                            網頁（index.html、app.js、style.css）
```

搜尋流程：

1. 所有有設定 key 的來源**同時**搜尋
2. 同一個網址出現在多個來源 → **合併成一筆**
3. 用計分算法幫每一筆算分數
4. 分數高的排前面

某個來源失敗時，網頁會顯示警告，其他來源的結果照常顯示。

---

## 6. 修改計分算法

打開 [`scoring/MyScorer.java`](src/main/java/com/example/dsfinal/scoring/MyScorer.java)，改 `score()` 方法。存檔後重新搜尋就會看到新的排序。

在 `score()` 裡可以用的資料：

| 寫法 | 意思 |
|---|---|
| `query` | 使用者輸入的關鍵字 |
| `r.title()` | 標題 |
| `r.snippet()` | 摘要 |
| `r.url()` | 網址 |
| `r.sources()` | 從哪些來源找到，例如 `["google", "brave"]` |
| `r.rank()` | 在原本搜尋引擎的名次（1 = 第一名） |
| `TextUtil.count(文字, 關鍵字)` | 關鍵字在文字裡出現幾次 |

目前的範例算法：

- 關鍵字在**標題**出現一次 +3 分，在**摘要**出現一次 +1 分
- 自訂關鍵字權重（把註解打開就能用）：
  ```java
  KEYWORD_WEIGHTS.put("教學", 2.0);    // 出現「教學」每次 +2
  KEYWORD_WEIGHTS.put("廣告", -5.0);   // 出現「廣告」每次 -5
  ```
- 原本名次越前面加越多（第 1 名 +1、第 10 名 +0.1）
- 多個來源都找到同一個網址 → 每多一個來源 +2

**切換算法**：網頁搜尋框下方的「計分算法」選單可以直接切換（會記在網址 `?scorer=id`）。
預設選哪一個，在 `application.properties` 改這一行：

```properties
search.scorer=my        # my = MyScorer，default = DefaultScorer
```

**新增自己的算法**：在 `scoring` 套件新增一個 class 實作 `ResultScorer`，加上 `@Component("名字")`，
選單就會自動出現它。可以覆寫 `displayName()`、`description()` 設定選單上顯示的名稱和說明：

```java
@Component("title-only")
public class TitleOnlyScorer implements ResultScorer {
    @Override
    public double score(ScoringContext ctx, SearchResult r) {
        return TextUtil.count(r.title(), ctx.query().raw().trim());
    }
    @Override public String displayName() { return "只看標題"; }
    @Override public String description() { return "只計算查詢詞在標題出現的次數"; }
}
```

---

## 7. 新增其他搜尋來源

1. 在 `provider/` 資料夾新增一個 class，例如 `MySearchProvider.java`
2. `implements SearchProvider`，並在 class 上面加 `@Component`
3. 實作 3 個方法：

```java
@Component
public class MySearchProvider implements SearchProvider {

    public String name() {
        return "my-source";               // 顯示在結果上的來源名稱
    }

    public boolean isEnabled() {
        return true;                      // 通常寫成「有 API key 才 true」
    }

    public List<SearchResult> search(String query) throws Exception {
        List<SearchResult> results = new ArrayList<>();
        // 呼叫搜尋 API，把每一筆結果加進去，名次從 1 開始：
        // results.add(SearchResult.of(標題, 網址, 摘要, name(), 名次));
        return results;
    }
}
```

不用改其他檔案，程式啟動時會自動找到它。
怎麼呼叫 API、讀 JSON，可以直接複製 `BraveSearchProvider.java` 來改。

新來源需要 API key 的話：

1. 在 `.env` 加一行 `MY_KEY=...`
2. 在 `application.properties` 加一行 `my.key=${MY_KEY:}`
3. 在 class 裡用 `@Value("${my.key:}") String apiKey` 讀取

---

## 8. 上傳到 GitHub（GitHub Desktop）

### 第一次上傳

1. 下載安裝 <https://desktop.github.com>，打開後選 **Sign in to GitHub.com** 登入
2. **File → Add local repository…** → 選這個專案資料夾
3. 出現「does not appear to be a Git repository」→ 點 **create a repository**
   - **Git ignore 選 None、License 選 None**（專案已經有 `.gitignore`，選了會被蓋掉）
   - 按 **Create repository**
4. 看左邊 **Changes** 清單：**確認裡面沒有 `.env`**（有的話先停下來，不然 key 會被傳上去）
5. 左下角 Summary 填 `Initial commit` → 按 **Commit to main**
6. 上方按 **Publish repository**
   - 要公開就取消勾選 **Keep this code private**
   - 按 **Publish repository**
7. **Repository → View on GitHub** 就能在網頁上看到

### 之後每次修改

1. 改完程式存檔，GitHub Desktop 的 **Changes** 會自動列出改過的檔案（綠色 = 新增、紅色 = 刪除）
2. 填 Summary（例如 `修改計分算法`）→ **Commit to main**
3. 按 **Push origin**

### 常用功能

| 功能 | 用途 |
|---|---|
| **History** 分頁 | 看以前每次 commit 改了什麼 |
| 檔案按右鍵 → **Discard changes** | 放棄這個檔案還沒 commit 的修改 |
| **Fetch origin / Pull origin** | 拿組員推上去的修改（開始改之前先做，比較不會衝突） |
| GitHub 網頁 → Settings → **Collaborators** | 邀請組員 |
| **File → Clone repository** | 組員把專案下載到自己的電腦 |

---

## 9. 常見問題

**Q：搜尋時顯示「沒有可用的搜尋來源」**
程式沒讀到 key。確認：
- `.env` 和 `pom.xml` 在同一層
- 檔名是 `.env`，不是 `.env.txt`（Windows 檔案總管要打開「副檔名」顯示才看得到）
- 等號後面有貼上 key、沒有多餘空白或引號
- 改完 `.env` 後有**重新啟動**程式

**Q：顯示「部分來源失敗：…」**
那個來源出問題（key 錯誤、免費次數用完…），其他來源的結果還是會顯示。到該服務的 Dashboard 檢查 key 和剩餘次數。

**Q：`search.scorer=xxx 找不到`**
`application.properties` 裡 `search.scorer` 的名字要和 `@Component("名字")` 一樣，目前可用的是 `my` 和 `default`。

**Q：網頁打不開**
確認程式有啟動成功（看到 `Started DsfinalApplication`），網址是 <http://localhost:8080>。
