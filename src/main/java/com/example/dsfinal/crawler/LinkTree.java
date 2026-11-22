package com.example.dsfinal.crawler;

/**
 * 一筆搜尋結果的連結樹，由 LinkTreeBuilder 建立。
 *
 * 連結層數加權（提案第 2 節第三點）：
 *
 *   relatedRatio = （相關連結的 1/d 總和）÷（所有連結的 1/d 總和）
 *   L            = L_max × relatedRatio
 *
 * d 是連結所在的層數，越深權重越小；根節點（d = 0）是網頁本身，不算連結。
 * relatedRatio 一定介於 0 到 1，所以 L 一定介於 0 到 L_max，不會因為連結多而暴增。
 */
public final class LinkTree {

    private final LinkNode root;
    private final int linkCount;
    private final int relatedCount;
    private final double weightedRelated;
    private final double weightedTotal;

    public LinkTree(LinkNode root) {
        this.root = root;
        Sums sums = new Sums();
        collect(root, sums);
        this.linkCount = sums.links;
        this.relatedCount = sums.related;
        this.weightedRelated = sums.weightedRelated;
        this.weightedTotal = sums.weightedTotal;
    }

    /** 遞迴走訪整棵樹（前序），把每個連結的 1/d 加進總和 */
    private static void collect(LinkNode node, Sums sums) {
        if (node.getDepth() > 0) {
            double weight = 1.0 / node.getDepth();
            sums.links++;
            sums.weightedTotal += weight;
            if (node.isRelated()) {
                sums.related++;
                sums.weightedRelated += weight;
            }
        }
        for (LinkNode child : node.getChildren()) {
            collect(child, sums);
        }
    }

    private static final class Sums {
        int links;
        int related;
        double weightedRelated;
        double weightedTotal;
    }

    public LinkNode getRoot() {
        return root;
    }

    /** 樹裡的連結數（不含根節點） */
    public int getLinkCount() {
        return linkCount;
    }

    /** 相關連結數 */
    public int getRelatedCount() {
        return relatedCount;
    }

    /** （相關連結的 1/d 總和）÷（所有連結的 1/d 總和），沒有連結時為 0 */
    public double getRelatedRatio() {
        return weightedTotal == 0 ? 0 : weightedRelated / weightedTotal;
    }

    /** 空的樹：網頁打不開，或沒有同網站的連結 */
    public static LinkTree empty(String url) {
        return new LinkTree(new LinkNode(url, "", 0, java.util.List.of()));
    }
}
