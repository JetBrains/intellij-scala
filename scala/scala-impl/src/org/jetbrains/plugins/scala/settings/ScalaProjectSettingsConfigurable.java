package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.scala.help.ScalaWebHelpProvider$;

import javax.swing.*;

public class ScalaProjectSettingsConfigurable implements Configurable {

  public ScalaProjectSettingsConfigurable(Project project) {
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Scala";
  }

  @Override
  public String getHelpTopic() {
    return ScalaWebHelpProvider$.MODULE$.HelpPrefix() + "scala-features-overview-scala.html";
  }

  @Override
  public JComponent createComponent() {
    return null;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
  }
}
