package org.jetbrains.sbt.project.template.wizard.kotlin_interop;

import com.intellij.openapi.observable.properties.ObservableClearableProperty;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.layout.CellKt;

public class CellKt_Wrapper {

    private CellKt_Wrapper() {
    }

    public static <T, C extends ComboBox<T>> C bind(C cell, ObservableClearableProperty<T> property) {
        return CellKt.bind(cell, property);
    }

}
