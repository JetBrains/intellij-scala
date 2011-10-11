package org.jetbrains.plugins.scala.config.ui;

import com.intellij.facet.ui.*;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.scala.config.*;
import scala.Option;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Pavel.Fatin, 26.07.2010
 */
public class FacetConfigurationEditor extends FacetEditorTab {
  private JPanel panelContent;
  private JComboBox myCompilerLibrary;
  private RawCommandLineEditor myCompilerOptions;
  private JButton addButton;
  private JButton removeButton;
  private JButton editButton;
  private JButton moveUpButton;
  private JButton moveDownButton;
  private MyTableView<CompilerPlugin> tablePlugins;
  private JPanel panelPlugins;
  private JCheckBox myEnableWarnings;
  private JCheckBox myDeprecationWarnings;
  private JCheckBox myOptimiseBytecode;
  private JCheckBox myUncheckedWarnings;
  private JComboBox myDebuggingInfoLevel;
  private JCheckBox myExplainTypeErrors;
  private JCheckBox myEnableContinuations;
  private RawCommandLineEditor myVmParameters;
  private JTextField myMaximumHeapSize;
  private JTextField basePackageField;
  private JRadioButton myFSCRadioButton;
  private JRadioButton myRunSeparateCompilerRadioButton;
  private JLabel myCompilerLibraryLabel;
  private JLabel myMaximumHeapSizeLabel;
  private JLabel myVmParametersLabel;
  private LinkLabel myFscSettings;

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
  private final LibraryRenderer myLibraryRenderer;

  static {
    CHOOSER_DESCRIPTOR.setDescription("Select Scala compiler plugin JAR");
  }
  
