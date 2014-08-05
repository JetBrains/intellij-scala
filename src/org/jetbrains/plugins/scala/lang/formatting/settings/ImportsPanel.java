package org.jetbrains.plugins.scala.lang.formatting.settings;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighter;
import org.jetbrains.plugins.scala.settings.ScalaProjectSettingsUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * @author Alefas
 * @since 21/05/14.
 */
public class ImportsPanel extends CodeStyleAbstractPanel {
  private JPanel contentPanel;
  private JCheckBox addImportStatementInCheckBox;
  private JCheckBox addFullQualifiedImportsCheckBox;
  private JCheckBox importMembersUsingUnderscoreCheckBox;
  private JCheckBox sortImportsCheckBox;
  private JCheckBox importTheShortestPathCheckBox;
  private JPanel myImportsWithPrefixPanel;
  private JCheckBox collectImportsWithTheCheckBox;
  private JSpinner classCountSpinner;
  private JPanel importLayoutPanel;
  private JBList referencesWithPrefixList;
  private DefaultListModel myReferencesWithPrefixModel;
  private JBList importLayoutTable;
  private DefaultListModel myImportLayoutModel;

  public ImportsPanel(@NotNull CodeStyleSettings settings) {
    super(settings);
    classCountSpinner.setModel(new SpinnerNumberModel(1, 1, null, 1));
    referencesWithPrefixList = new JBList();
    myReferencesWithPrefixModel = new DefaultListModel();
    referencesWithPrefixList.setModel(myReferencesWithPrefixModel);
    JPanel panel = ScalaProjectSettingsUtil.getPatternListPanel(contentPanel, referencesWithPrefixList,
        "Add pattern to use appropriate classes only with prefix", "Use References With Prefix:");
    myImportsWithPrefixPanel.add(panel, BorderLayout.CENTER);
    referencesWithPrefixList.getEmptyText().setText(ApplicationBundle.message("exclude.from.imports.no.exclusions"));
    myImportLayoutModel = new DefaultListModel();
    importLayoutTable = new JBList(myImportLayoutModel);

    panel = ScalaProjectSettingsUtil.getUnsortedPatternListPanel(contentPanel, importLayoutTable, "Add package name", "Import Layout Manager");
    importLayoutPanel.add(panel, BorderLayout.CENTER);
  }

  @Override
  protected String getTabTitle() {
    return "Imports";
  }

  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Nullable
  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return new ScalaEditorHighlighter(null, null, scheme);
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return ScalaFileType.SCALA_FILE_TYPE;
  }

  @Nullable
  @Override
  protected String getPreviewText() {
    return null;
  }

  public String[] getPrefixPackages() {
    String[] prefixPackages = new String[myReferencesWithPrefixModel.size()];
    for (int i = 0; i < myReferencesWithPrefixModel.size(); i++) {
      prefixPackages[i] = (String)myReferencesWithPrefixModel.elementAt(i);
    }
    Arrays.sort(prefixPackages);
    return prefixPackages;
  }

  public String[] getImportLayout() {
    String[] importLayout = new String[myImportLayoutModel.size()];
    for (int i = 0; i < myImportLayoutModel.size(); i++) {
      importLayout[i] = (String)myImportLayoutModel.elementAt(i);
    }
    return importLayout;
  }

  @Override
  public void apply(CodeStyleSettings settings) throws ConfigurationException {
    if (!isModified(settings)) return;

    ScalaCodeStyleSettings scalaCodeStyleSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

    scalaCodeStyleSettings.setAddImportMostCloseToReference(addImportStatementInCheckBox.isSelected());
    scalaCodeStyleSettings.setAddFullQualifiedImports(addFullQualifiedImportsCheckBox.isSelected());
    scalaCodeStyleSettings.setSortImports(sortImportsCheckBox.isSelected());
    scalaCodeStyleSettings.setCollectImports(collectImportsWithTheCheckBox.isSelected());
    scalaCodeStyleSettings.setClassCountToUseImportOnDemand((Integer) classCountSpinner.getValue());
    scalaCodeStyleSettings.setImportMembersUsingUnderScore(importMembersUsingUnderscoreCheckBox.isSelected());
    scalaCodeStyleSettings.setImportShortestPathForAmbiguousReferences(importTheShortestPathCheckBox.isSelected());
    scalaCodeStyleSettings.setImportsWithPrefix(getPrefixPackages());
    scalaCodeStyleSettings.setImportLayout(getImportLayout());
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaCodeStyleSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

    if (scalaCodeStyleSettings.getClassCountToUseImportOnDemand() !=
        (Integer) classCountSpinner.getValue()) return true;
    if (scalaCodeStyleSettings.isAddImportMostCloseToReference() !=
        addImportStatementInCheckBox.isSelected()) return true;
    if (scalaCodeStyleSettings.isAddFullQualifiedImports() !=
        addFullQualifiedImportsCheckBox.isSelected()) return true;
    if (scalaCodeStyleSettings.isSortImports() !=
        sortImportsCheckBox.isSelected()) return true;
    if (scalaCodeStyleSettings.isCollectImports() !=
        collectImportsWithTheCheckBox.isSelected()) return true;
    if (scalaCodeStyleSettings.isImportMembersUsingUnderScore() !=
        importMembersUsingUnderscoreCheckBox.isSelected()) return true;
    if (scalaCodeStyleSettings.isImportShortestPathForAmbiguousReferences() !=
        importTheShortestPathCheckBox.isSelected()) return true;
    if (!Arrays.deepEquals(scalaCodeStyleSettings.getImportsWithPrefix(), getPrefixPackages())) return true;
    if (!Arrays.deepEquals(scalaCodeStyleSettings.getImportLayout(), getImportLayout())) return true;
    return false;
  }

  @Nullable
  @Override
  public JComponent getPanel() {
    return contentPanel;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    ScalaCodeStyleSettings scalaCodeStyleSettings = settings.getCustomSettings(ScalaCodeStyleSettings.class);

    setValue(addImportStatementInCheckBox, scalaCodeStyleSettings.isAddImportMostCloseToReference());
    setValue(addFullQualifiedImportsCheckBox, scalaCodeStyleSettings.isAddFullQualifiedImports());
    setValue(sortImportsCheckBox, scalaCodeStyleSettings.isSortImports());
    setValue(collectImportsWithTheCheckBox, scalaCodeStyleSettings.isCollectImports());
    setValue(classCountSpinner, scalaCodeStyleSettings.getClassCountToUseImportOnDemand());
    setValue(importMembersUsingUnderscoreCheckBox, scalaCodeStyleSettings.isImportMembersUsingUnderScore());
    setValue(importTheShortestPathCheckBox, scalaCodeStyleSettings.isImportShortestPathForAmbiguousReferences());

    myReferencesWithPrefixModel.clear();
    for (String aPackage : scalaCodeStyleSettings.getImportsWithPrefix()) {
      myReferencesWithPrefixModel.add(myReferencesWithPrefixModel.size(), aPackage);
    }
    myImportLayoutModel.clear();
    for (String layoutElement : scalaCodeStyleSettings.getImportLayout()) {
      myImportLayoutModel.add(myImportLayoutModel.size(), layoutElement);
    }
  }

  private static void setValue(JSpinner spinner, int value) {
    spinner.setValue(value);
  }

  private static void setValue(final JCheckBox box, final boolean value) {
    box.setSelected(value);
  }
}
