package com.yuhaojun.douyinadskipper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
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
        if (result.hasPromotionChapter()) {
            lastActionTime = now;
            seekPastPromotionChapter(result.markerBounds);
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
            }
            if (hasChapterContext) {
                result.hasSeenChapterContext = true;
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

        boolean hasPromotionChapter() {
            return promotionChapterCount > 0;
        }
    }
}
