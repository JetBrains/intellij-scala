package org.jetbrains.plugins.scala.debugger.filters;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.icons.Icons;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author ilyas
 */
public class ScalaDebuggerSettingsConfigurable implements Configurable {
  private JCheckBox myIgnoreScalaMethods;
  private JPanel myPanel;
  private boolean isModified = false;
  private final ScalaDebuggerSettings mySettings;

  public ScalaDebuggerSettingsConfigurable(final ScalaDebuggerSettings settings) {
    mySettings = settings;
    final Boolean flag = settings.DEBUG_DISABLE_SPECIFIC_SCALA_METHODS;
    myIgnoreScalaMethods.setSelected(flag == null || flag.booleanValue());

    myIgnoreScalaMethods.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        isModified = mySettings.DEBUG_DISABLE_SPECIFIC_SCALA_METHODS.booleanValue() != myIgnoreScalaMethods.isSelected();
      }
    });
  }

  @Nls
  public String getDisplayName() {
    return ScalaBundle.message("scala.debug.caption");
  }

  public Icon getIcon() {
    return Icons.SCALA_SMALL_LOGO;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return isModified;
  }

  public void apply() throws ConfigurationException {
    if (isModified) {
      mySettings.DEBUG_DISABLE_SPECIFIC_SCALA_METHODS = myIgnoreScalaMethods.isSelected();
    }
    isModified = false;
  }

  public void reset() {
    final Boolean flag = mySettings.DEBUG_DISABLE_SPECIFIC_SCALA_METHODS;
    myIgnoreScalaMethods.setSelected(flag == null || flag.booleanValue());
  }

  public void disposeUIResources() {
  }}
