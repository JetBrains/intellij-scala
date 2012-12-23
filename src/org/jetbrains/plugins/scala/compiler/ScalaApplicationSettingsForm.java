package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkListCellRenderer;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.JBColor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.containers.ComparatorUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Pavel Fatin
 */
public class ScalaApplicationSettingsForm implements Configurable {
  private JPanel myCompilationServerPanel;
  private RawCommandLineEditor myCompilationServerJvmParameters;
  private JTextField myCompilationServerPort;
  private JTextField myCompilationServerMaximumHeapSize;
  private JCheckBox myEnableCompileServer;
  private JPanel myContentPanel;
  private JComboBox myCompilationServerSdk;
  private MultiLineLabel myNote;

  private ScalaApplicationSettings mySettings;

  public ScalaApplicationSettingsForm(ScalaApplicationSettings settings) {
    mySettings = settings;
    
    myEnableCompileServer.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateCompilationServerSettingsPanel();
      }
    });

    myCompilationServerSdk.setRenderer(new SdkRenderer());
    myCompilationServerSdk.setModel(new CollectionComboBoxModel(getAllJdk(), null));

    myNote.setForeground(JBColor.GRAY);

    updateCompilationServerSettingsPanel();
  }

  private static List<Sdk> getAllJdk() {
    List<Sdk> result = new ArrayList<Sdk>();

    result.add(null);
    result.addAll(ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()));

    return result;
  }

  private void updateCompilationServerSettingsPanel() {
    setDescendantsEnabledIn(myCompilationServerPanel, myEnableCompileServer.isSelected());
    myNote.setEnabled(true);
  }

  private static void setDescendantsEnabledIn(JComponent root, boolean b) {
    for (Component child : root.getComponents()) {
      child.setEnabled(b);
      if (child instanceof JComponent) {
        setDescendantsEnabledIn((JComponent) child, b);
      }
    }
  }

  @Nls
  public String getDisplayName() {
    return "Scala";
  }

  @Nullable
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  public JComponent createComponent() {
    return myContentPanel;
  }

  public boolean isModified() {
    Sdk sdk = (Sdk) myCompilationServerSdk.getSelectedItem();
    String sdkName = sdk == null ? null : sdk.getName();

    return !(myEnableCompileServer.isSelected() == mySettings.COMPILE_SERVER_ENABLED &&
        myCompilationServerPort.getText().equals(mySettings.COMPILE_SERVER_PORT) &&
        ComparatorUtil.equalsNullable(sdkName, mySettings.COMPILE_SERVER_SDK) &&
        myCompilationServerMaximumHeapSize.getText().equals(mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE) &&
        myCompilationServerJvmParameters.getText().equals(mySettings.COMPILE_SERVER_JVM_PARAMETERS));
  }

  public void apply() throws ConfigurationException {
    mySettings.COMPILE_SERVER_ENABLED = myEnableCompileServer.isSelected();
    mySettings.COMPILE_SERVER_PORT = myCompilationServerPort.getText();

    Sdk sdk = (Sdk) myCompilationServerSdk.getSelectedItem();
    mySettings.COMPILE_SERVER_SDK = sdk == null ? null : sdk.getName();

    mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE = myCompilationServerMaximumHeapSize.getText();
    mySettings.COMPILE_SERVER_JVM_PARAMETERS = myCompilationServerJvmParameters.getText();

    // TODO
//    boolean externalCompiler = CompilerWorkspaceConfiguration.getInstance(myProject).USE_COMPILE_SERVER;
//
//    if (!externalCompiler || !myEnableCompileServer.isSelected()) {
//      myProject.getComponent(CompileServerLauncher.class).stop();
//    }
//    myProject.getComponent(CompileServerManager.class).configureWidget();
  }

  public void reset() {
    myEnableCompileServer.setSelected(mySettings.COMPILE_SERVER_ENABLED);
    myCompilationServerPort.setText(mySettings.COMPILE_SERVER_PORT);

    Sdk sdk = mySettings.COMPILE_SERVER_SDK == null
        ? null
        : ProjectJdkTable.getInstance().findJdk(mySettings.COMPILE_SERVER_SDK);
    myCompilationServerSdk.setSelectedItem(sdk);

    myCompilationServerMaximumHeapSize.setText(mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE);
    myCompilationServerJvmParameters.setText(mySettings.COMPILE_SERVER_JVM_PARAMETERS);
  }

  public void disposeUIResources() {
  }
}
