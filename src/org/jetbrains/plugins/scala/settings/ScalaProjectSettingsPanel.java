package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.settings.uiControls.DependencyAwareInjectionSettings;
import org.jetbrains.plugins.scala.settings.uiControls.ScalaUiWithDependency;

import javax.swing.*;
import java.awt.*;

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.07.2008
 */
public class ScalaProjectSettingsPanel {
  public final static String INJECTION_SETTINGS_NAME = "DependencyAwareInjectionSettings";
  
  private JPanel myPanel;
  private JCheckBox searchAllSymbolsIncludeCheckBox;
  private JCheckBox enableConversionOnCopyCheckBox;
  private JCheckBox donTShowDialogCheckBox;
  private JCheckBox showImplicitConversionsInCheckBox;
  private JCheckBox myResolveToAllClassesCheckBox;
  private JCheckBox showArgumentsToByNameParametersCheckBox;
  private JCheckBox includeBlockExpressionsExpressionsCheckBox;
  private JCheckBox includeLiteralsCheckBox;
  private JCheckBox treatDocCommentAsBlockComment;
  private JCheckBox myDisableLanguageInjection;
  private JCheckBox useScalaClassesPriorityCheckBox;
  private JComboBox collectionHighlightingChooser;
  private JPanel injectionJPanel;
  private JSpinner outputSpinner;
  private JSpinner implicitParametersSearchDepthSpinner;
  private JCheckBox myDontCacheCompound;
  private JCheckBox runWorksheetInTheCheckBox;
  private JCheckBox worksheetInteractiveModeCheckBox;
  private ScalaUiWithDependency.ComponentWithSettings injectionPrefixTable;
  private Project myProject;

  public ScalaProjectSettingsPanel(Project project) {
    myProject = project;
    outputSpinner.setModel(new SpinnerNumberModel(35, 1, null, 1));

    ScalaUiWithDependency[] deps = DependencyAwareInjectionSettings.EP_NAME.getExtensions();
    for (ScalaUiWithDependency uiWithDependency : deps) {
      if (INJECTION_SETTINGS_NAME.equals(uiWithDependency.getName())) {
        injectionPrefixTable = uiWithDependency.createComponent(injectionJPanel);
        break;
      }
    }
    if (injectionPrefixTable == null) injectionPrefixTable = new ScalaUiWithDependency.NullComponentWithSettings();

    setSettings();
  }

  @NotNull
  protected FileType getFileType() {
    return ScalaFileType.SCALA_FILE_TYPE;
  }

  public void apply() {
    if (!isModified()) return;

    final ScalaProjectSettings scalaProjectSettings = ScalaProjectSettings.getInstance(myProject);
    scalaProjectSettings.setImplicitParametersSearchDepth((Integer) implicitParametersSearchDepthSpinner.getValue());
    scalaProjectSettings.setOutputLimit((Integer) outputSpinner.getValue());
    scalaProjectSettings.setInProcessMode(runWorksheetInTheCheckBox.isSelected());
    scalaProjectSettings.setInteractiveMode(worksheetInteractiveModeCheckBox.isSelected());

    scalaProjectSettings.setSearchAllSymbols(searchAllSymbolsIncludeCheckBox.isSelected());
    scalaProjectSettings.setEnableJavaToScalaConversion(enableConversionOnCopyCheckBox.isSelected());
    scalaProjectSettings.setDontShowConversionDialog(donTShowDialogCheckBox.isSelected());
    scalaProjectSettings.setTreatDocCommentAsBlockComment(treatDocCommentAsBlockComment.isSelected());

    scalaProjectSettings.setShowImplisitConversions(showImplicitConversionsInCheckBox.isSelected());
    scalaProjectSettings.setShowArgumentsToByNameParams(showArgumentsToByNameParametersCheckBox.isSelected());
    scalaProjectSettings.setIncludeBlockExpressions(includeBlockExpressionsExpressionsCheckBox.isSelected());
    scalaProjectSettings.setIncludeLiterals(includeLiteralsCheckBox.isSelected());

    scalaProjectSettings.setIgnorePerformance(myResolveToAllClassesCheckBox.isSelected());
    scalaProjectSettings.setDisableLangInjection(myDisableLanguageInjection.isSelected());
    scalaProjectSettings.setDontCacheCompoundTypes(myDontCacheCompound.isSelected());
    scalaProjectSettings.setScalaPriority(useScalaClassesPriorityCheckBox.isSelected());
    scalaProjectSettings.setCollectionTypeHighlightingLevel(collectionHighlightingChooser.getSelectedIndex());
    injectionPrefixTable.saveSettings(scalaProjectSettings);
  }

