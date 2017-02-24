package org.jetbrains.sbt.project.template;

import javax.swing.*;

/**
 * @author Pavel Fatin
 */
public class SComboBox extends JComboBox {
  public SComboBox() {
  }

  public <T extends Object> SComboBox(T[] items) {
    //noinspection unchecked
    super(items);
  }

  public <T extends Object> void setItems(T[] items) {
    //noinspection unchecked
    super.setModel(new DefaultComboBoxModel(items));
  }
}
