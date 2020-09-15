package org.jetbrains.plugins.scala.project;

import javax.swing.*;

/**
 * @author Pavel Fatin
 */
@SuppressWarnings("deprecation")
public class NamedValueRenderer extends com.intellij.ui.ListCellRendererWrapper<Named> {
  @Override
  public void customize(JList list, Named value, int index, boolean selected, boolean hasFocus) {
    String name = value == null ? null : value.getName();
    setText(name);
  }
}
