package org.genomebridge.consent.http.enumeration;

public enum ElectionType {

    DATA_ACCESS("DataAccess"),
    TRANSLATE_DUL("TranslateDUL"),
    RP("RP");

    private String value;

    ElectionType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static String getValue(String value) {
        for (ElectionType e : ElectionType.values()) {
            if (e.getValue().equalsIgnoreCase(value)) {
                return e.getValue();
            }
        }
        return null;
    }

}
