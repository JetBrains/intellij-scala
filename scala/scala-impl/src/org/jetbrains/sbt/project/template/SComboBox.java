package org.jetbrains.sbt.project.template;

import org.jetbrains.annotations.Nullable;
import scala.Function1;
import scala.Option;

import javax.swing.*;
import java.awt.*;

/**
 * @author Pavel Fatin
 */
public class SComboBox<T> extends JComboBox<T> {
  public SComboBox() {
  }

  public SComboBox(T[] items) {
    super(items);
  }

  public void setItems(T[] items) {
    super.setModel(new DefaultComboBoxModel<>(items));
  }

  public void setSelectedItemSafe(T anObject) {
    setSelectedItem(anObject);
  }

  @SuppressWarnings("unchecked")
  public Option<T> getSelectedItemTyped() {
    return Option.apply((T) getSelectedItem());
  }

  public void setTextRenderer(final Function1<String, String> renderer) {
    setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value != null && !(value instanceof String)) {
          throw new IllegalArgumentException("Not a String value: " + value);
        }
        Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setText(renderer.apply((String) value));
        return component;
      }
    });
  }
}
