package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.scala.components.CompilerProjectComponent;
import org.jetbrains.plugins.scala.config.LibraryId;
import org.jetbrains.plugins.scala.config.LibraryLevel;
import org.jetbrains.plugins.scala.config.ui.LibraryDescriptor;
import org.jetbrains.plugins.scala.config.ui.LibraryRenderer;

import javax.swing.*;

/**
 * User: Alexander Podkhalyuzin, Pavel Fatin
 * Date: 22.09.2008
 */
public class ScalacConfigurable implements Configurable {
  private JPanel myPanel;
  private JRadioButton scalacBeforeRadioButton;
  private RawCommandLineEditor myVmParameters;
  private JTextField myMaximumHeapSize;
  private RawCommandLineEditor myFscOptions;
  private JComboBox myCompilerLibrary;
  private ScalacSettings mySettings;
  private Project myProject;

  public ScalacConfigurable(ScalacSettings settings, Project project) {
    myProject = project;
    mySettings = settings;
    myCompilerLibrary.setRenderer(new LibraryRenderer());
    updateLibrariesList();
  }


  private void updateLibrariesList() {
    LibraryId id = getCompilerLibraryId();

    myCompilerLibrary.setModel(new DefaultComboBoxModel(LibraryDescriptor.compilersFor(myProject)));

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
    LibraryDescriptor descriptor = (LibraryDescriptor) myCompilerLibrary.getSelectedItem();
    return descriptor == null ? LibraryId.empty() : descriptor.id();
  }

  public void setCompilerLibraryById(LibraryId id) {
    if(id.isEmpty()) {
      myCompilerLibrary.addItem(null);
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

  @Nls
  public String getDisplayName() {
    return "Scala Compiler";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    if (mySettings.SCALAC_BEFORE != scalacBeforeRadioButton.isSelected()) return true;
    if (!mySettings.COMPILER_LIBRARY_NAME.equals(getCompilerLibraryName())) return true;
    if (mySettings.COMPILER_LIBRARY_LEVEL != getCompilerLibraryLevel()) return true;
    if (!mySettings.MAXIMUM_HEAP_SIZE.equals(myMaximumHeapSize.getText())) return true;
    if (!mySettings.VM_PARAMETERS.equals(myVmParameters.getText())) return true;
    if (!mySettings.FSC_OPTIONS.equals(myFscOptions.getText())) return true;

    return false;
  }

  public void apply() throws ConfigurationException {
    mySettings.MAXIMUM_HEAP_SIZE = myMaximumHeapSize.getText();
    mySettings.VM_PARAMETERS = myVmParameters.getText();
    mySettings.FSC_OPTIONS = myFscOptions.getText();
    mySettings.COMPILER_LIBRARY_NAME = getCompilerLibraryName();
    mySettings.COMPILER_LIBRARY_LEVEL = getCompilerLibraryLevel();

    CompilerProjectComponent component = myProject.getComponent(CompilerProjectComponent.class);

    if (scalacBeforeRadioButton.isSelected() && mySettings.SCALAC_BEFORE != scalacBeforeRadioButton.isSelected()) {
      component.configureToCompileScalaFirst();
    } else if (!scalacBeforeRadioButton.isSelected() && mySettings.SCALAC_BEFORE != scalacBeforeRadioButton.isSelected()){
      component.configureToCompileJavaFirst();
    }

    mySettings.SCALAC_BEFORE = scalacBeforeRadioButton.isSelected();
  }

  public void reset() {
    scalacBeforeRadioButton.setSelected(mySettings.SCALAC_BEFORE);
    updateLibrariesList();
    setCompilerLibraryById(new LibraryId(mySettings.COMPILER_LIBRARY_NAME, mySettings.COMPILER_LIBRARY_LEVEL));
    myMaximumHeapSize.setText(mySettings.MAXIMUM_HEAP_SIZE);
    myVmParameters.setText(mySettings.VM_PARAMETERS);
    myFscOptions.setText(mySettings.FSC_OPTIONS);
  }

  public void disposeUIResources() {
  }
}
