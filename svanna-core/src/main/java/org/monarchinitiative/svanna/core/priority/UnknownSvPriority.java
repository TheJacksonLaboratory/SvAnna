package org.monarchinitiative.svanna.core.priority;

class UnknownSvPriority implements SvPriority {

    private static final UnknownSvPriority INSTANCE = new UnknownSvPriority();

    static UnknownSvPriority instance() {
        return INSTANCE;
    }

    private UnknownSvPriority() {}

    @Override
    public double getPriority() {
        return 0;
    }

    @Override
    public String toString() {
        return "UNKNOWN PRIORITY";
    }
}
