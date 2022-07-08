package org.jetbrains.sbt.project.template;

import com.intellij.openapi.ui.ComboBox;
import scala.Function1;
import scala.Option;

import javax.swing.*;
import java.awt.*;

public class SComboBox<T> extends ComboBox<T> {
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
  public void setTextRenderer2(final Function1<T, String> renderer) {
    setRenderer(new DefaultListCellRenderer() {
      @Override
      @SuppressWarnings("unchecked")
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setText(renderer.apply((T) value));
        return component;
      }
    });
  }
}
