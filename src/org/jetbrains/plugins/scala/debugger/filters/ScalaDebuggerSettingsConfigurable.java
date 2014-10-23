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
  private JLabel startIndexLabel;
  private JSpinner myStartIndexSpinner;
  private JSpinner myEndIndexSpinner;
  private JLabel endIndexLabel;
  private JCheckBox friendlyDisplayOfScalaCheckBox;
  private JCheckBox friendlyDisplayOfObjectRefCheckBox;
  private JCheckBox doNotExpandStreamsCheckBox;
  private JCheckBox dontShowRuntimeRefs = new JCheckBox(); // TODO
  private boolean isModified = false;
  private final ScalaDebuggerSettings mySettings;

  public ScalaDebuggerSettingsConfigurable(final ScalaDebuggerSettings settings) {
    mySettings = settings;
    final Boolean flag = settings.DEBUG_DISABLE_SPECIFIC_SCALA_METHODS;
    myIgnoreScalaMethods.setSelected(flag == null || flag.booleanValue());
    friendlyDisplayOfScalaCheckBox.setSelected(settings.FRIENDLY_COLLECTION_DISPLAY_ENABLED);
    dontShowRuntimeRefs.setSelected(settings.DONT_SHOW_RUNTIME_REFS);
    friendlyDisplayOfScalaCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final boolean collectionsSettingsEnabled = friendlyDisplayOfScalaCheckBox.isSelected();
        myStartIndexSpinner.setEnabled(collectionsSettingsEnabled);
        myEndIndexSpinner.setEnabled(collectionsSettingsEnabled);
        doNotExpandStreamsCheckBox.setEnabled(collectionsSettingsEnabled);
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
    myStartIndexSpinner.setModel(new SpinnerNumberModel(1, 1, null, 1));
    myEndIndexSpinner.setModel(new SpinnerNumberModel(1, 1, null, 1));

    myStartIndexSpinner.setValue(mySettings.COLLECTION_START_INDEX);
    myEndIndexSpinner.setValue(mySettings.COLLECTION_END_INDEX);
    
    return myPanel;
  }

  public boolean isModified() {
    return mySettings.COLLECTION_START_INDEX != myStartIndexSpinner.getValue() || 
        mySettings.COLLECTION_END_INDEX != myEndIndexSpinner.getValue() || 
        mySettings.FRIENDLY_COLLECTION_DISPLAY_ENABLED != friendlyDisplayOfScalaCheckBox.isSelected() ||
        mySettings.DONT_SHOW_RUNTIME_REFS != dontShowRuntimeRefs.isSelected() ||
        mySettings.DEBUG_DISABLE_SPECIFIC_SCALA_METHODS != myIgnoreScalaMethods.isSelected() ||
        mySettings.DO_NOT_DISPLAY_STREAMS != doNotExpandStreamsCheckBox.isSelected();
  }

  public void apply() throws ConfigurationException {
    mySettings.FRIENDLY_COLLECTION_DISPLAY_ENABLED = friendlyDisplayOfScalaCheckBox.isSelected();
    mySettings.DONT_SHOW_RUNTIME_REFS = dontShowRuntimeRefs.isSelected();
    mySettings.DEBUG_DISABLE_SPECIFIC_SCALA_METHODS = myIgnoreScalaMethods.isSelected();
    mySettings.COLLECTION_START_INDEX = (Integer)myStartIndexSpinner.getValue();
    mySettings.COLLECTION_END_INDEX = (Integer)myEndIndexSpinner.getValue();
    mySettings.DO_NOT_DISPLAY_STREAMS = doNotExpandStreamsCheckBox.isSelected();
  }

  public void reset() {
    final Boolean flag = mySettings.DEBUG_DISABLE_SPECIFIC_SCALA_METHODS;
    myIgnoreScalaMethods.setSelected(flag == null || flag.booleanValue());
  }

  public void disposeUIResources() {
  }}
