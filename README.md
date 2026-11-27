# goo狗 搜尋狗專案

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
10. [多語言與身份](#10-多語言與身份)
11. [連結樹（連結分數 L）](#11-連結樹連結分數-l)

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

---

## 10. 多語言與身份

網頁右上角可以切換**語言**（目前有繁體中文、English）。切換後會一起換掉：

- 網頁上的文字
- 身份的名稱、說明，以及**搜尋時附加的字**（英文不會再加上中文的「狗」）
- 計分用的關鍵字：中文關鍵字 + 該語言的關鍵字一起算（網頁常常中英混寫）
- 送給 Google / Brave 的語言參數（英文版會拿英文結果，但地區仍是台灣）

語言會記在網址 `?lang=en`，也會記在瀏覽器裡，下次打開還是同一個語言。

### 相關檔案

| 檔案 | 內容 |
|---|---|
| `src/main/resources/profiles.json` | 所有身份（中文），**新增身份改這裡** |
| `src/main/resources/profiles.en.json` | 各身份的英文名稱、搜尋附加字、英文關鍵字 |
| `src/main/resources/languages.json` | 支援哪些語言、送給搜尋服務的語言參數 |
| `src/main/resources/static/i18n/zh-TW.json`、`en.json` | 網頁上的文字、計分算法的英文名稱 |

### 新增一個身份

1. 在 `profiles.json` 的最後加一筆（記得前一筆後面要有逗號）：

   ```json
   {
     "id": "grooming",
     "name": "美容洗澡",
     "group": "daily",
     "description": "狗狗洗澡、剪毛、美容店資訊",
     "queryPrefix": "",
     "querySuffix": "狗 美容",
     "keywords": { "洗澡": 3, "剪毛": 3, "美容師": 2, "團購": -2 },
     "stopwords": ["熱狗", "犬儒", "狗屁", "走狗"]
   }
   ```

   `group` 是這個身份要放在哪一組，可以用的組別寫在 `profiles.json` 最上面的 `groups`（目前有 `daily` 日常照顧、`health` 健康醫療、`adopt-travel` 領養與外出、`knowledge` 知識與其他）。
   要新增一組，就在 `groups` 加一筆 `{ "id": "...", "name": "..." }`，並在 `profiles.en.json` 的 `groups` 加上英文組名。沒寫 `group` 的身份會放在最後的「其他」。

2. 在 `profiles.en.json` 加同一個 id 的英文版（只寫要翻譯的欄位）：

   ```json
   "grooming": {
     "name": "Grooming",
     "description": "Bathing, haircuts and grooming salons",
     "querySuffix": "dog grooming",
     "keywords": { "bath": 3, "haircut": 3, "groomer": 2 }
   }
   ```

   沒寫英文版也能用，英文介面會在按鈕上標「not translated yet」，並沿用中文的關鍵字和附加字。

### 英文關鍵字的比對規則

- 不分大小寫
- 要是**完整的單字**才算：`cat` 不會在 category 裡被算到，`dog` 不會在 hotdog 裡被算到
- 字尾可以多一個 s 或 es：`vaccine` 也會算到 vaccines。但 puppy → puppies、allergy → allergies 這種變化要另外列
- 要排除的片語寫在 `stopwords`，例如 `"hot dog"`

### 新增一種語言（例如日文）

1. `languages.json` 加一筆：`{"code": "ja", "label": "日本語", "googleHl": "ja", "googleGl": "tw", "braveLang": "jp", "braveCountry": "tw"}`
2. 新增 `profiles.ja.json`（格式和 `profiles.en.json` 一樣）
3. 複製 `static/i18n/en.json` 成 `ja.json` 並翻譯

不用改任何 Java 程式。

### 測試

```bash
./mvnw test
```

會檢查關鍵字比對規則，以及每個身份都有英文翻譯、英文的搜尋附加字裡沒有中文。

---

## 11. 連結樹（連結分數 L）

「Goo狗 評分公式」會幫每筆結果建一棵**連結樹**，用來算連結分數 L（提案第 2 節第三點）。
搜尋結果下方點「連結樹」就能展開來看。

**怎麼建樹**（`crawler/LinkTreeBuilder.java`）

1. 根節點是搜尋結果的網頁本身（第 0 層）
2. 用 **BFS** 一層一層展開：**Queue** 放等著展開的網頁，**HashSet visited** 記錄看過的網址，連回首頁或互相連結也不會重複
3. 只收**同網站**的網頁連結（圖片、PDF 等檔案跳過）
4. 第 1 層的網頁會下載，用來找第 2 層；第 2 層只看連結文字，不下載
5. 連結文字或網址裡有身份的正權重關鍵字，就算「相關連結」

**公式**

```
L = L_max × （相關連結的 1/d 總和）÷（所有連結的 1/d 總和）
```

d 是連結在第幾層，第 1 層權重 1、第 2 層權重 1/2。L 一定介於 0 到 L_max。

**樹的節點裡有什麼**（`crawler/LinkNode.java`）：網址、連結文字、層數、有沒有相關、對到哪些關鍵字；有下載的網頁還會記錄每個關鍵字出現幾次。

**調整速度與準確度**（`application.properties`）

| 設定 | 預設 | 意思 |
|---|---|---|
| `scoring.tree.max-depth` | 2 | 最多展開幾層 |
| `scoring.tree.max-links-per-page` | 30 | 每頁最多記錄幾個連結 |
| `scoring.tree.expand-per-page` | 3 | 每頁最多下載幾個子網頁去找下一層，**搜尋太慢就調小** |
| `scoring.tree.cache-size` | 256 | 最多記住幾棵樹（LRU 快取，超過就丟掉最久沒用的） |

測試在 `src/test/java/.../crawler/LinkTreeBuilderTest.java`，用一個假的小網站驗證樹的形狀和 L 的手算結果（2.5 ÷ 6.5）。

