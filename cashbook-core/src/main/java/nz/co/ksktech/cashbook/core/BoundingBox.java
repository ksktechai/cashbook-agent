package nz.co.ksktech.cashbook.core;

/**
 * A box on a PDF page in points, with the origin at the page's top-left corner (as PDFBox reports text
 * positions), so {@code top} grows downwards.
 */
public record BoundingBox(float left, float top, float width, float height) {

    public float right() {
        return left + width;
    }

    public float bottom() {
        return top + height;
    }

    public float area() {
        return width * height;
    }

    /** Area shared with another box, 0 when they don't overlap. */
    public float intersectionArea(BoundingBox other) {
        float w = Math.min(right(), other.right()) - Math.max(left, other.left);
        float h = Math.min(bottom(), other.bottom()) - Math.max(top, other.top);
        return w <= 0 || h <= 0 ? 0 : w * h;
    }
}
