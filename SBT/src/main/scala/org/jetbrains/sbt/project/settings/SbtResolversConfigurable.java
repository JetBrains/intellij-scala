package org.jetbrains.sbt.project.settings;

import com.intellij.openapi.options.BaseConfigurable;

import javax.swing.*;

/**
 * @author Nikolay Obedin
 * @since 7/23/14.
 */
public class SbtResolversConfigurable extends BaseConfigurable {
  private JPanel myPanel;

  public String getDisplayName() {
    return "Resolvers";
  }

  public String getHelpTopic() {
    return null;
  }

  public void disposeUIResources() {
  }

  public void reset() {
  }

  public void apply() {
  }

  public JComponent createComponent() {
    return myPanel;
  }
}
