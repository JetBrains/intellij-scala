package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

public class ScalaProjectSettingsConfigurable implements Configurable {
  private JComponent myComponent;
  private ScalaProjectSettingsPanel myPanel;

  public ScalaProjectSettingsConfigurable(Project project) {
    myPanel = new ScalaProjectSettingsPanel(project);
    myComponent = myPanel.getPanel();
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Scala";
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    return myComponent;
  }

  public void selectXRayModeTab() {
    myPanel.selectXRayModeTab();
  }

  public void selectUpdatesTab() {
    myPanel.selectUpdatesTab();
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  @Override
  public void reset() {
    myPanel.resetImpl();
  }

  @Override
  public void disposeUIResources() {
    myPanel.dispose();
    myPanel = null;
    myComponent = null;
  }
}
