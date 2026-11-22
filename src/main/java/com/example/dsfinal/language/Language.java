package com.example.dsfinal.language;

/**
 * 一種介面／搜尋語言。內容寫在 src/main/resources/languages.json。
 *
 * @param code         語言代碼，網址和前端用（例如 zh-TW、en）
 * @param label        語言選單上顯示的名稱，用該語言自己的寫法（例如 繁體中文、English）
 * @param googleHl     SerpApi（Google）的 hl 參數：結果的介面語言
 * @param googleGl     SerpApi（Google）的 gl 參數：結果偏好的國家／地區
 * @param braveLang    Brave 的 search_lang 參數
 * @param braveCountry Brave 的 country 參數
 */
public record Language(String code, String label,
                       String googleHl, String googleGl,
                       String braveLang, String braveCountry) {
}
