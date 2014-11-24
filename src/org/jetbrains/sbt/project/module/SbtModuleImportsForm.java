package org.jetbrains.sbt.project.module;

import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * @author Nikolay Obedin
 * @since 11/24/14.
 */
public class SbtModuleImportsForm {
  public JPanel mainPanel;
  public JTextField sbtVersionTextField;
  public JBList sbtImportsList;
  private JLabel sbtVersionLabel;
  private JLabel sbtImportsLabel;

  public SbtModuleImportsForm() {
    mainPanel.setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS));
  }
}
