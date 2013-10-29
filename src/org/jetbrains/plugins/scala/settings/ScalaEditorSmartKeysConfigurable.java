package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
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
    JPanel panel = new JPanel();
    panel.setBorder(BorderFactory.createTitledBorder(new EmptyBorder(0,0,0,0), "Scala"));
    myChbInsertMultilineQuotes = new JCheckBox(ScalaBundle.message("insert.pair.multiline.quotes"));
    myChbInsertMultilineQuotes.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ScalaApplicationSettings.getInstance().INSERT_MULTILINE_QUOTES = myChbInsertMultilineQuotes.isSelected();
      }
    });
    panel.add(myChbInsertMultilineQuotes);
    return panel;
  }

  @Override
  public void apply() throws ConfigurationException {
    ScalaApplicationSettings.getInstance().INSERT_MULTILINE_QUOTES = myChbInsertMultilineQuotes.isSelected();
  }

  @Override
  public void reset() {
    myChbInsertMultilineQuotes.setSelected(ScalaApplicationSettings.getInstance().INSERT_MULTILINE_QUOTES);
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
