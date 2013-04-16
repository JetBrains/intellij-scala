package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.ui.JBColor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.containers.ComparatorUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

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
  private JdkComboBox myCompilationServerSdk;
  private MultiLineLabel myNote;
  private JPanel mySdkPanel;

  private ScalaApplicationSettings mySettings;

  public ScalaApplicationSettingsForm(ScalaApplicationSettings settings) {
    mySettings = settings;
    
    myEnableCompileServer.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateCompilationServerSettingsPanel();
      }
    });

    ProjectSdksModel model = new ProjectSdksModel();
    model.reset(null);

    myCompilationServerSdk = new JdkComboBox(model);
    myCompilationServerSdk.insertItemAt(new JdkComboBox.NoneJdkComboBoxItem(), 0);

    mySdkPanel.add(myCompilationServerSdk, BorderLayout.CENTER);

    myNote.setForeground(JBColor.GRAY);

    updateCompilationServerSettingsPanel();
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
    Sdk sdk = myCompilationServerSdk.getSelectedJdk();
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

    Sdk sdk = myCompilationServerSdk.getSelectedJdk();
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
    myCompilationServerSdk.setSelectedJdk(sdk);

    myCompilationServerMaximumHeapSize.setText(mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE);
    myCompilationServerJvmParameters.setText(mySettings.COMPILE_SERVER_JVM_PARAMETERS);
  }

  public void disposeUIResources() {
  }
}
