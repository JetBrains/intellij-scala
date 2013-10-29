package org.jetbrains.plugins.scala.configuration;

import javax.swing.*;
import java.awt.*;

/**
 * @author Pavel Fatin
 */
public class ScalaLibraryEditorForm {
  private JPanel myContentPanel;
  private JPanel myRootPanel;
  private JComboBox myLanguageLevel;

  public ScalaLibraryEditorForm(JComponent content) {
    myContentPanel.add(content, BorderLayout.CENTER);
  }

  public JComponent getComponent() {
    return myRootPanel;
  }
}
