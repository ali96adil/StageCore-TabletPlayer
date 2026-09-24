package com.stagecore.player;

/** Geometry in pixel coordinates after a right-angle Live-only rotation. */
final class LiveImageGeometry {
    final float scaleX;
    final float scaleY;

    private LiveImageGeometry(float scaleX, float scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    static LiveImageGeometry of(int sourceWidth, int sourceHeight, int viewWidth,
                                int viewHeight, int degrees, String mode) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return new LiveImageGeometry(1f, 1f);
        }
        int orientation = AppSettings.normalizeLiveRotation(degrees);
        float rotatedWidth = (orientation == 90 || orientation == 270)
                ? sourceHeight : sourceWidth;
        float rotatedHeight = (orientation == 90 || orientation == 270)
                ? sourceWidth : sourceHeight;
        float x = viewWidth / rotatedWidth;
        float y = viewHeight / rotatedHeight;
        if (AppSettings.SCALE_FULL.equals(mode)) return new LiveImageGeometry(x, y);
        float uniform = AppSettings.SCALE_CROP.equals(mode) ? Math.max(x, y) : Math.min(x, y);
        return new LiveImageGeometry(uniform, uniform);
    }
}
