package org.jetbrains.sbt.project.template;

import javax.swing.*;

/**
 * @author Pavel Fatin
 */
class SComboBox extends JComboBox {
  public SComboBox(String[] items) {
    super(items);
  }

  @Override
  public String getSelectedItem() {
    return (String) super.getSelectedItem();
  }

  public void setItems(String[] items) {
    super.setModel(new DefaultComboBoxModel(items));
  }
}
