package org.jetbrains.sbt.project.template;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ClientProperty;
import scala.Function1;
import scala.Option;

import javax.swing.*;
import java.awt.*;

import static com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED;

public class SComboBox<T> extends ComboBox<T> {
  public SComboBox() {
  }

  public SComboBox(T[] items, int width, DefaultListCellRenderer renderer, Boolean shouldAllowAnimation) {
    super(items, width);
    setRenderer(renderer, shouldAllowAnimation);
  }

  private void setRenderer(DefaultListCellRenderer renderer, Boolean shouldAllowAnimation) {
    if (shouldAllowAnimation) {
      ClientProperty.put(this , ANIMATION_IN_RENDERER_ALLOWED, true);
    }
    setRenderer(renderer);
  }

  public void setItems(T[] items) {
    super.setModel(new DefaultComboBoxModel<>(items));
  }

  public void setSelectedItemSafe(T anObject) {
    setSelectedItem(anObject);
  }

  public void updateComboBoxModel(T[] items, Option<T> selectedItem) {
    ComboBoxModel<T> model = new DefaultComboBoxModel<>(items);
    if (selectedItem.isDefined()) model.setSelectedItem(selectedItem.get());
    super.setModel(model);
    // note: change of data model does not fire selected item change, so the GraphProperty associated with it is not updated.
    // It is necessary for graph properties that exist in com.intellij.scala.play.projectTemplate.PlayNewProjectWizardStep.
    // The same hack is done in com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkVersionVendorCombobox.setModel
    selectedItemChanged();
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
