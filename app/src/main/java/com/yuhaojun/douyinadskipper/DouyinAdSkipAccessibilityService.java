package com.yuhaojun.douyinadskipper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class DouyinAdSkipAccessibilityService extends AccessibilityService {
    private static final long ACTION_COOLDOWN_MS = 9000L;
    private static final int MAX_NODES_TO_SCAN = 220;

    private static final Set<String> DOUYIN_PACKAGES = new HashSet<>(Arrays.asList(
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite"
    ));

    private static final String[] SKIP_BUTTON_KEYWORDS = {
            "跳过广告",
            "跳过",
            "关闭广告"
    };

    private static final String[] STRONG_EMBEDDED_AD_KEYWORDS = {
            "广告时间",
            "本视频由",
            "赞助播出",
            "感谢赞助",
            "品牌赞助",
            "品牌合作",
            "商务合作",
            "商业合作",
            "恰饭",
            "接个广告",
            "口播",
            "小黄车",
            "购物车",
            "商品链接",
            "购买链接",
            "链接在下方",
            "点击左下角",
            "点击右下角",
            "官方旗舰店",
            "直播间同款"
    };

    private static final String[] WEAK_PROMOTION_KEYWORDS = {
            "领券",
            "优惠券",
            "下单",
            "购买",
            "入手",
            "同款",
            "旗舰店",
            "咨询",
            "私信",
            "课程",
            "套餐",
            "下载",
            "安装",
            "注册",
            "试用",
            "活动价",
            "限时",
            "福利",
            "链接"
    };

    private long lastActionTime;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !isTargetPackage(event.getPackageName())) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }

        if (clickIfSkipButtonExists(root)) {
            lastActionTime = now;
            return;
        }

        DetectionResult result = new DetectionResult();
        collectSignals(root, result);
        if (result.shouldSeekForward()) {
            lastActionTime = now;
            seekForwardInCurrentVideo();
        }
    }

    @Override
    public void onInterrupt() {
        // No persistent work to cancel.
    }

    private boolean isTargetPackage(CharSequence packageName) {
        return packageName != null && DOUYIN_PACKAGES.contains(packageName.toString());
    }

    private boolean clickIfSkipButtonExists(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }

        String label = normalizeText(node);
        if (containsAny(label, SKIP_BUTTON_KEYWORDS)) {
            AccessibilityNodeInfo clickable = findClickableParent(node);
            if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (clickIfSkipButtonExists(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private void collectSignals(AccessibilityNodeInfo node, DetectionResult result) {
        if (node == null || result.scannedNodes >= MAX_NODES_TO_SCAN) {
            return;
        }
        result.scannedNodes++;

        String label = normalizeText(node);
        if (!label.isEmpty()) {
            if (containsAny(label, STRONG_EMBEDDED_AD_KEYWORDS)) {
                result.strongSignalCount++;
            }
            if (containsAny(label, WEAK_PROMOTION_KEYWORDS)) {
                result.weakSignalCount++;
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            collectSignals(node.getChild(i), result);
        }
    }

    private boolean containsAny(String label, String[] keywords) {
        if (label.isEmpty()) {
            return false;
        }
        String lower = label.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeText(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        StringBuilder builder = new StringBuilder();
        if (text != null) {
            builder.append(text);
        }
        if (description != null) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(description);
        }
        return builder.toString().trim();
    }

    private AccessibilityNodeInfo findClickableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable() && current.isEnabled()) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private void seekForwardInCurrentVideo() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float y = metrics.heightPixels * 0.91f;
        float startX = metrics.widthPixels * 0.42f;
        float endX = metrics.widthPixels * 0.78f;

        Path path = new Path();
        path.moveTo(startX, y);
        path.lineTo(endX, y);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 420))
                .build();
        dispatchGesture(gesture, null, null);
    }

    private static class DetectionResult {
        int scannedNodes;
        int strongSignalCount;
        int weakSignalCount;

        boolean shouldSeekForward() {
            return strongSignalCount >= 1 || weakSignalCount >= 2;
        }
    }
}
