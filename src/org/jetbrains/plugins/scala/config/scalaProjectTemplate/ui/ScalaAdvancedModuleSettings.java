package org.jetbrains.plugins.scala.config.scalaProjectTemplate.ui;

import org.jetbrains.plugins.scala.components.TypeAwareHighlightingApplicationState;

import javax.swing.*;

/**
 * User: Dmitry Naydanov
 * Date: 11/12/12
 */
public class ScalaAdvancedModuleSettings {
  private JCheckBox enableTypeAwareHighlightingCheckBox;
  private JPanel mainPanel;

  public ScalaAdvancedModuleSettings() {
    boolean enableTypeAwareHighlighting = TypeAwareHighlightingApplicationState.getInstance().suggest();
    enableTypeAwareHighlightingCheckBox.setSelected(enableTypeAwareHighlighting);
  }

  public boolean isTypeAwareHighlightingEnabled() {
    return enableTypeAwareHighlightingCheckBox.isSelected();
  }
  
  public JComponent getComponent() {
    return mainPanel;
  }
}
