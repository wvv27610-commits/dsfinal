package com.example.dsfinal.profile;

/**
 * 身份的分組（例如「日常照顧」「健康醫療」），前端用來把身份按鈕分區顯示。
 * 寫在 profiles.json 的 groups 陣列；英文名稱寫在 profiles.en.json 的 groups 區塊。
 *
 * @param id   英文代號，身份用 "group": "id" 指定自己屬於哪一組
 * @param name 顯示名稱
 */
public record ProfileGroup(String id, String name) {
}
