package nin.app.overlay;

public class NativeControlItem {

    public enum ControlType {
        SWITCH,
        SEEKBAR
    }

    public final ControlType type;
    public final String label;
    public final int id;
    public final int min;
    public final int max;

    public NativeControlItem(ControlType type, String label, int id, int min, int max) {
        this.type = type;
        this.label = label;
        this.id = id;
        this.min = min;
        this.max = max;
    }
}
