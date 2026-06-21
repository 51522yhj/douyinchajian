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
    private static final long ACTION_COOLDOWN_MS = 2600L;
    private static final int MAX_NODES_TO_SCAN = 180;

    private static final Set<String> DOUYIN_PACKAGES = new HashSet<>(Arrays.asList(
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite"
    ));

    private static final String[] EXACT_AD_LABELS = {
            "广告",
            "视频广告",
            "品牌广告",
            "推广",
            "赞助",
            "广告详情"
    };

    private static final String[] COMMERCIAL_KEYWORDS = {
            "广告",
            "推广",
            "赞助",
            "品牌合作",
            "商业合作",
            "达人推荐",
            "立即下载",
            "立即安装",
            "查看详情",
            "了解详情",
            "去看看",
            "进店看看",
            "进入店铺",
            "立即购买",
            "领券购买"
    };

    private static final String[] SKIP_BUTTON_KEYWORDS = {
            "跳过",
            "关闭广告",
            "关闭推广"
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
        if (result.shouldSkip()) {
            lastActionTime = now;
            swipeToNextVideo();
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
            if (matchesExactAdLabel(label)) {
                result.hasAdLabel = true;
            }
            if (containsAny(label, COMMERCIAL_KEYWORDS)) {
                result.commercialSignalCount++;
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            collectSignals(node.getChild(i), result);
        }
    }

    private boolean matchesExactAdLabel(String label) {
        String compact = label.replace(" ", "");
        for (String adLabel : EXACT_AD_LABELS) {
            if (compact.equals(adLabel) || compact.startsWith(adLabel + "·") || compact.startsWith(adLabel + "|")) {
                return true;
            }
        }
        return false;
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

    private void swipeToNextVideo() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float startX = metrics.widthPixels * 0.5f;
        float startY = metrics.heightPixels * 0.76f;
        float endY = metrics.heightPixels * 0.24f;

        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(startX, endY);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 240))
                .build();
        dispatchGesture(gesture, null, null);
    }

    private static class DetectionResult {
        int scannedNodes;
        int commercialSignalCount;
        boolean hasAdLabel;

        boolean shouldSkip() {
            return hasAdLabel || commercialSignalCount >= 2;
        }
    }
}
