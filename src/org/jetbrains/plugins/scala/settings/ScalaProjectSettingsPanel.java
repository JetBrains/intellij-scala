package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.highlighter.ScalaEditorHighlighter;

import javax.swing.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.07.2008
 */
public class ScalaProjectSettingsPanel {
  private JPanel myPanel;
  private JTabbedPane tabbedPane;
  private JSpinner classCountSpinner;
  private JCheckBox addUnambiguousImportsOnCheckBox;
  private JCheckBox addImportStatementInCheckBox;
  private JCheckBox searchAllSymbolsIncludeCheckBox;
  private JCheckBox enableConversionOnCopyCheckBox;
  private JCheckBox donTShowDialogCheckBox;
  private JCheckBox addFullQualifiedImportsCheckBox;
  private JCheckBox sortImportsCheckBox;
  private JCheckBox showImplicitConversionsInCheckBox;
  private JCheckBox myResolveToAllClassesCheckBox;
  private JCheckBox showArgumentsToByNameParametersCheckBox;
  private JCheckBox includeBlockExpressionsExpressionsCheckBox;
  private JCheckBox includeLiteralsCheckBox;
  private JCheckBox treatDocCommentAsBlockComment;
  private JCheckBox importMembersUsingUnderscoreCheckBox;
  private JCheckBox myDisableLanguageInjection;
  private JCheckBox useScalaClassesPriorityCheckBox;

