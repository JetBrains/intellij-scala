package org.jetbrains.plugins.scala.settings;

enum TypeMismatchHighlightingMode {
    HIGHLIGHT_EXPRESSION("Highlight expression"),
    HIGHLIGHT_TYPE_HINT("Highlight type hint");

    private String myName;

    TypeMismatchHighlightingMode(String name) {
        myName = name;
    }

    @Override
    public String toString() {
        return myName;
    }
}
