package domain.viewer;

import java.util.HashMap;
import java.util.Map;

public enum ViewerAge {
    AGE_13_17("13-17", 13, 17),
    AGE_18_24("18-24", 18, 24),
    AGE_25_34("25-34", 25, 34),
    AGE_35_44("35-44", 35, 44),
    AGE_45_54("45-54", 45, 54),
    AGE_55_PLUS("55+", 55, Integer.MAX_VALUE);

    private static final Map<String, ViewerAge> BY_PROTOCOL_VALUE = new HashMap<>();

    static {
        for (ViewerAge viewerAge : values()) {
            BY_PROTOCOL_VALUE.put(viewerAge.protocolValue, viewerAge);
        }
    }

    private final String protocolValue;
    private final int minInclusive;
    private final int maxInclusive;

    ViewerAge(String protocolValue, int minInclusive, int maxInclusive) {
        this.protocolValue = protocolValue;
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public String getProtocolValue() {
        return protocolValue;
    }

    public int getMinInclusive() {
        return minInclusive;
    }

    public int getMaxInclusive() {
        return maxInclusive;
    }

    public boolean contains(int age) {
        return age >= minInclusive && age <= maxInclusive;
    }

    public static ViewerAge fromProtocolValue(String protocolValue) {
        ViewerAge viewerAge = BY_PROTOCOL_VALUE.get(protocolValue);
        if (viewerAge == null) {
            throw new IllegalArgumentException("Unknown viewer age bracket: " + protocolValue);
        }
        return viewerAge;
    }

    @Override
    public String toString() {
        return protocolValue;
    }
}
