package org.jetbrains.plugins.scala.config.ui;

import com.intellij.facet.ui.*;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.scala.config.*;
import org.jetbrains.plugins.scala.config.LibraryDescriptor;
import scala.Option;

import javax.swing.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pavel.Fatin, 26.07.2010
 */
public class FacetConfigurationEditor extends FacetEditorTab {
  private JPanel panelContent;
  private JComboBox comboCompilerLibrary;
  private RawCommandLineEditor fieldCompilerOptions;
  private JButton addButton;
  private JButton removeButton;
  private JButton editButton;
  private JButton moveUpButton;
  private JButton moveDownButton;
  private MyTableView<CompilerPlugin> tablePlugins;
  private JPanel panelPlugins;

  private MyAction myAddPluginAction = new AddPluginAction();
  private MyAction myRemovePluginAction = new RemovePluginAction();
  private MyAction myEditPluginAction = new EditPluginAction();
  private MyAction myMoveUpPluginAction = new MoveUpPluginAction();
  private MyAction myMoveDownPluginAction = new MoveDownPluginAction();
  
  private ConfigurationData myData;
  private FacetEditorContext myEditorContext;
  private FacetValidatorsManager myValidatorsManager;
  private List<CompilerPlugin> myPlugins = new ArrayList<CompilerPlugin>();
  
