package org.jetbrains.plugins.scala.config.ui;

/**
 * Pavel.Fatin, 01.08.2010
 */

import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class MyTableView<Item> extends TableView<Item> {
  public boolean hasSelection() {
    ListSelectionModel m = getSelectionModel();
    return !m.isSelectionEmpty();
  }

  public boolean hasSingleSelection() {
    ListSelectionModel m = getSelectionModel();
    return hasSelection() && (m.getMaxSelectionIndex() - m.getMinSelectionIndex() == 0);
  }

  public boolean hasMultipleSelection() {
    ListSelectionModel m = getSelectionModel();
    return hasSelection() && (m.getMaxSelectionIndex() - m.getMinSelectionIndex() > 0);
  }


  public boolean isNotFirstRowSelected() {
    ListSelectionModel m = getSelectionModel();
    return m.getMinSelectionIndex() > 0;
  }
  
  public boolean isNotLastRowSelected() {
    ListSelectionModel m = getSelectionModel();
    return m.getMaxSelectionIndex() < getRowCount() - 1;
  }


  public void moveSelectionUpUsing(List<Item> items) {
    Collection<Item> selection = getSelection();

    int[] rows = getSelectedRows();
    for(int i = 0; i < rows.length; i++) {
      int index = rows[i];
      swap(items, index - 1, index);
      ((ListTableModel) getModel()).fireTableRowsUpdated(index - 1, index);
    }
    
    setSelection(selection);
  }

  public void moveSelectionDownUsing(List<Item> items) {
    Collection<Item> selection = getSelection();

    int[] rows = getSelectedRows();
    for(int i = rows.length - 1; i >= 0; i--) {
      int index = rows[i];
      swap(items, index, index + 1);
      ((ListTableModel) getModel()).fireTableRowsUpdated(index, index + 1);
    }

    setSelection(selection);
  }
  
  private static <T> void swap(List<T> items, int a, int b) {
    T item = items.get(a);
    items.set(a, items.get(b));
    items.set(b, item);
  }
  
  public void removeSelection() {
    int first = getSelectionModel().getMinSelectionIndex();
    
    int[] rows = getSelectedRows();
    for (int i = rows.length - 1; i >= 0; i--) {
      int index = rows[i];
      ((ListTableModel) getModel()).removeRow(index);
    }
    
    getSelectionModel().setSelectionInterval(first - 1, first - 1);
  }
}
