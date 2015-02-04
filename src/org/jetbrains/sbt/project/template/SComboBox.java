package org.jetbrains.sbt.project.template;

import javax.swing.*;

/**
 * @author Pavel Fatin
 */
class SComboBox<T> extends JComboBox {
  public SComboBox(String[] items) {
    super(items);
  }

  @Override
  public T getSelectedItem() {
    return (T) super.getSelectedItem();
  }

  public void setItems(String[] items) {
    super.setModel(new DefaultComboBoxModel(items));
  }
}
