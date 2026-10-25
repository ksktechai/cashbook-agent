package nz.co.ksktech.cashbook.synthetic.model;

/** The highlighter colours seen in the real statements. Any colour marks a business line. */
public enum HighlightColour {
    LIGHT_YELLOW(1f, 0.988f, 0.473f),
    GOLD(0.980392f, 0.803922f, 0.352941f),
    GREEN(0f, 1f, 0f),
    SAGE(0.486275f, 0.784314f, 0.407843f),
    PURPLE(0.784314f, 0.521569f, 0.854902f),
    ORANGE(1f, 0.5f, 0f);

    private final float red;
    private final float green;
    private final float blue;

    HighlightColour(float red, float green, float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public float[] rgb() {
        return new float[] {red, green, blue};
    }
}
