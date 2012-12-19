package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.RawCommandLineEditor;
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
  
  private ScalaApplicationSettings mySettings;

  public ScalaApplicationSettingsForm(ScalaApplicationSettings settings) {
    mySettings = settings;
    
    myEnableCompileServer.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        updateCompilationServerSettingsPanel();
      }
    });
  }

  private void updateCompilationServerSettingsPanel() {
    setDescendantsEnabledIn(myCompilationServerPanel, myEnableCompileServer.isSelected());
  }

  private static void setDescendantsEnabledIn(JComponent root, boolean b) {
    for (Component child : root.getComponents()) {
      child.setEnabled(b);
      setDescendantsEnabledIn((JComponent) child, b);
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
    return !(myEnableCompileServer.isSelected() == mySettings.COMPILE_SERVER_ENABLED &&
        myCompilationServerPort.getText().equals(mySettings.COMPILE_SERVER_PORT) &&
        myCompilationServerMaximumHeapSize.getText().equals(mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE) &&
        myCompilationServerJvmParameters.getText().equals(mySettings.COMPILE_SERVER_JVM_PARAMETERS));
  }

  public void apply() throws ConfigurationException {
    mySettings.COMPILE_SERVER_ENABLED = myEnableCompileServer.isSelected();
    mySettings.COMPILE_SERVER_PORT = myCompilationServerPort.getText();
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
    myCompilationServerMaximumHeapSize.setText(mySettings.COMPILE_SERVER_MAXIMUM_HEAP_SIZE);
    myCompilationServerJvmParameters.setText(mySettings.COMPILE_SERVER_JVM_PARAMETERS);
  }

  public void disposeUIResources() {
  }
}
