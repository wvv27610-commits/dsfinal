package com.example.dsfinal.profile;

import java.util.List;
import java.util.Map;

/**
 * 一個「身份」：例如「寵物主人」「生物知識」，每個身份對應一組關鍵字和權重。
 * 內容寫在 src/main/resources/profiles.json，要新增身份只要改那個檔案。
 *
 * @param id          英文代號，網址和前端用（例如 pet-owner）
 * @param name        顯示名稱（例如 寵物主人）
 * @param description 一句話說明
 * @param keywords    關鍵字 → 權重 Wₖ（可以是負數，代表偏離主題要扣分）
 * @param stopwords   排除詞：權重 0，只用來避免把裡面的字算進去（例如「熱狗」不算「狗」）
 * @param queryPrefix 搜尋時強制加在查詢前面的字（沒有就是空字串）
 * @param querySuffix 搜尋時強制加在查詢後面的字（沒有就是空字串）
 */
public record Profile(String id, String name, String description,
                      Map<String, Double> keywords, List<String> stopwords,
                      String queryPrefix, String querySuffix) {
}
