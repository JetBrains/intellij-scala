package org.jetbrains.sbt.project.module;

import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * @author Nikolay Obedin
 * @since 7/25/14.
 */
public class SbtModuleResolversForm {
  public JBTable resolversTable;
  public JButton updateButton;
  public JPanel mainPanel;

  public SbtModuleResolversForm() {
    mainPanel.setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS));
  }
}