  private static final FileChooserDescriptor CHOOSER_DESCRIPTOR = 
      new FileChooserDescriptor(false, false, true, true, false, true);
  
  
  public FacetConfigurationEditor(ConfigurationData data, FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    myData = data;
    myEditorContext = editorContext;
    myValidatorsManager = validatorsManager;

    comboCompilerLibrary.setRenderer(new LibraryRenderer());

    CompilerPluginsTableModel model = new CompilerPluginsTableModel();
    model.setItems(myPlugins);
    tablePlugins.setModel(model);
    
    addButton.setAction(myAddPluginAction);
    removeButton.setAction(myRemovePluginAction);
    editButton.setAction(myEditPluginAction);
    moveUpButton.setAction(myMoveUpPluginAction);
    moveDownButton.setAction(myMoveDownPluginAction);
    
    myAddPluginAction.registerOn(panelPlugins);
    myRemovePluginAction.registerOn(tablePlugins);
    myEditPluginAction.registerOn(tablePlugins);
    myMoveUpPluginAction.registerOn(tablePlugins);
    myMoveDownPluginAction.registerOn(tablePlugins);

    ListSelectionModel selectionModel = tablePlugins.getSelectionModel();
    selectionModel.addListSelectionListener(myAddPluginAction);
    selectionModel.addListSelectionListener(myRemovePluginAction);
    selectionModel.addListSelectionListener(myEditPluginAction);
    selectionModel.addListSelectionListener(myMoveUpPluginAction);
    selectionModel.addListSelectionListener(myMoveDownPluginAction);
    
    tablePlugins.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if(e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
          myEditPluginAction.perform();
        }
      }
    });
    
    myValidatorsManager.registerValidator(new FacetEditorValidator() {
      @Override
      public ValidationResult check() {
        return checCompilerLibrary((LibraryDescriptor) comboCompilerLibrary.getSelectedItem());
      }
    }, comboCompilerLibrary);
    
    
    myAddPluginAction.update();
    myRemovePluginAction.update();
    myEditPluginAction.update();
    myMoveUpPluginAction.update();
    myMoveDownPluginAction.update();
  }
  
  private static ValidationResult checCompilerLibrary(LibraryDescriptor descriptor) {
    if(descriptor == null || descriptor.data().isEmpty()) 
      return ValidationResult.OK;

    String libraryName = "Compiler library";
        
    CompilerLibraryData compilerLibraryData = (CompilerLibraryData) descriptor.data().get();
    
    Option<String> compilerLibraryProblem = compilerLibraryData.problem();
        
    if(compilerLibraryProblem.isDefined()) 
      return new ValidationResult(libraryName + ": " + compilerLibraryProblem.get());
      
    return ValidationResult.OK;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return comboCompilerLibrary;
  }

  @Override
  public void onTabEntering() {
    updateLibrariesList();
  }

  private CompilerPluginsTableModel getPluginsModel() {
    return (CompilerPluginsTableModel) tablePlugins.getModel();
  }

  @Nls
  public String getDisplayName() {
    return "Scala";
  }

  public JComponent createComponent() {
    return panelContent;
  }

  public boolean isModified() {
    return !myData.getCompilerLibraryName().equals(getCompilerLibraryName()) ||
        myData.getCompilerLibraryLevel() != getCompilerLibraryLevel() ||
        !myData.getCompilerOptions().equals(fieldCompilerOptions.getText()) ||
        !Arrays.equals(myData.getPluginPaths(), CompilerPlugin.toPaths(myPlugins));
  }

  public void apply() throws ConfigurationException {
    myData.setCompilerLibraryName(getCompilerLibraryName());
    myData.setCompilerLibraryLevel(getCompilerLibraryLevel());
    myData.setCompilerOptions(fieldCompilerOptions.getText());
    myData.setPluginPaths(CompilerPlugin.toPaths(myPlugins));
  }

  public void reset() {
    updateLibrariesList();
    
    setCompilerLibraryById(new LibraryId(myData.getCompilerLibraryName(), myData.getCompilerLibraryLevel()));
    fieldCompilerOptions.setText(myData.getCompilerOptions());

    myPlugins = new ArrayList(CompilerPlugin.fromPaths(myData.getPluginPaths(), myEditorContext.getModule()));
    getPluginsModel().setItems(myPlugins);
  }

  private void updateLibrariesList() {
    LibraryId id = getCompilerLibraryId();

    comboCompilerLibrary.setModel(new DefaultComboBoxModel(
        LibraryDescriptor.compilersFor(myEditorContext.getProject())));

    setCompilerLibraryById(id);
  }

  private String getCompilerLibraryName() {
    LibraryId id = getCompilerLibraryId();
    return id == null ? "" : id.name();
  }

  private LibraryLevel getCompilerLibraryLevel() {
    LibraryId id = getCompilerLibraryId();
    return id == null ? null : id.level();
  }
  
  private LibraryId getCompilerLibraryId() {
    LibraryDescriptor descriptor = (LibraryDescriptor) comboCompilerLibrary.getSelectedItem();
    return descriptor == null ? LibraryId.empty() : descriptor.id();  
  }
  
  public void setCompilerLibraryById(LibraryId id) {
    if(id.isEmpty()) {
      comboCompilerLibrary.addItem(null);
      comboCompilerLibrary.setSelectedItem(null);
    } else {
      LibraryDescriptor descriptor = findLibraryDescriptorFor(id);
      if(descriptor == null) {
        LibraryDescriptor newId = LibraryDescriptor.createFor(id);
        comboCompilerLibrary.addItem(newId);
        comboCompilerLibrary.setSelectedItem(newId);
      } else {
        comboCompilerLibrary.setSelectedItem(descriptor);
      }
    }
  }
  
  public LibraryDescriptor findLibraryDescriptorFor(LibraryId id) {
    DefaultComboBoxModel model = (DefaultComboBoxModel) comboCompilerLibrary.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      LibraryDescriptor entry = (LibraryDescriptor) model.getElementAt(i);
      if(entry != null && entry.id().equals(id)) {
        return entry;
      }
    }
    return null;
  } 

  public void disposeUIResources() {
  }

  private class AddPluginAction extends MyAction {
    private AddPluginAction() {
      super("ACTION_ADD", "&Add...", KeyEvent.VK_INSERT, KeyEvent.ALT_DOWN_MASK);
    }

    public void actionPerformed(ActionEvent e) {
      VirtualFile[] files = FileChooser.chooseFiles(myEditorContext.getProject(), CHOOSER_DESCRIPTOR);
      tablePlugins.clearSelection();
      for(VirtualFile file : files) {
        String path = CompilerPlugin.pathTo(VfsUtil.virtualToIoFile(file), myEditorContext.getModule());
        CompilerPlugin item = new CompilerPlugin(path, myEditorContext.getModule());
        getPluginsModel().addRow(item);
        tablePlugins.addSelection(item);
      }
      tablePlugins.requestFocusInWindow();
    }
  }
  
  private class RemovePluginAction extends MyAction {
    private RemovePluginAction() {
      super("ACTION_REMOVE", "&Remove", KeyEvent.VK_DELETE);
    }

    public void actionPerformed(ActionEvent e) {
      tablePlugins.removeSelection();
      tablePlugins.requestFocusInWindow();
    }
    
    @Override
    public boolean isActive() {
      return tablePlugins.hasSelection();
    }
  }
  
  private class EditPluginAction extends MyAction {
    private EditPluginAction() {
      super("ACTION_EDIT", "&Edit...", KeyEvent.VK_ENTER);
    }

    public void actionPerformed(ActionEvent e) {
      int index = tablePlugins.getSelectedRow();
      CompilerPlugin plugin = (CompilerPlugin) getPluginsModel().getItem(index);
      EditPathDialog dialog = new EditPathDialog(myEditorContext.getProject(), CHOOSER_DESCRIPTOR);
      dialog.setPath(plugin.path());
      dialog.show();
      if(dialog.isOK()) {
        String path = CompilerPlugin.pathTo(new File(dialog.getPath()), myEditorContext.getModule());
        myPlugins.set(index, new CompilerPlugin(path, myEditorContext.getModule()));
        getPluginsModel().fireTableRowsUpdated(index, index);
      }
    }
    
    @Override
    public boolean isActive() {
      return tablePlugins.hasSingleSelection();
    }
  }

  private class MoveUpPluginAction extends MyAction {
    private MoveUpPluginAction() {
      super("ACTION_MOVE_UP", "Move &Up", KeyEvent.VK_UP, KeyEvent.CTRL_DOWN_MASK);
    }

    public void actionPerformed(ActionEvent e) {
      tablePlugins.moveSelectionUpUsing(myPlugins);
      tablePlugins.requestFocusInWindow();
    }

    @Override
    public boolean isActive() {
      return tablePlugins.isNotFirstRowSelected();
    }
  }
  
  private class MoveDownPluginAction extends MyAction {
    private MoveDownPluginAction() {
      super("ACTION_MOVE_DOWN", "Move &Down", KeyEvent.VK_DOWN, KeyEvent.CTRL_DOWN_MASK);
    }

    public void actionPerformed(ActionEvent e) {
      tablePlugins.moveSelectionDownUsing(myPlugins);
      tablePlugins.requestFocusInWindow();
    }

    @Override
    public boolean isActive() {
      return tablePlugins.isNotLastRowSelected();
    }
  }
  
}
