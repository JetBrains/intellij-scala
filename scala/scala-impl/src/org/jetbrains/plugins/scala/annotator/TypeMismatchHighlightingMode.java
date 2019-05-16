package org.jetbrains.plugins.scala.annotator;

import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings;

public enum TypeMismatchHighlightingMode {
    HIGHLIGHT_EXPRESSION("Highlight expression"),
    UNDERLINE_ACTUAL_TYPE_HINT("Underline actual type hint"),
    STRIKETHROUGH_ACTUAL_TYPE_HINT("Strikethrough actual type hint"),
    UNDERLINE_EXPECTED_TYPE_HINT("Underline expected type hint"),
    STRIKETHROUGH_EXPECTED_TYPE_HINT("Strikethrough expected type hint");

    private String myName;

    TypeMismatchHighlightingMode(String name) {
        myName = name;
    }

    @Override
    public String toString() {
        return myName;
    }

    public static TypeMismatchHighlightingMode in(Project project) {
        return ScalaProjectSettings.getInstance(project).getTypeMismatchHighlightingMode();
    }
}