  public FacetConfigurationEditor(ConfigurationData data, FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    myData = data;
    myEditorContext = editorContext;
    myValidatorsManager = validatorsManager;

    myDebuggingInfoLevel.setModel(new DefaultComboBoxModel(DebuggingInfoLevel.values()));
    myLibraryRenderer = new LibraryRenderer(myCompilerLibrary);
    myCompilerLibrary.setRenderer(myLibraryRenderer);

    myEnableWarnings.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateCheckboxesState();
      }
    });

    myFSCRadioButton.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateCompilerSection();
      }
    });
    
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
        return myFSCRadioButton.isSelected()
            ? ValidationResult.OK
            : checkCompilerLibrary((LibraryDescriptor) myCompilerLibrary.getSelectedItem());
      }
    }, myCompilerLibrary, myFSCRadioButton);

    myAddPluginAction.update();
    myRemovePluginAction.update();
    myEditPluginAction.update();
    myMoveUpPluginAction.update();
    myMoveDownPluginAction.update();

    myFscSettings.setListener(new LinkListener() {
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        ShowSettingsUtil.getInstance().showSettingsDialog(myEditorContext.getProject(), "Scala Compiler");
      }
    }, null);
  }

  private void updateCompilerSection() {
    boolean b = !myFSCRadioButton.isSelected();
    myCompilerLibraryLabel.setEnabled(b);
    myCompilerLibrary.setEnabled(b);
    myMaximumHeapSizeLabel.setEnabled(b);
    myMaximumHeapSize.setEnabled(b);
    myVmParametersLabel.setEnabled(b);
    myVmParameters.setEnabled(b);
  }

  private void updateCheckboxesState() {
    boolean enabled = myEnableWarnings.isSelected();
    myDeprecationWarnings.setEnabled(enabled);
    myUncheckedWarnings.setEnabled(enabled);
  }

  private static ValidationResult checkCompilerLibrary(LibraryDescriptor descriptor) {
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
    return myCompilerLibrary;
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
    ConfigurationData data = new ConfigurationData();
    update(data);
    return !myData.equals(data);
  }

  public void apply() throws ConfigurationException {
    update(myData);
  }

  private void update(ConfigurationData data) {
    data.setBasePackage(basePackageField.getText());
    data.setFsc(myFSCRadioButton.isSelected());
    data.setCompilerLibraryName(getCompilerLibraryName());
    data.setCompilerLibraryLevel(getCompilerLibraryLevel());

    try {
      data.setMaximumHeapSize(Integer.parseInt(myMaximumHeapSize.getText().trim()));
    } catch(NumberFormatException e){
      data.setMaximumHeapSize(myData.getMaximumHeapSize());
    }
    
    data.setVmOptions(myVmParameters.getText().trim());

    data.setWarnings(myEnableWarnings.isSelected());
    data.setDeprecationWarnings(myDeprecationWarnings.isSelected());
    data.setUncheckedWarnings(myUncheckedWarnings.isSelected());
    data.setOptimiseBytecode(myOptimiseBytecode.isSelected());
    data.setExplainTypeErrors(myExplainTypeErrors.isSelected());
    data.setContinuations(myEnableContinuations.isSelected());
    
    data.setDebuggingInfoLevel((DebuggingInfoLevel) myDebuggingInfoLevel.getSelectedItem());
    data.setCompilerOptions(myCompilerOptions.getText().trim());

    data.setPluginPaths(CompilerPlugin.toPaths(myPlugins));
    
    updateCheckboxesState();
  }

  public void reset() {
    basePackageField.setText(myData.getBasePackage());
    myFSCRadioButton.setSelected(myData.getFsc());
    myRunSeparateCompilerRadioButton.setSelected(!myData.getFsc());
    updateLibrariesList();
    setCompilerLibraryById(new LibraryId(myData.getCompilerLibraryName(), myData.getCompilerLibraryLevel()));
    myMaximumHeapSize.setText(Integer.toString(myData.getMaximumHeapSize()));
    myVmParameters.setText(myData.getVmOptions());
    
    myEnableWarnings.setSelected(myData.getWarnings());
    myDeprecationWarnings.setSelected(myData.getDeprecationWarnings());
    myUncheckedWarnings.setSelected(myData.getUncheckedWarnings());
    myOptimiseBytecode.setSelected(myData.getOptimiseBytecode());
    myExplainTypeErrors.setSelected(myData.getExplainTypeErrors());
    myEnableContinuations.setSelected(myData.getContinuations());
    
    myDebuggingInfoLevel.setSelectedItem(myData.getDebuggingInfoLevel());
    myCompilerOptions.setText(myData.getCompilerOptions());

    myPlugins = new ArrayList(CompilerPlugin.fromPaths(myData.getPluginPaths(), myEditorContext.getModule()));
    getPluginsModel().setItems(myPlugins);
  }

  private void updateLibrariesList() {
    LibraryId id = getCompilerLibraryId();

    LibraryDescriptor[] items = (LibraryDescriptor[]) LibraryDescriptor.compilersFor(myEditorContext.getProject());
    DefaultComboBoxModel model = new DefaultComboBoxModel(items);
    model.insertElementAt(null, 0);
    myCompilerLibrary.setModel(model);
    myLibraryRenderer.setPrefixLength(lastIndexOfProperItemIn(items) + 1);

    setCompilerLibraryById(id);
  }

  private static int lastIndexOfProperItemIn(LibraryDescriptor[] descriptors) {
    int result = -1;
    for (LibraryDescriptor descriptor : descriptors) {
      if (descriptor.data().get().problem().isDefined()) break;
      result++;
    }
    return result;
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
    LibraryDescriptor descriptor = (LibraryDescriptor) myCompilerLibrary.getSelectedItem();
    return descriptor == null ? LibraryId.empty() : descriptor.id();  
  }
  
  public void setCompilerLibraryById(LibraryId id) {
    if(id.isEmpty()) {
//      myCompilerLibrary.addItem(null);
      myCompilerLibrary.setSelectedItem(null);
    } else {
      LibraryDescriptor descriptor = findLibraryDescriptorFor(id);
      if(descriptor == null) {
        LibraryDescriptor newId = LibraryDescriptor.createFor(id);
        myCompilerLibrary.addItem(newId);
        myCompilerLibrary.setSelectedItem(newId);
      } else {
        myCompilerLibrary.setSelectedItem(descriptor);
      }
    }
  }
  
  public LibraryDescriptor findLibraryDescriptorFor(LibraryId id) {
    DefaultComboBoxModel model = (DefaultComboBoxModel) myCompilerLibrary.getModel();
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
