package org.jetbrains.sbt.project.template.activator;

import javax.swing.*;

/**
 * @author Alefas
 * @since 06/02/15.
 */
public abstract class JavaAbstractListModel<T> extends AbstractListModel {
  public JavaAbstractListModel() {}

  @Override
  public Object getElementAt(int index) {
    return getElementAtAdapter(index);
  }

  public abstract Object getElementAtAdapter(int index);
}
