package com.fly.rent.web.analytics.support;

import java.util.ArrayList;
import java.util.List;

/**
 * 支付宝租赁分析价值评分工具。
 * <p>
 * 设计目标：
 * 1. 将“等级/标签/解释文案”从具体 service 中抽离，避免多个分析服务重复维护同一套口径。
 * 2. 保留基于权重和区间归一化的扩展点，后续新增指标时优先扩展本类而不是改调用方。
 * 3. 对外只暴露稳定的 ValueProfile 结果，便于首页、专题页和缓存结果复用。
 */
public final class AnalyticsValueScoreHelper {

    private AnalyticsValueScoreHelper() {
    }

    /**
     * 根据已经归一化后的用户维度得分构建画像结果。
     */
    public static ValueProfile buildUserProfile(double amountScore,
                                                double orderScore,
                                                double activeScore,
                                                double rentDaysScore,
                                                long activeDays,
                                                int orderCount) {
        double score = round2(amountScore * 0.38D + orderScore * 0.28D + rentDaysScore * 0.16D + activeScore * 0.18D);
        String tag = resolveUserTag(score, activeDays, orderCount);
        return new ValueProfile(score, resolveLevel(score), tag,
                buildUserFactorSummary(amountScore, orderScore, activeScore, rentDaysScore));
    }

    /**
     * 根据已经归一化后的设备维度得分构建设备画像结果。
     */
    public static ValueProfile buildDeviceProfile(double amountScore,
                                                  double orderScore,
                                                  double rentDaysScore,
                                                  double shareScore) {
        double score = round2(amountScore * 0.40D + orderScore * 0.25D + rentDaysScore * 0.20D + shareScore * 0.15D);
        String tag = resolveDeviceTag(score, orderScore, rentDaysScore);
        return new ValueProfile(score, resolveLevel(score), tag,
                buildDeviceFactorSummary(amountScore, orderScore, rentDaysScore, shareScore));
    }

    /**
     * 看板排行使用略偏收入导向的设备评分权重。
     */
    public static ValueProfile buildDashboardDeviceProfile(double revenueScore,
                                                           double orderScore,
                                                           double rentDaysScore,
                                                           double shareScore) {
        double score = round2(revenueScore * 0.42D + orderScore * 0.26D + rentDaysScore * 0.17D + shareScore * 0.15D);
        String tag = score >= 78D ? "高价值设备" : (orderScore >= 75D ? "高周转设备" : (score >= 60D ? "潜力设备" : "稳健设备"));
        return new ValueProfile(score, resolveLevel(score), tag,
                buildDeviceFactorSummary(revenueScore, orderScore, rentDaysScore, shareScore));
    }

    /**
     * 统一读取画像分数，避免调用方反复空值判断。
     */
    public static double profileScore(ValueProfile profile) {
        return profile == null ? 0D : profile.getScore();
    }

    /**
     * 统一输出等级，前端标签色和排序规则可以直接复用。
     */
    public static String resolveLevel(double score) {
        if (score >= 80D) {
            return "S";
        }
        if (score >= 65D) {
            return "A";
        }
        if (score >= 50D) {
            return "B";
        }
        if (score >= 35D) {
            return "C";
        }
        return "D";
    }

    /**
     * 用户标签以价值分为主，活跃度和订单数为兜底约束，避免高金额但沉默顾客被误判。
     */
    public static String resolveUserTag(double score, long activeDays, int orderCount) {
        if (score >= 78D && activeDays <= 30) {
            return "高价值用户";
        }
        if (score >= 62D) {
            return "成长型用户";
        }
        if (activeDays >= 45 && orderCount >= 2) {
            return "流失风险用户";
        }
        if (orderCount <= 1) {
            return "潜力新客";
        }
        return "普通用户";
    }

    /**
     * 设备标签优先区分“高价值 / 高周转 / 潜力 / 稳健”四类。
     */
    public static String resolveDeviceTag(double score, double orderScore, double rentDaysScore) {
        if (score >= 78D) {
            return "高价值设备";
        }
        if (orderScore >= 75D && rentDaysScore >= 60D) {
            return "高周转设备";
        }
        if (score >= 60D) {
            return "潜力设备";
        }
        return "稳健设备";
    }

    /**
     * 面向业务人员输出用户评分说明。
     */
    public static String buildUserFactorSummary(double amountScore,
                                                double orderScore,
                                                double activeScore,
                                                double rentDaysScore) {
        List<String> factors = new ArrayList<>();
        if (amountScore >= 70D) {
            factors.add("金额贡献高");
        }
        if (orderScore >= 70D) {
            factors.add("复购频次高");
        }
        if (activeScore >= 70D) {
            factors.add("近期活跃");
        }
        if (rentDaysScore >= 70D) {
            factors.add("长租偏好明显");
        }
        if (factors.isEmpty()) {
            factors.add("整体表现平稳");
        }
        return String.join("、", factors);
    }

    /**
     * 面向业务人员输出设备评分说明。
     */
    public static String buildDeviceFactorSummary(double amountScore,
                                                  double orderScore,
                                                  double rentDaysScore,
                                                  double shareScore) {
        List<String> factors = new ArrayList<>();
        if (amountScore >= 70D) {
            factors.add("收入贡献高");
        }
        if (orderScore >= 70D) {
            factors.add("订单活跃");
        }
        if (rentDaysScore >= 70D) {
            factors.add("租期价值高");
        }
        if (shareScore >= 70D) {
            factors.add("占比表现强");
        }
        if (factors.isEmpty()) {
            factors.add("经营表现平稳");
        }
        return String.join("、", factors);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    /**
     * 简单区间归一化器。
     * <p>
     * 说明：
     * - 调用方只负责注册样本和取分数；
     * - 后续若要改成分位数、Z-Score 或对数缩放，只需替换本类内部实现。
     */
    public static class ScoreRange {
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;

        public void register(double value) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        public double score(double value) {
            if (!Double.isFinite(min) || !Double.isFinite(max) || max <= min) {
                return value > 0D ? 60D : 0D;
            }
            return round2(Math.max(0D, Math.min(100D, (value - min) * 100D / (max - min))));
        }

        public double inverseScore(double value) {
            return round2(100D - score(value));
        }
    }

    /**
     * 稳定的评分输出结构。
     */
    public static class ValueProfile {
        private final double score;
        private final String level;
        private final String tag;
        private final String factorSummary;

        public ValueProfile(double score, String level, String tag, String factorSummary) {
            this.score = score;
            this.level = level;
            this.tag = tag;
            this.factorSummary = factorSummary;
        }

        public double getScore() {
            return score;
        }

        public String getLevel() {
            return level;
        }

        public String getTag() {
            return tag;
        }

        public String getFactorSummary() {
            return factorSummary;
        }
    }
}
