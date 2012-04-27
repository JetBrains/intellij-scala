package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
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
  private JCheckBox showImplicitConversionsInCheckBox;
  private JCheckBox myResolveToAllClassesCheckBox;
  private JCheckBox showArgumentsToByNameParametersCheckBox;
  private JCheckBox includeBlockExpressionsExpressionsCheckBox;
  private JCheckBox includeLiteralsCheckBox;
  private JCheckBox treatDocCommentAsBlockComment;
  private JCheckBox importMembersUsingUnderscoreCheckBox;
  private JCheckBox myDisableLanguageInjection;
  private JCheckBox useScalaClassesPriorityCheckBox;
  private JComboBox collectionHighlightingChooser;
  private Project myProject;

  public ScalaProjectSettingsPanel(Project project) {
    myProject = project;
    classCountSpinner.setModel(new SpinnerNumberModel(1, 1, null, 1));
    setSettings();
  }

  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return new ScalaEditorHighlighter(myProject, null, scheme);
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

    ScalaProjectSettings.getInstance(myProject).setAddUnambigiousImportsOnTheFly(addUnambiguousImportsOnCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setAddImportMostCloseToReference(addImportStatementInCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setAddFullQualifiedImports(addFullQualifiedImportsCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setClassCountToUseImportOnDemand((Integer) classCountSpinner.getValue());
    ScalaProjectSettings.getInstance(myProject).setImportMembersUsingUnderScore(importMembersUsingUnderscoreCheckBox.isSelected());

    ScalaProjectSettings.getInstance(myProject).setSearchAllSymbols(searchAllSymbolsIncludeCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setEnableJavaToScalaConversion(enableConversionOnCopyCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setDontShowConversionDialog(donTShowDialogCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setTreatDocCommentAsBlockComment(treatDocCommentAsBlockComment.isSelected());

    ScalaProjectSettings.getInstance(myProject).setShowImplisitConversions(showImplicitConversionsInCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setShowArgumentsToByNameParams(showArgumentsToByNameParametersCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setIncludeBlockExpressions(includeBlockExpressionsExpressionsCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setIncludeLiterals(includeLiteralsCheckBox.isSelected());

    ScalaProjectSettings.getInstance(myProject).setIgnorePerformance(myResolveToAllClassesCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setDisableLangInjection(myDisableLanguageInjection.isSelected());
    ScalaProjectSettings.getInstance(myProject).setScalaPriority(useScalaClassesPriorityCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setCollectionTypeHighlightingLevel(collectionHighlightingChooser.getSelectedIndex());
  }

  @SuppressWarnings({"ConstantConditions", "RedundantIfStatement"})
  public boolean isModified() {

    if (ScalaProjectSettings.getInstance(myProject).isShowImplisitConversions() !=
        showImplicitConversionsInCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance(myProject).isShowArgumentsToByNameParams() !=
        showArgumentsToByNameParametersCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance(myProject).isIncludeBlockExpressions() !=
        includeBlockExpressionsExpressionsCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance(myProject).isIncludeLiterals() !=
        includeLiteralsCheckBox.isSelected()) return true;

    if (ScalaProjectSettings.getInstance(myProject).getClassCountToUseImportOnDemand() !=
        (Integer) classCountSpinner.getValue()) return true;
    if (ScalaProjectSettings.getInstance(myProject).isAddUnambigiousImportsOnTheFly() !=
        addUnambiguousImportsOnCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance(myProject).isAddImportMostCloseToReference() !=
        addImportStatementInCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance(myProject).isAddFullQualifiedImports() !=
        addFullQualifiedImportsCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance(myProject).isImportMembersUsingUnderScore() !=
        importMembersUsingUnderscoreCheckBox.isSelected()) return true;

    if (ScalaProjectSettings.getInstance(myProject).isSearchAllSymbols() !=
        searchAllSymbolsIncludeCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance(myProject).isEnableJavaToScalaConversion() !=
        enableConversionOnCopyCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance(myProject).isDontShowConversionDialog() !=
        donTShowDialogCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance(myProject).isTreatDocCommentAsBlockComment() !=
        treatDocCommentAsBlockComment.isSelected()) return true;

    if (ScalaProjectSettings.getInstance(myProject).isIgnorePerformance() != myResolveToAllClassesCheckBox.isSelected())
      return true;

    if (ScalaProjectSettings.getInstance(myProject).isDisableLangInjection() != myDisableLanguageInjection.isSelected())
      return true;

    if (ScalaProjectSettings.getInstance(myProject).isScalaPriority() != useScalaClassesPriorityCheckBox.isSelected())
      return true;

    if (ScalaProjectSettings.getInstance(myProject).getCollectionTypeHighlightingLevel() !=
        collectionHighlightingChooser.getSelectedIndex()) return true;

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
        ScalaProjectSettings.getInstance(myProject).isAddUnambigiousImportsOnTheFly());
    setValue(addImportStatementInCheckBox,
        ScalaProjectSettings.getInstance(myProject).isAddImportMostCloseToReference());
    setValue(addFullQualifiedImportsCheckBox,
        ScalaProjectSettings.getInstance(myProject).isAddFullQualifiedImports());
    setValue(classCountSpinner,
        ScalaProjectSettings.getInstance(myProject).getClassCountToUseImportOnDemand());
    setValue(importMembersUsingUnderscoreCheckBox,
        ScalaProjectSettings.getInstance(myProject).isImportMembersUsingUnderScore());

    setValue(searchAllSymbolsIncludeCheckBox,
        ScalaProjectSettings.getInstance(myProject).isSearchAllSymbols());
    setValue(enableConversionOnCopyCheckBox,
        ScalaProjectSettings.getInstance(myProject).isEnableJavaToScalaConversion());
    setValue(donTShowDialogCheckBox,
        ScalaProjectSettings.getInstance(myProject).isDontShowConversionDialog());
    setValue(treatDocCommentAsBlockComment,
        ScalaProjectSettings.getInstance(myProject).isTreatDocCommentAsBlockComment());

    setValue(showImplicitConversionsInCheckBox,
        ScalaProjectSettings.getInstance(myProject).isShowImplisitConversions());
    setValue(showArgumentsToByNameParametersCheckBox,
        ScalaProjectSettings.getInstance(myProject).isShowArgumentsToByNameParams());
    setValue(includeBlockExpressionsExpressionsCheckBox,
        ScalaProjectSettings.getInstance(myProject).isIncludeBlockExpressions());
    setValue(includeLiteralsCheckBox,
        ScalaProjectSettings.getInstance(myProject).isIncludeLiterals());

    setValue(myResolveToAllClassesCheckBox,
        ScalaProjectSettings.getInstance(myProject).isIgnorePerformance());

    setValue(myDisableLanguageInjection,
        ScalaProjectSettings.getInstance(myProject).isDisableLangInjection());
    setValue(useScalaClassesPriorityCheckBox,
        ScalaProjectSettings.getInstance(myProject).isScalaPriority());
    collectionHighlightingChooser.setSelectedIndex(ScalaProjectSettings.getInstance(myProject).getCollectionTypeHighlightingLevel());
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
}
