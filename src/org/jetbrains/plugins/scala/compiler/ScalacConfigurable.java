package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.scala.components.CompileServerLauncher;
import org.jetbrains.plugins.scala.components.CompileServerManager;
import org.jetbrains.plugins.scala.components.CompilerProjectComponent;
import org.jetbrains.plugins.scala.config.LibraryId;
import org.jetbrains.plugins.scala.config.LibraryLevel;
import org.jetbrains.plugins.scala.config.ui.LibraryDescriptor;
import org.jetbrains.plugins.scala.config.ui.LibraryRenderer;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

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
  private JRadioButton myRunInternalServerRadioButton;
  private JRadioButton myConnectToExternalServerRadioButton;
  private JTextField myRemotePort;
  private JTextField myRemoteHost;
  private TextFieldWithBrowseButton mySharedDirectory;
  private JPanel myServerPanel;
  private JPanel myClientPanel;
  private JTextField myIdleTimeout;
  private ScalacSettings mySettings;
  private Project myProject;
  private final LibraryRenderer myLibraryRenderer;

  public ScalacConfigurable(ScalacSettings settings, Project project) {
    myProject = project;
    mySettings = settings;
    $$$setupUI$$$();
    myLibraryRenderer = new LibraryRenderer(myCompilerLibrary);
    myCompilerLibrary.setRenderer(myLibraryRenderer);

    myRunInternalServerRadioButton.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateSections();
      }
    });

    updateLibrariesList();
  }


  private void updateLibrariesList() {
    LibraryId id = getCompilerLibraryId();

    LibraryDescriptor[] items = (LibraryDescriptor[]) LibraryDescriptor.compilersFor(myProject);
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

  private void updateSections() {
    boolean b = myRunInternalServerRadioButton.isSelected();
    setDescendantsEnabledIn(myServerPanel, b);
    setDescendantsEnabledIn(myClientPanel, !b);
  }

  private static void setDescendantsEnabledIn(JComponent root, boolean b) {
    for (Component child : root.getComponents()) {
      child.setEnabled(b);
      setDescendantsEnabledIn((JComponent) child, b);
    }
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
    if (!mySettings.IDLE_TIMEOUT.equals(myIdleTimeout.getText())) return true;
    if (!mySettings.FSC_OPTIONS.equals(myFscOptions.getText())) return true;
    if (mySettings.INTERNAL_SERVER != myRunInternalServerRadioButton.isSelected()) return true;
    if (!mySettings.REMOTE_HOST.equals(myRemoteHost.getText())) return true;
    if (!mySettings.REMOTE_PORT.equals(myRemotePort.getText())) return true;
    if (!mySettings.SHARED_DIRECTORY.equals(mySharedDirectory.getText())) return true;

    return false;
  }

  public void apply() throws ConfigurationException {
    mySettings.MAXIMUM_HEAP_SIZE = myMaximumHeapSize.getText();
    mySettings.VM_PARAMETERS = myVmParameters.getText();
    mySettings.IDLE_TIMEOUT = myIdleTimeout.getText();
    mySettings.FSC_OPTIONS = myFscOptions.getText();
    mySettings.COMPILER_LIBRARY_NAME = getCompilerLibraryName();
    mySettings.COMPILER_LIBRARY_LEVEL = getCompilerLibraryLevel();
    mySettings.INTERNAL_SERVER = myRunInternalServerRadioButton.isSelected();
    mySettings.REMOTE_HOST = myRemoteHost.getText();
    mySettings.REMOTE_PORT = myRemotePort.getText();
    mySettings.SHARED_DIRECTORY = mySharedDirectory.getText();

    if (!myRunInternalServerRadioButton.isSelected()) {
      myProject.getComponent(CompileServerLauncher.class).stop();
    }
    myProject.getComponent(CompileServerManager.class).configureWidget();

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
    myIdleTimeout.setText(mySettings.IDLE_TIMEOUT);
    myFscOptions.setText(mySettings.FSC_OPTIONS);
    myRunInternalServerRadioButton.setSelected(mySettings.INTERNAL_SERVER);
    myConnectToExternalServerRadioButton.setSelected(!mySettings.INTERNAL_SERVER);
    myRemoteHost.setText(mySettings.REMOTE_HOST);
    myRemotePort.setText(mySettings.REMOTE_PORT);
    mySharedDirectory.setText(mySettings.SHARED_DIRECTORY);

    updateSections();
  }

  public void disposeUIResources() {
  }

  private void $$$setupUI$$$()
  {
    JPanel jpanel = new JPanel();
    myPanel = jpanel;
    jpanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1, false, false));
    Spacer spacer = new Spacer();
    jpanel.add(spacer, new GridConstraints(2, 0, 1, 1, 0, 2, 1, 6, null, null, null));
    JPanel jpanel1 = new JPanel();
    jpanel1.setLayout(new GridLayoutManager(5, 2, new Insets(3, 3, 3, 3), -1, -1, false, false));
    jpanel.add(jpanel1, new GridConstraints(1, 0, 1, 1, 0, 3, 3, 3, null, null, null));
    jpanel1.setBorder(BorderFactory.createTitledBorder(null, "Project FSC", 0, 0, null, null));
    JLabel jlabel = new JLabel();
    jlabel.setEnabled(true);
    jlabel.setText("Compiler library:");
    jlabel.setDisplayedMnemonic('L');
    jlabel.setDisplayedMnemonicIndex(9);
    jpanel1.add(jlabel, new GridConstraints(0, 0, 1, 1, 8, 0, 0, 0, null, null, null));
    JComboBox jcombobox = new JComboBox();
    myCompilerLibrary = jcombobox;
    jpanel1.add(jcombobox, new GridConstraints(0, 1, 1, 1, 8, 0, 2, 0, null, null, null));
    JRadioButton jradiobutton = new JRadioButton();
    myRunInternalServerRadioButton = jradiobutton;
    jradiobutton.setText("Run internal server");
    jradiobutton.setMnemonic('I');
    jradiobutton.setDisplayedMnemonicIndex(4);
    jpanel1.add(jradiobutton, new GridConstraints(1, 0, 1, 2, 8, 0, 3, 0, null, null, null));
    JRadioButton jradiobutton1 = new JRadioButton();
    myConnectToExternalServerRadioButton = jradiobutton1;
    jradiobutton1.setText("Connect to external server");
    jradiobutton1.setMnemonic('E');
    jradiobutton1.setDisplayedMnemonicIndex(11);
    jpanel1.add(jradiobutton1, new GridConstraints(3, 0, 1, 2, 8, 0, 3, 0, null, null, null));
    JPanel jpanel2 = new JPanel();
    myServerPanel = jpanel2;
    jpanel2.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1, false, false));
    jpanel1.add(jpanel2, new GridConstraints(2, 0, 1, 2, 0, 3, 3, 3, null, null, null, 1));
    JLabel jlabel1 = new JLabel();
    jlabel1.setEnabled(true);
    jlabel1.setText("VM parameters:");
    jlabel1.setDisplayedMnemonic('V');
    jlabel1.setDisplayedMnemonicIndex(0);
    jpanel2.add(jlabel1, new GridConstraints(1, 0, 1, 1, 8, 0, 0, 0, null, null, null));
    JLabel jlabel2 = new JLabel();
    jlabel2.setEnabled(true);
    jlabel2.setText("FSC server options:");
    jlabel2.setDisplayedMnemonic('O');
    jlabel2.setDisplayedMnemonicIndex(11);
    jpanel2.add(jlabel2, new GridConstraints(3, 0, 1, 1, 8, 0, 0, 0, null, null, null));
    JLabel jlabel3 = new JLabel();
    jlabel3.setEnabled(true);
    jlabel3.setText("Maximum heap size, MB:");
    jlabel3.setDisplayedMnemonic('M');
    jlabel3.setDisplayedMnemonicIndex(0);
    jpanel2.add(jlabel3, new GridConstraints(0, 0, 1, 1, 8, 0, 0, 0, null, null, null));
    JTextField jtextfield = new JTextField();
    myMaximumHeapSize = jtextfield;
    jtextfield.setColumns(5);
    jtextfield.setEnabled(true);
    jpanel2.add(jtextfield, new GridConstraints(0, 1, 1, 1, 8, 0, 6, 0, null, null, null));
    RawCommandLineEditor rawcommandlineeditor = new RawCommandLineEditor();
    myFscOptions = rawcommandlineeditor;
    rawcommandlineeditor.setDialogCaption("Additional command-line options for Scala compiler");
    jpanel2.add(rawcommandlineeditor, new GridConstraints(3, 1, 1, 1, 8, 0, 7, 3, new Dimension(250, -1), null, null));
    RawCommandLineEditor rawcommandlineeditor1 = new RawCommandLineEditor();
    myVmParameters = rawcommandlineeditor1;
    rawcommandlineeditor1.setEnabled(true);
    rawcommandlineeditor1.setDialogCaption("Java VM command line parameters");
    jpanel2.add(rawcommandlineeditor1, new GridConstraints(1, 1, 1, 1, 8, 0, 7, 3, new Dimension(250, -1), null, null));
    JLabel jlabel4 = new JLabel();
    jlabel4.setEnabled(true);
    jlabel4.setText("Idle timeout, minutes");
    jlabel4.setDisplayedMnemonic('T');
    jlabel4.setDisplayedMnemonicIndex(5);
    jpanel2.add(jlabel4, new GridConstraints(2, 0, 1, 1, 8, 0, 0, 0, null, null, null));
    JPanel jpanel3 = new JPanel();
    jpanel3.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, false, false));
    jpanel2.add(jpanel3, new GridConstraints(2, 1, 1, 1, 0, 3, 3, 3, null, null, null));
    JLabel jlabel5 = new JLabel();
    jlabel5.setText("(use 0 for no timeout)");
    jpanel3.add(jlabel5, new GridConstraints(0, 1, 1, 1, 8, 0, 4, 0, null, null, null));
    JTextField jtextfield1 = new JTextField();
    myIdleTimeout = jtextfield1;
    jtextfield1.setColumns(5);
    jtextfield1.setEnabled(true);
    jpanel3.add(jtextfield1, new GridConstraints(0, 0, 1, 1, 8, 0, 2, 0, null, null, null));
    JPanel jpanel4 = new JPanel();
    myClientPanel = jpanel4;
    jpanel4.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1, false, false));
    jpanel1.add(jpanel4, new GridConstraints(4, 0, 1, 2, 0, 3, 3, 3, null, null, null, 1));
    TextFieldWithBrowseButton textfieldwithbrowsebutton = new TextFieldWithBrowseButton();
    mySharedDirectory = textfieldwithbrowsebutton;
    jpanel4.add(textfieldwithbrowsebutton, new GridConstraints(1, 1, 1, 1, 8, 0, 3, 3, new Dimension(250, -1), null, null));
    JLabel jlabel6 = new JLabel();
    jlabel6.setEnabled(true);
    jlabel6.setText("Shared directory:");
    jlabel6.setDisplayedMnemonic('D');
    jlabel6.setDisplayedMnemonicIndex(7);
    jpanel4.add(jlabel6, new GridConstraints(1, 0, 1, 1, 8, 0, 0, 0, null, null, null));
    JLabel jlabel7 = new JLabel();
    jlabel7.setEnabled(true);
    jlabel7.setText("Host / Port:");
    jlabel7.setDisplayedMnemonic('H');
    jlabel7.setDisplayedMnemonicIndex(0);
    jpanel4.add(jlabel7, new GridConstraints(0, 0, 1, 1, 8, 0, 0, 0, null, null, null));
    JPanel jpanel5 = new JPanel();
    jpanel5.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, false, false));
    jpanel4.add(jpanel5, new GridConstraints(0, 1, 1, 1, 0, 3, 3, 3, null, null, null));
    JTextField jtextfield2 = new JTextField();
    myRemoteHost = jtextfield2;
    jtextfield2.setColumns(15);
    jtextfield2.setEnabled(true);
    jpanel5.add(jtextfield2, new GridConstraints(0, 0, 1, 1, 8, 0, 2, 0, null, new Dimension(150, -1), null));
    JTextField jtextfield3 = new JTextField();
    myRemotePort = jtextfield3;
    jtextfield3.setColumns(5);
    jtextfield3.setEnabled(true);
    jpanel5.add(jtextfield3, new GridConstraints(0, 1, 1, 1, 8, 0, 6, 0, null, null, null));
    JPanel jpanel6 = new JPanel();
    jpanel6.setLayout(new GridLayoutManager(2, 1, new Insets(3, 3, 3, 3), -1, -1, false, false));
    jpanel.add(jpanel6, new GridConstraints(0, 0, 1, 1, 0, 3, 3, 3, null, null, null));
    jpanel6.setBorder(BorderFactory.createTitledBorder(null, "Join compilation", 0, 0, null, null));
    JRadioButton jradiobutton2 = new JRadioButton();
    scalacBeforeRadioButton = jradiobutton2;
    jradiobutton2.setToolTipText("Can compile circular dependencies, rely on Scalac Java parser and typer");
    jradiobutton2.setText("Scalac(*.scala + *.java), Javac(*.java)");
    jradiobutton2.setMnemonic('S');
    jradiobutton2.setDisplayedMnemonicIndex(0);
    jpanel6.add(jradiobutton2, new GridConstraints(0, 0, 1, 1, 8, 0, 3, 0, null, null, null));
    JRadioButton jradiobutton3 = new JRadioButton();
    jradiobutton3.setToolTipText("Can't compile circular dependencies, consume Java as compiled classes");
    jradiobutton3.setText("Javac(*.java), Scalac(*.scala)");
    jradiobutton3.setMnemonic('J');
    jradiobutton3.setDisplayedMnemonicIndex(0);
    jpanel6.add(jradiobutton3, new GridConstraints(1, 0, 1, 1, 8, 0, 3, 0, null, null, null));
    jlabel.setLabelFor(jtextfield);
    jlabel3.setLabelFor(jtextfield);
    jlabel4.setLabelFor(jtextfield1);
    jlabel7.setLabelFor(jtextfield2);
    ButtonGroup buttongroup = new ButtonGroup();
    buttongroup.add(jradiobutton2);
    buttongroup.add(jradiobutton3);
    buttongroup = new ButtonGroup();
    buttongroup.add(jradiobutton);
    buttongroup.add(jradiobutton1);
  }

  public JComponent $$$getRootComponent$$$()
  {
    return myPanel;
  }
}
