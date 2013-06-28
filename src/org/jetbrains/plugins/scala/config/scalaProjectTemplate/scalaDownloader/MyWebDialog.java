package org.jetbrains.plugins.scala.config.scalaProjectTemplate.scalaDownloader;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.download.DownloadableFileSetDescription;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

//TODO ugly; should remade later
public class MyWebDialog extends DialogWrapper {
  private JPanel contentPane;
  private JBTable myLibraryTable;
  private MyLibraryTableModel myTableModel;

  public MyWebDialog(Project project, List<? extends DownloadableFileSetDescription> descriptions) {
    super(project);
    myLibraryTable = new JBTable();
    contentPane = new JPanel(new GridLayoutManager(2, 2));
    final GridConstraints constraints = new GridConstraints();
    
//    myLibraryTable.set
    constraints.setFill(GridConstraints.FILL_BOTH);
    constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_WANT_GROW);
    constraints.setVSizePolicy(GridConstraints.SIZEPOLICY_WANT_GROW);
        
    contentPane.add(myLibraryTable, constraints);
    
    setTitle("Download Library");
    setOKButtonText("Download and Install");
    setCancelButtonText("Close");
    setOKActionEnabled(false);
    myLibraryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    if (descriptions.size() > 0) {
      myTableModel = new MyLibraryTableModel(descriptions);
      myLibraryTable.setModel(myTableModel);
    }
    init();
    TableColumnModel columnModel = myLibraryTable.getColumnModel();
    int[] colSizes = {100, 50, 300};
    for (int col = 0; col < columnModel.getColumnCount(); col++) {
      columnModel.getColumn(col).setPreferredWidth(colSizes[col]);
    }
    new TableSpeedSearch(myLibraryTable);
    myLibraryTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        setOKActionEnabled(true);
      }
    });
  }


  @Override
  protected JComponent createCenterPanel() {
    return contentPane;
  }

  @Nullable
  public DownloadableFileSetDescription getSelection() {
    return myTableModel.getDescriptionAt(myLibraryTable.getSelectedRow());
  }

  private static class MyLibraryTableModel extends AbstractTableModel {
    private final static int NAME_COL = 0;
    private final static int VERSION_COL = 1;
    private final static int URL_COL = 2;

    private final List<? extends DownloadableFileSetDescription> myDescriptions;

    public MyLibraryTableModel(List<? extends DownloadableFileSetDescription> descriptions) {
      myDescriptions = descriptions;
      Collections.sort(myDescriptions, new Comparator<DownloadableFileSetDescription>() {

        @Override
        public int compare(DownloadableFileSetDescription d1, DownloadableFileSetDescription d2) {
          if (d1.getName().equals(d2.getName())) return d1.getVersionString().compareTo(d2.getVersionString());
          return d1.getName().compareToIgnoreCase(d2.getName());
        }
      });
    }

    @Override
    public int getRowCount() {
      return myDescriptions.size();
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Nullable
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      DownloadableFileSetDescription descriptor = myDescriptions.get(rowIndex);
      switch (columnIndex) {
        case NAME_COL:
          return descriptor.getName();
        case VERSION_COL:
          return descriptor.getVersionString();
        case URL_COL:
          return descriptor.getFiles().get(0).getDownloadUrl();
      }
      return null;
    }

    @Override
    @Nullable
    public String getColumnName(int column) {
      switch (column) {
        case NAME_COL:
          return "Name";
        case VERSION_COL:
          return "Version";
        case URL_COL:
          return "URL";
      }
      return "";
    }

    @Nullable
    public DownloadableFileSetDescription getDescriptionAt(int rowIndex) {
      if (rowIndex < 0 || rowIndex >= myDescriptions.size()) {
        return null;
      }
      return myDescriptions.get(rowIndex);
    }
  }
}