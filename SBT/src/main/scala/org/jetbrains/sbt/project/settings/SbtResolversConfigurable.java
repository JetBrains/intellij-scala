package org.jetbrains.sbt.project.settings;

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.ui.table.JBTable;

import javax.swing.*;

/**
 * @author Nikolay Obedin
 * @since 7/23/14.
 */
public class SbtResolversConfigurable extends BaseConfigurable {
  private JPanel myPanel;
  private JBTable myIndicesTable;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myRemoveButton;
  private JButton myUpdateButton;

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
