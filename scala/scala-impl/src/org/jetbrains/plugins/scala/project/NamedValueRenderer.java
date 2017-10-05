package org.jetbrains.plugins.scala.project;

import com.intellij.ui.ListCellRendererWrapper;

import javax.swing.*;

/**
 * @author Pavel Fatin
 */
public class NamedValueRenderer extends ListCellRendererWrapper<Named> {
  @Override
  public void customize(JList list, Named value, int index, boolean selected, boolean hasFocus) {
    String name = value == null ? null : value.getName();
    setText(name);
  }
}