  public ScalaProjectSettingsPanel() {
    classCountSpinner.setModel(new SpinnerNumberModel(1, 1, null, 1));
    setSettings();
  }

  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return new ScalaEditorHighlighter(null, null, scheme);
  }

  protected int getRightMargin() {
    return 0;
  }

  protected void prepareForReformat(PsiFile psiFile) {
  }

  @NotNull
  protected FileType getFileType() {
    return ScalaFileType.SCALA_FILE_TYPE;
  }

  protected String getPreviewText() {
    return "";
  }

  public void apply() {
    if (!isModified()) return;

    ScalaProjectSettings.getInstance().setAddUnambigiousImportsOnTheFly(addUnambiguousImportsOnCheckBox.isSelected());
    ScalaProjectSettings.getInstance().setAddImportMostCloseToReference(addImportStatementInCheckBox.isSelected());
    ScalaProjectSettings.getInstance().setAddFullQualifiedImports(addFullQualifiedImportsCheckBox.isSelected());
    ScalaProjectSettings.getInstance().setSortImports(sortImportsCheckBox.isSelected());
    ScalaProjectSettings.getInstance().setClassCountToUseImportOnDemand((Integer) classCountSpinner.getValue());
    ScalaProjectSettings.getInstance().setImportMembersUsingUnderScore(importMembersUsingUnderscoreCheckBox.isSelected());

    ScalaProjectSettings.getInstance().setSearchAllSymbols(searchAllSymbolsIncludeCheckBox.isSelected());
    ScalaProjectSettings.getInstance().setEnableJavaToScalaConversion(enableConversionOnCopyCheckBox.isSelected());
    ScalaProjectSettings.getInstance().setDontShowConversionDialog(donTShowDialogCheckBox.isSelected());
    ScalaProjectSettings.getInstance().setTreatDocCommentAsBlockComment(treatDocCommentAsBlockComment.isSelected());

    ScalaProjectSettings.getInstance().setShowImplisitConversions(showImplicitConversionsInCheckBox.isSelected());
    ScalaProjectSettings.getInstance().setShowArgumentsToByNameParams(showArgumentsToByNameParametersCheckBox.isSelected());
    ScalaProjectSettings.getInstance().setIncludeBlockExpressions(includeBlockExpressionsExpressionsCheckBox.isSelected());
    ScalaProjectSettings.getInstance().setIncludeLiterals(includeLiteralsCheckBox.isSelected());

    ScalaProjectSettings.getInstance().setIgnorePerformance(myResolveToAllClassesCheckBox.isSelected());
    ScalaProjectSettings.getInstance().setDisableLangInjection(myDisableLanguageInjection.isSelected());
    ScalaProjectSettings.getInstance().setScalaPriority(useScalaClassesPriorityCheckBox.isSelected());
  }

  @SuppressWarnings({"ConstantConditions", "RedundantIfStatement"})
  public boolean isModified() {

    if (ScalaProjectSettings.getInstance().isShowImplisitConversions() !=
        showImplicitConversionsInCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance().isShowArgumentsToByNameParams() !=
        showArgumentsToByNameParametersCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance().isIncludeBlockExpressions() !=
        includeBlockExpressionsExpressionsCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance().isIncludeLiterals() !=
        includeLiteralsCheckBox.isSelected()) return true;

    if (ScalaProjectSettings.getInstance().getClassCountToUseImportOnDemand() !=
        (Integer) classCountSpinner.getValue()) return true;
    if (ScalaProjectSettings.getInstance().isAddUnambigiousImportsOnTheFly() !=
        addUnambiguousImportsOnCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance().isAddImportMostCloseToReference() !=
        addImportStatementInCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance().isAddFullQualifiedImports() !=
        addFullQualifiedImportsCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance().isSortImports() !=
        sortImportsCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance().isImportMembersUsingUnderScore() !=
        importMembersUsingUnderscoreCheckBox.isSelected()) return true;

    if (ScalaProjectSettings.getInstance().isSearchAllSymbols() !=
        searchAllSymbolsIncludeCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance().isEnableJavaToScalaConversion() !=
        enableConversionOnCopyCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance().isDontShowConversionDialog() !=
        donTShowDialogCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance().isTreatDocCommentAsBlockComment() !=
        treatDocCommentAsBlockComment.isSelected()) return true;

    if (ScalaProjectSettings.getInstance().isIgnorePerformance() != myResolveToAllClassesCheckBox.isSelected())
      return true;

    if (ScalaProjectSettings.getInstance().isDisableLangInjection() != myDisableLanguageInjection.isSelected())
      return true;

    if (ScalaProjectSettings.getInstance().isScalaPriority() != useScalaClassesPriorityCheckBox.isSelected())
      return true;

    return false;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void resetImpl() {
    setSettings();
  }

  private void setSettings() {
    setValue(addUnambiguousImportsOnCheckBox,
        ScalaProjectSettings.getInstance().isAddUnambigiousImportsOnTheFly());
    setValue(addImportStatementInCheckBox,
        ScalaProjectSettings.getInstance().isAddImportMostCloseToReference());
    setValue(addFullQualifiedImportsCheckBox,
        ScalaProjectSettings.getInstance().isAddFullQualifiedImports());
    setValue(sortImportsCheckBox,
        ScalaProjectSettings.getInstance().isSortImports());
    setValue(classCountSpinner,
        ScalaProjectSettings.getInstance().getClassCountToUseImportOnDemand());
    setValue(importMembersUsingUnderscoreCheckBox,
        ScalaProjectSettings.getInstance().isImportMembersUsingUnderScore());

    setValue(searchAllSymbolsIncludeCheckBox,
        ScalaProjectSettings.getInstance().isSearchAllSymbols());
    setValue(enableConversionOnCopyCheckBox,
        ScalaProjectSettings.getInstance().isEnableJavaToScalaConversion());
    setValue(donTShowDialogCheckBox,
        ScalaProjectSettings.getInstance().isDontShowConversionDialog());
    setValue(treatDocCommentAsBlockComment,
        ScalaProjectSettings.getInstance().isTreatDocCommentAsBlockComment());

    setValue(showImplicitConversionsInCheckBox,
        ScalaProjectSettings.getInstance().isShowImplisitConversions());
    setValue(showArgumentsToByNameParametersCheckBox,
        ScalaProjectSettings.getInstance().isShowArgumentsToByNameParams());
    setValue(includeBlockExpressionsExpressionsCheckBox,
        ScalaProjectSettings.getInstance().isIncludeBlockExpressions());
    setValue(includeLiteralsCheckBox,
        ScalaProjectSettings.getInstance().isIncludeLiterals());

    setValue(myResolveToAllClassesCheckBox,
        ScalaProjectSettings.getInstance().isIgnorePerformance());

    setValue(myDisableLanguageInjection,
        ScalaProjectSettings.getInstance().isDisableLangInjection());
    setValue(useScalaClassesPriorityCheckBox,
        ScalaProjectSettings.getInstance().isScalaPriority());
  }

  private static void setValue(JSpinner spinner, int value) {
    spinner.setValue(value);
  }

  private static void setValue(final JCheckBox box, final boolean value) {
    box.setSelected(value);
  }

  private static void setValue(final JComboBox box, final int value) {
    box.setSelectedIndex(value);
  }

  private void createUIComponents() {
    // TODO: place custom component creation code here
  }
}
