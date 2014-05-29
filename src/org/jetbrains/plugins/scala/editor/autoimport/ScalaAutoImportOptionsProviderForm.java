package org.jetbrains.plugins.scala.editor.autoimport;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.application.ApplicationBundle;

import javax.swing.*;

/**
 * @author Alefas
 * @since 24.05.12
 */
public class ScalaAutoImportOptionsProviderForm {
  private static final String INSERT_IMPORTS_ALWAYS = ApplicationBundle.message("combobox.insert.imports.all");
  private static final String INSERT_IMPORTS_ASK = ApplicationBundle.message("combobox.insert.imports.ask");
  private static final String INSERT_IMPORTS_NONE = ApplicationBundle.message("combobox.insert.imports.none");

  private JPanel panel1;
  private JComboBox<String> importOnPasteComboBox;
  private JCheckBox addUnambiguousImportsOnCheckBox;
  private JCheckBox optimizeImportsOnTheCheckBox;

  public ScalaAutoImportOptionsProviderForm() {
    importOnPasteComboBox.addItem(INSERT_IMPORTS_ALWAYS);
    importOnPasteComboBox.addItem(INSERT_IMPORTS_ASK);
    importOnPasteComboBox.addItem(INSERT_IMPORTS_NONE);
  }

  public boolean isAddUnambiguous() {
    return addUnambiguousImportsOnCheckBox.isSelected();
  }

  public void setAddUnambiguous(boolean addUnambiguous) {
    addUnambiguousImportsOnCheckBox.setSelected(addUnambiguous);
  }

  public boolean isOptimizeImports() {
    return optimizeImportsOnTheCheckBox.isSelected();
  }

  public void setOptimizeImports(boolean optimizeImports) {
    optimizeImportsOnTheCheckBox.setSelected(optimizeImports);
  }

  public int getImportOnPasteOption() {
    if (importOnPasteComboBox.getSelectedItem().equals(INSERT_IMPORTS_ALWAYS)) {
      return CodeInsightSettings.YES;
    } else if (importOnPasteComboBox.getSelectedItem().equals(INSERT_IMPORTS_ASK)) {
      return CodeInsightSettings.ASK;
    } else {
      return CodeInsightSettings.NO;
    }
  }

  public void setImportOnPasteOption(int importOnPasteOption) {
    switch (importOnPasteOption) {
      case CodeInsightSettings.YES:
        importOnPasteComboBox.setSelectedItem(INSERT_IMPORTS_ALWAYS);
        break;
      case CodeInsightSettings.ASK:
        importOnPasteComboBox.setSelectedItem(INSERT_IMPORTS_ASK);
        break;
      case CodeInsightSettings.NO:
        importOnPasteComboBox.setSelectedItem(INSERT_IMPORTS_NONE);
        break;
    }
  }

  public JComponent getComponent() {
    return panel1;
  }
}
