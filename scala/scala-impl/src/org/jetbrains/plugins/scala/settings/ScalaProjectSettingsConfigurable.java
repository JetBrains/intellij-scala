package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author Ksenia.Sautina
 * @since 4/25/12
 */
public class ScalaProjectSettingsConfigurable implements Configurable {
  private JComponent myComponent;
  private ScalaProjectSettingsPanel myPanel;

  public ScalaProjectSettingsConfigurable(Project project) {
    myPanel = new ScalaProjectSettingsPanel(project);
    myComponent = myPanel.getPanel();
  }

  @Nls
  public String getDisplayName() {
    return "Scala";
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myComponent;
  }

  public void selectUpdatesTab() {
    myPanel.selectUpdatesTab();
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  public void reset() {
    myPanel.resetImpl();
  }

  public void disposeUIResources() {
    myPanel.dispose();
    myPanel = null;
    myComponent = null;
  }
}
