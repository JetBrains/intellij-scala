package org.jetbrains.plugins.scala.util;

import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Alefas
 * @since 30/10/14.
 */
public class JListCompatibility {
  public static DefaultListModel createDefaultListModel() {
    return new DefaultListModel();
  }

  public static class CollectionListModelWrapper {
    public CollectionListModelWrapper(CollectionListModel<String> model) {
      this.model = model;
    }

    public CollectionListModel<String> getModel() {
      return model;
    }

    CollectionListModel<String> model;

    public ListModel getModelRaw() {
      return model;
    }
  }

  public static ListModel createCollectionListModel() {
    return new CollectionListModel<String>(new ArrayList<String>());
  }

  public static JList createJListFromModel(DefaultListModel model) {
    return new JList(model);
  }

  public static JList createJBListFromModel(DefaultListModel model) {
    return new JBList(model);
  }

  public static JList createJBListFromListData(Object... listData) {
    return new JBList(listData);
  }

  public static class GoToImplicitConversionAction {
    private static JList list = null;

    public static JList getList() {
      return list;
    }

    public static void setList(JList list) {
      GoToImplicitConversionAction.list = list;
    }
  }

  public static class JListContainer {
    public JListContainer(JList list) {
      this.list = list;
    }

    public JList getList() {
      return list;
    }

    private JList list;
  }


  public static DefaultListModel getDefaultListModel(ListModel model) {
    if (model instanceof DefaultListModel) {
      return (DefaultListModel) model;
    } else return null;
  }

  public static void addElement(DefaultListModel model, Object element) {
    model.addElement(element);
  }

  public static void add(DefaultListModel model, int index, Object element) {
    model.add(index, element);
  }

  public static void setCellRenderer(JList list, ListCellRenderer renderer) {
    list.setCellRenderer(renderer);
  }

  public static void setModel(JList list, ListModel model) {
    list.setModel(model);
  }

  public static void setModel(JBList list, ListModel model) {
    list.setModel(model);
  }

  public static ListCellRenderer getGotoFileCellRenderer(int maxSize) {
    return new GotoFileCellRenderer(maxSize);
  }

  public static void addItem(JComboBox comboBox, Object item) {
    comboBox.addItem(item);
  }
}
