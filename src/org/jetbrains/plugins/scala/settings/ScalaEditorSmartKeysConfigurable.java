package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Nikolay.Tropin
 * 10/23/13
 */
public class ScalaEditorSmartKeysConfigurable extends BaseConfigurable implements SearchableConfigurable {
  private JCheckBox myChbInsertMultilineQuotes;
  private JCheckBox myChbUpgradeToInterpolated;

  @Nls
  @Override
  public String getDisplayName() {
    return "Scala";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    JPanel panel = new JPanel(new GridLayoutManager(2, 1));
    panel.setBorder(BorderFactory.createTitledBorder(new EmptyBorder(0, 0, 0, 0), "Scala"));
    myChbInsertMultilineQuotes = new JCheckBox(ScalaBundle.message("insert.pair.multiline.quotes"));
    myChbUpgradeToInterpolated = new JCheckBox(ScalaBundle.message("upgrade.to.interpolated"));
    myChbInsertMultilineQuotes.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ScalaApplicationSettings.getInstance().INSERT_MULTILINE_QUOTES = myChbInsertMultilineQuotes.isSelected();
      }
    });
    myChbUpgradeToInterpolated.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ScalaApplicationSettings.getInstance().UPGRADE_TO_INTERPOLATED = myChbUpgradeToInterpolated.isSelected();
      }
    });
    panel.add(myChbInsertMultilineQuotes, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    panel.add(myChbUpgradeToInterpolated, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    return panel;
  }

  @Override
  public void apply() throws ConfigurationException {
    ScalaApplicationSettings.getInstance().INSERT_MULTILINE_QUOTES = myChbInsertMultilineQuotes.isSelected();
    ScalaApplicationSettings.getInstance().UPGRADE_TO_INTERPOLATED = myChbUpgradeToInterpolated.isSelected();
  }

  @Override
  public void reset() {
    myChbInsertMultilineQuotes.setSelected(ScalaApplicationSettings.getInstance().INSERT_MULTILINE_QUOTES);
    myChbUpgradeToInterpolated.setSelected(ScalaApplicationSettings.getInstance().UPGRADE_TO_INTERPOLATED);
  }

  @Override
  public void disposeUIResources() {

  }

  @NotNull
  @Override
  public String getId() {
    return "ScalaSmartKeys";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }
}
