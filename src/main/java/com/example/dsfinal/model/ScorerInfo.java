package com.example.dsfinal.model;

/**
 * /api/scorers 回傳的一個計分算法。
 *
 * @param id          @Component("名字") 的名字，搜尋時用 ?scorer=id 指定
 * @param name        顯示名稱
 * @param description 說明
 * @param isDefault   是否為 application.properties 裡 search.scorer 設定的預設算法
 */
public record ScorerInfo(String id, String name, String description, boolean isDefault) {
}
