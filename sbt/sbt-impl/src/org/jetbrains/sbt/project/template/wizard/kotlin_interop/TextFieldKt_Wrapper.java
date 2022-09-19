package org.jetbrains.sbt.project.template.wizard.kotlin_interop;

import com.intellij.openapi.observable.properties.GraphProperty;
import com.intellij.ui.dsl.builder.Cell;
import com.intellij.ui.dsl.builder.TextFieldKt;

import javax.swing.text.JTextComponent;

public final class TextFieldKt_Wrapper {

    private TextFieldKt_Wrapper() {
    }

    public static <T extends JTextComponent> Cell<T> bindText(Cell<T> cell, GraphProperty<String> property) {
        return TextFieldKt.bindText(cell, property);
    }
}
