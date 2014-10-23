package org.jetbrains.plugins.scala.settings.uiControls;

import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ListWithSelection;
import com.intellij.util.ui.table.ComboBoxTableCellEditor;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: Dmitry Naydanov
 * Date: 2/18/13
 */
public class ScalaInterpolatedPrefixMappingTable extends JBTable implements ScalaUiWithDependency.ComponentWithSettings {
  private final MyTableModel tableModel;
  private final List<String> availableIds;
  

  public ScalaInterpolatedPrefixMappingTable() {
    super(new MyTableModel(new ArrayList<PrefixLanguagePair>()));
    tableModel = (MyTableModel) getModel();
    availableIds = Arrays.asList(InjectedLanguage.getAvailableLanguageIDs());

    TableColumn column = getColumnModel().getColumn(1);

    column.setCellEditor(ComboBoxTableCellEditor.INSTANCE);
  }
  
  public Map<String, String> getMapping() {
    final Map<String, String> mapping = new HashMap<String, String>();
    for (PrefixLanguagePair pair : tableModel.data) mapping.put(pair.getMyPrefix(), pair.getMyLanguage());
    return mapping;
  }
  
  public void setMapping(Map<String, String> mapping) {
    final List<PrefixLanguagePair> data = tableModel.data;
    
    data.clear();
    for (Map.Entry<String, String> pair : mapping.entrySet()) {
      int index = availableIds.indexOf(pair.getValue());
      if (index != -1) data.add(new PrefixLanguagePair(pair.getKey(), index));
    }
    
    tableModel.fireTableDataChanged();
  }

  public void setMyMainPanel(JPanel myMainPanel) {
    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(this).setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        if (availableIds.size() > 0) tableModel.data.add(new PrefixLanguagePair("prefix", 0));
        tableModel.fireTableDataChanged();
      }
    }).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        int[] selected = getSelectedRows();
        if (selected.length == 0) return;
        
        for (int i = selected.length - 1; i > -1; --i) {
          tableModel.data.remove(selected[i]);
          tableModel.fireTableRowsDeleted(i, i);
        }

        tableModel.fireTableDataChanged();
      }
    });
    
    final JPanel editPanel = decorator.createPanel();
    final JPanel localPanel = new JPanel(new BorderLayout());
    
    localPanel.setBorder(IdeBorderFactory.createTitledBorder("Language Injection settings for interpolated strings", false));
    localPanel.add(editPanel, BorderLayout.CENTER);
    
    myMainPanel.add(localPanel);
  }

  @Override
  public void loadSettings(ScalaProjectSettings settings) {
    setMapping(settings.getIntInjectionMapping());
  }

  @Override
  public void saveSettings(ScalaProjectSettings settings) {
    settings.setIntInjectionMapping(getMapping());
  }

  @Override
  public boolean isModified(ScalaProjectSettings settings) {
    return !settings.getIntInjectionMapping().equals(getMapping());
  }

  private class PrefixLanguagePair implements Cloneable {
    private static final String INTERPOLATED_PREFIX = "Interpolated String prefix";
    private static final String LANGUAGE_ID = "Language ID";
    
    private String myPrefix;
    private MyListWithSelection myLanguagesModel;

    private PrefixLanguagePair(String myPrefix, Integer myLanguageIndex) {
      this.myPrefix = myPrefix;
      final List<String> ids = ScalaInterpolatedPrefixMappingTable.this.availableIds;
      myLanguagesModel = new MyListWithSelection(ids, ids.get(myLanguageIndex)); 
    }


    private String getMyPrefix() {
      return myPrefix;
    }

    private void setMyPrefix(String myPrefix) {
      this.myPrefix = myPrefix;
    }

    private String getMyLanguage() {
      return myLanguagesModel.getSelection();
    }

    private void setMyLanguage(String language) {
      myLanguagesModel.select(language);
    }
  }
  
  private static class MyListWithSelection extends ListWithSelection<String> {
    public MyListWithSelection(Collection<String> collection, String selection) {
      super(collection, selection);
    }

    @Override
    public String toString() {
      return getSelection();
    }

    @Override
    public void removeRange(int fromIndex, int toIndex) {
      super.removeRange(fromIndex, toIndex);    //to make it available
    }
  }
  
  private static class MyTableModel extends AbstractTableModel {
    private final List<PrefixLanguagePair> data;

    private MyTableModel(@NotNull List<PrefixLanguagePair> data) {
      this.data = data;
    }

    @Override
    public int getRowCount() {
      return data.size();
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (!checkRanges(rowIndex, columnIndex)) return null;
      
      PrefixLanguagePair pair = data.get(rowIndex);
      return columnIndex == 0? pair.getMyPrefix() : pair.myLanguagesModel;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (!checkRanges(rowIndex, columnIndex)) return;
      PrefixLanguagePair pair = data.get(rowIndex);
      if (columnIndex == 0) pair.setMyPrefix(aValue.toString()); else pair.setMyLanguage(aValue.toString());  
    }

    @Override
    public String getColumnName(int column) {
      switch (column) {
        case 0: return PrefixLanguagePair.INTERPOLATED_PREFIX;
        case 1: return PrefixLanguagePair.LANGUAGE_ID;
        default: return null;
      }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return true;
    }
    
    private boolean checkRanges(int row, int column) {
      return row >=0  && column >= 0 && row < getRowCount() && column < getColumnCount();
    } 
  } 
}
