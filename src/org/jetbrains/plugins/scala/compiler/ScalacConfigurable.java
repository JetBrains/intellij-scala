package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.scala.ScalaBundle;

import javax.swing.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.09.2008
 */
public class ScalacConfigurable implements Configurable {
  private RawCommandLineEditor additionalCommandLineParameters;
  private JPanel myPanel;
  private JTextField maximumHeapSizeTextField;
  private ScalacSettings mySettings;

  public ScalacConfigurable(ScalacSettings settings) {
    mySettings = settings;
    additionalCommandLineParameters.setDialogCaption(ScalaBundle.message("scala.compiler.option.additional.command.line.parameters"));
  }

  @Nls
  public String getDisplayName() {
    return null;
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
    try {
    if (Integer.parseInt(maximumHeapSizeTextField.getText()) != mySettings.MAXIMUM_HEAP_SIZE) return true;
    } catch (NumberFormatException ignored) {}
    if (!additionalCommandLineParameters.getText().equals(mySettings.ADDITIONAL_OPTIONS_STRING)) return true;
    return false;
  }

  public void apply() throws ConfigurationException {
    try {
      int maxHeapSize = Integer.parseInt(maximumHeapSizeTextField.getText());
      if (maxHeapSize < 1) mySettings.MAXIMUM_HEAP_SIZE = 128;
      else mySettings.MAXIMUM_HEAP_SIZE = maxHeapSize;
    } catch (NumberFormatException e) {
      mySettings.ADDITIONAL_OPTIONS_STRING = additionalCommandLineParameters.getText();
    }
  }

  public void reset() {
    additionalCommandLineParameters.setText("");
    maximumHeapSizeTextField.setText("128");
  }

  public void disposeUIResources() {
  }
}
