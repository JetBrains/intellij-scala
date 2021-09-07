package org.jetbrains.sbt.project.template.wizard.kotlin_interop;

import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.ui.dsl.builder.Cell;
import com.intellij.ui.dsl.builder.ComboBoxKt;

@SuppressWarnings("UnstableApiUsage")
public final class ComboBoxKt_Wrapper {

    private ComboBoxKt_Wrapper() {
    }

    // NOTE: have to use this as a workaround for:
    // https://github.com/scala/bug/issues/11775
    // and
    // https://youtrack.jetbrains.com/issue/SCL-19511
    public static Cell<JdkComboBox> columns(Cell<JdkComboBox> combobox, int columns) {
        ComboBoxKt.columns(combobox, columns);
        return combobox;
    }
}
