package org.jetbrains.plugins.scala.annotator;

import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings;

public enum TypeMismatchHighlightingMode {
    HIGHLIGHT_EXPRESSION("Highlight expression"),
    HIGHLIGHT_TYPE_HINT("Highlight type hint"),
    SHOW_TYPE_MISMATCH_HINT("Show type mismatch hint");

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
