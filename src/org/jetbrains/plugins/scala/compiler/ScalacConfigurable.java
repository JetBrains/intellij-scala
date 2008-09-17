package org.jetbrains.plugins.scala.compiler;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 10.09.2008
 */
public class ScalacConfigurable implements Configurable{
  private JPanel myPanel;
  private JTextField myScalacMaximumHeapField;
  private RawCommandLineEditor myAdditionalOptionField;

  public ScalacConfigurable() {
    //todo [Sasha] please, use internationalized resource boundles 
    myAdditionalOptionField.setDialogCaption("Scalac options");
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
    return false;
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
  }

  public void disposeUIResources() {
  }
}
