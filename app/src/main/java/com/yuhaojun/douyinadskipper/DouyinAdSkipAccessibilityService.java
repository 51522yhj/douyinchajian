package com.yuhaojun.douyinadskipper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

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

    private static final String[] CHAPTER_CONTEXT_KEYWORDS = {
            "章节",
            "视频章节",
            "进度条",
            "看点",
            "片段",
            "时间轴"
    };

    private static final String[] PROMOTION_CHAPTER_KEYWORDS = {
            "推广内容",
            "推广片段",
            "广告片段",
            "广告章节",
            "营销片段",
            "营销内容",
            "商业推广",
            "商品推荐",
            "品牌推广",
            "赞助内容",
            "赞助片段",
            "品牌合作",
            "商务合作",
            "商业合作"
    };

    private static final String[] CONTEXTUAL_PROMOTION_KEYWORDS = {
            "广告",
            "推广",
            "赞助",
            "营销",
            "带货"
    };

    private long lastActionTime;
    private AccessibilityButtonController.AccessibilityButtonCallback accessibilityButtonCallback;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            handleAccessibilityEvent(event);
        } catch (Throwable throwable) {
            Diagnostics.error(this, throwable);
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Diagnostics.serviceState(this, "已连接");
        registerAccessibilityShortcutButton();
    }

    @Override
    public void onDestroy() {
        unregisterAccessibilityShortcutButton();
        Diagnostics.serviceState(this, "已停止");
        super.onDestroy();
    }

    private void handleAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        CharSequence packageName = event.getPackageName();
        if (packageName == null || getPackageName().contentEquals(packageName)) {
            return;
        }
        Diagnostics.event(this, packageName);

        if (!isTargetPackage(packageName)) {
            return;
        }
        Diagnostics.targetEvent(this, packageName);

        long now = SystemClock.uptimeMillis();
        if (now - lastActionTime < ACTION_COOLDOWN_MS) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Diagnostics.scan(this, "抖音窗口可读，但 root 节点为空");
            return;
        }

        NodeBudget clickBudget = new NodeBudget(MAX_NODES_TO_SCAN);
        if (clickIfSkipButtonExists(root, clickBudget)) {
            lastActionTime = now;
            Diagnostics.action(this, "点击跳过/关闭广告按钮");
            return;
        }

        DetectionResult result = new DetectionResult();
        collectSignals(root, result);
        Diagnostics.scan(this, result.summary());
        if (result.hasPromotionChapter()) {
            lastActionTime = now;
            seekPastPromotionChapter(result.markerBounds);
            Diagnostics.action(this, "检测到章节推广标记，拖动进度条");
        }
    }

    @Override
    public void onInterrupt() {
        Diagnostics.serviceState(this, "被系统中断");
    }

    private void registerAccessibilityShortcutButton() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Diagnostics.action(this, "系统版本不支持无障碍快捷按钮回调");
            return;
        }

        AccessibilityButtonController controller = getAccessibilityButtonController();
        if (controller == null) {
            Diagnostics.action(this, "无障碍快捷按钮不可用");
            return;
        }

        accessibilityButtonCallback = new AccessibilityButtonController.AccessibilityButtonCallback() {
            @Override
            public void onClicked(AccessibilityButtonController controller) {
                Toast.makeText(
                        DouyinAdSkipAccessibilityService.this,
                        Diagnostics.readSummary(DouyinAdSkipAccessibilityService.this),
                        Toast.LENGTH_LONG
                ).show();
            }

            @Override
            public void onAvailabilityChanged(AccessibilityButtonController controller, boolean available) {
                Diagnostics.action(
                        DouyinAdSkipAccessibilityService.this,
                        available ? "无障碍快捷按钮可用" : "无障碍快捷按钮不可用"
                );
            }
        };
        controller.registerAccessibilityButtonCallback(accessibilityButtonCallback);
    }

    private void unregisterAccessibilityShortcutButton() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || accessibilityButtonCallback == null) {
            return;
        }
        AccessibilityButtonController controller = getAccessibilityButtonController();
        if (controller != null) {
            controller.unregisterAccessibilityButtonCallback(accessibilityButtonCallback);
        }
        accessibilityButtonCallback = null;
    }

    private boolean isTargetPackage(CharSequence packageName) {
        if (packageName == null) {
            return false;
        }
        String value = packageName.toString().toLowerCase(Locale.ROOT);
        return DOUYIN_PACKAGES.contains(value)
                || value.contains("aweme")
                || value.contains("douyin");
    }

    private boolean clickIfSkipButtonExists(AccessibilityNodeInfo node, NodeBudget budget) {
        if (node == null || !budget.take()) {
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
            if (clickIfSkipButtonExists(node.getChild(i), budget)) {
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
            boolean hasChapterContext = containsAny(label, CHAPTER_CONTEXT_KEYWORDS);
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            boolean hasMarkerContext = hasChapterContext || result.hasSeenChapterContext || isLikelyProgressMarker(bounds);
            boolean hasPromotionLabel = containsAny(label, PROMOTION_CHAPTER_KEYWORDS)
                    || (hasMarkerContext && containsAny(label, CONTEXTUAL_PROMOTION_KEYWORDS));
            if (hasPromotionLabel && hasMarkerContext) {
                result.promotionChapterCount++;
                if (!bounds.isEmpty()) {
                    result.markerBounds = bounds;
                }
                result.addSample(label);
            }
            if (hasChapterContext) {
                result.hasSeenChapterContext = true;
                result.addSample(label);
            }
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            collectSignals(node.getChild(i), result);
        }
    }

    private boolean isLikelyProgressMarker(Rect bounds) {
        if (bounds == null || bounds.isEmpty()) {
            return false;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return bounds.centerY() > metrics.heightPixels * 0.65f
                && bounds.height() < metrics.heightPixels * 0.18f
                && bounds.width() < metrics.widthPixels * 0.9f;
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

    private void seekPastPromotionChapter(Rect markerBounds) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float y = metrics.heightPixels * 0.91f;
        float startX = metrics.widthPixels * 0.42f;
        float endX = metrics.widthPixels * 0.78f;

        if (markerBounds != null && markerBounds.centerY() > metrics.heightPixels * 0.55f) {
            y = markerBounds.centerY();
            startX = Math.max(metrics.widthPixels * 0.08f, markerBounds.right + dp(6));
            endX = Math.min(metrics.widthPixels * 0.94f, startX + metrics.widthPixels * 0.24f);
        }

        Path path = new Path();
        path.moveTo(startX, y);
        path.lineTo(endX, y);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 420))
                .build();
        dispatchGesture(gesture, null, null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class DetectionResult {
        int scannedNodes;
        int promotionChapterCount;
        boolean hasSeenChapterContext;
        Rect markerBounds;
        private final StringBuilder samples = new StringBuilder();

        boolean hasPromotionChapter() {
            return promotionChapterCount > 0;
        }

        void addSample(String label) {
            if (samples.length() > 90 || label == null || label.isEmpty()) {
                return;
            }
            if (samples.length() > 0) {
                samples.append(" | ");
            }
            samples.append(label);
        }

        String summary() {
            String marker = markerBounds == null ? "无坐标" : markerBounds.flattenToString();
            String sampleText = samples.length() == 0 ? "无" : samples.toString();
            return "扫描节点 " + scannedNodes
                    + "，章节上下文 " + (hasSeenChapterContext ? "有" : "无")
                    + "，推广章节 " + promotionChapterCount
                    + "，标记坐标 " + marker
                    + "，样本 " + sampleText;
        }
    }

    private static class NodeBudget {
        private int remaining;

        NodeBudget(int remaining) {
            this.remaining = remaining;
        }

        boolean take() {
            if (remaining <= 0) {
                return false;
            }
            remaining--;
            return true;
        }
    }
}
