package org.jetbrains.plugins.scala.project.settings;

import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.plugins.scala.project.IncrementalityType;

import javax.swing.*;

class IncrementalityTypeRenderer extends ListCellRendererWrapper<IncrementalityType> {
    @Override
    public void customize(JList list, IncrementalityType value, int index, boolean selected, boolean hasFocus) {
        setText(nameOf(value));
    }

    private String nameOf(IncrementalityType value) {
        switch (value) {
            case IDEA:
                return "IDEA";
            case SBT:
                return  "Zinc";
            default:
                throw new RuntimeException(value.toString());
        }
    }
}