  @SuppressWarnings({"ConstantConditions", "RedundantIfStatement"})
  public boolean isModified() {

    final ScalaProjectSettings scalaProjectSettings = ScalaProjectSettings.getInstance(myProject);
    
    if (scalaProjectSettings.isShowImplisitConversions() !=
        showImplicitConversionsInCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isShowArgumentsToByNameParams() !=
        showArgumentsToByNameParametersCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isIncludeBlockExpressions() !=
        includeBlockExpressionsExpressionsCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isIncludeLiterals() !=
        includeLiteralsCheckBox.isSelected()) return true;

    if (scalaProjectSettings.getImplicitParametersSearchDepth() !=
        (Integer) implicitParametersSearchDepthSpinner.getValue()) return true;
    if (scalaProjectSettings.getOutputLimit() !=
        (Integer) outputSpinner.getValue()) return true;
    if (scalaProjectSettings.isInProcessMode() != 
        runWorksheetInTheCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isInteractiveMode() != worksheetInteractiveModeCheckBox.isSelected()) return true;

    if (scalaProjectSettings.isSearchAllSymbols() !=
        searchAllSymbolsIncludeCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isEnableJavaToScalaConversion() !=
        enableConversionOnCopyCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isDontShowConversionDialog() !=
        donTShowDialogCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isTreatDocCommentAsBlockComment() !=
        treatDocCommentAsBlockComment.isSelected()) return true;

    if (scalaProjectSettings.isIgnorePerformance() != myResolveToAllClassesCheckBox.isSelected())
      return true;

    if (scalaProjectSettings.isDisableLangInjection() != myDisableLanguageInjection.isSelected())
      return true;


    if (scalaProjectSettings.isDontCacheCompoundTypes() != myDontCacheCompound.isSelected()) return true;

    if (scalaProjectSettings.isScalaPriority() != useScalaClassesPriorityCheckBox.isSelected())
      return true;

    if (scalaProjectSettings.getCollectionTypeHighlightingLevel() !=
        collectionHighlightingChooser.getSelectedIndex()) return true;
    
    if (injectionPrefixTable.isModified(scalaProjectSettings)) return true;

    return false;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void resetImpl() {
    setSettings();
  }

  private void setSettings() {
    final ScalaProjectSettings scalaProjectSettings = ScalaProjectSettings.getInstance(myProject);
    setValue(implicitParametersSearchDepthSpinner, scalaProjectSettings.getImplicitParametersSearchDepth());
    setValue(outputSpinner, scalaProjectSettings.getOutputLimit());
    setValue(runWorksheetInTheCheckBox, scalaProjectSettings.isInProcessMode());
    setValue(worksheetInteractiveModeCheckBox, scalaProjectSettings.isInteractiveMode());

    setValue(searchAllSymbolsIncludeCheckBox, scalaProjectSettings.isSearchAllSymbols());
    setValue(enableConversionOnCopyCheckBox, scalaProjectSettings.isEnableJavaToScalaConversion());
    setValue(donTShowDialogCheckBox, scalaProjectSettings.isDontShowConversionDialog());
    setValue(treatDocCommentAsBlockComment, scalaProjectSettings.isTreatDocCommentAsBlockComment());

    setValue(showImplicitConversionsInCheckBox, scalaProjectSettings.isShowImplisitConversions());
    setValue(showArgumentsToByNameParametersCheckBox, scalaProjectSettings.isShowArgumentsToByNameParams());
    setValue(includeBlockExpressionsExpressionsCheckBox, scalaProjectSettings.isIncludeBlockExpressions());
    setValue(includeLiteralsCheckBox, scalaProjectSettings.isIncludeLiterals());

    setValue(myResolveToAllClassesCheckBox, scalaProjectSettings.isIgnorePerformance());

    setValue(myDisableLanguageInjection, scalaProjectSettings.isDisableLangInjection());
    setValue(myDontCacheCompound, scalaProjectSettings.isDontCacheCompoundTypes());
    setValue(useScalaClassesPriorityCheckBox, scalaProjectSettings.isScalaPriority());
    collectionHighlightingChooser.setSelectedIndex(scalaProjectSettings.getCollectionTypeHighlightingLevel());
    
    injectionPrefixTable.loadSettings(scalaProjectSettings);
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
    injectionJPanel = new JPanel(new GridLayout(2, 1));
    injectionJPanel.setPreferredSize(new Dimension(200, 500));
    injectionJPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 0));
  }
}
