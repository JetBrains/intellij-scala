package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.settings.uiControls.DependencyAwareInjectionSettings;
import org.jetbrains.plugins.scala.settings.uiControls.ScalaUiWithDependency;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.07.2008
 */
public class ScalaProjectSettingsPanel {
  public final static String INJECTION_SETTINGS_NAME = "DependencyAwareInjectionSettings";
  
  private JPanel myPanel;
  private JSpinner classCountSpinner;
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
  private JCheckBox myDisablei18n;
  private JCheckBox useScalaClassesPriorityCheckBox;
  private JComboBox collectionHighlightingChooser;
  private JCheckBox importTheShortestPathCheckBox;
  private JPanel myImportsWithPrefixPanel;
  private JSpinner shiftSpinner;
  private JPanel injectionJPanel;
  private JSpinner outputSpinner;
  private JSpinner implicitParametersSearchDepthSpinner;
  private ScalaUiWithDependency.ComponentWithSettings injectionPrefixTable;
  private JBList referencesWithPrefixList;
  private DefaultListModel myReferencesWithPrefixModel;
  private Project myProject;

  public ScalaProjectSettingsPanel(Project project) {
    myProject = project;
    classCountSpinner.setModel(new SpinnerNumberModel(1, 1, null, 1));
    shiftSpinner.setModel(new SpinnerNumberModel(80, 40, null, 10));
    outputSpinner.setModel(new SpinnerNumberModel(35, 1, null, 1));
    referencesWithPrefixList = new JBList();
    JPanel panel = ScalaProjectSettingsUtil.getPatternListPanel(myPanel, referencesWithPrefixList,
        "Add pattern to use appropriate classes only with prefix", "Use References With Prefix:");
    myImportsWithPrefixPanel.add(panel, BorderLayout.CENTER);
    
    ScalaUiWithDependency[] deps = DependencyAwareInjectionSettings.EP_NAME.getExtensions();
    for (ScalaUiWithDependency uiWithDependency : deps) {
      if (INJECTION_SETTINGS_NAME.equals(uiWithDependency.getName())) {
        injectionPrefixTable = uiWithDependency.createComponent(injectionJPanel);
        break;
      }
    }
    if (injectionPrefixTable == null) injectionPrefixTable = new ScalaUiWithDependency.NullComponentWithSettings();

    referencesWithPrefixList.getEmptyText().setText(ApplicationBundle.message("exclude.from.imports.no.exclusions"));
    setSettings();
  }

  public String[] getPrefixPackages() {
    String[] prefixPackages = new String[myReferencesWithPrefixModel.size()];
    for (int i = 0; i < myReferencesWithPrefixModel.size(); i++) {
      prefixPackages[i] = (String)myReferencesWithPrefixModel.elementAt(i);
    }
    Arrays.sort(prefixPackages);
    return prefixPackages;
  }

  @NotNull
  protected FileType getFileType() {
    return ScalaFileType.SCALA_FILE_TYPE;
  }

  public void apply() {
    if (!isModified()) return;

    final ScalaProjectSettings scalaProjectSettings = ScalaProjectSettings.getInstance(myProject);
    
    scalaProjectSettings.setAddImportMostCloseToReference(addImportStatementInCheckBox.isSelected());
    scalaProjectSettings.setAddFullQualifiedImports(addFullQualifiedImportsCheckBox.isSelected());
    scalaProjectSettings.setSortImports(sortImportsCheckBox.isSelected());
    scalaProjectSettings.setImplicitParametersSearchDepth((Integer) implicitParametersSearchDepthSpinner.getValue());
    scalaProjectSettings.setClassCountToUseImportOnDemand((Integer) classCountSpinner.getValue());
    scalaProjectSettings.setShift((Integer) shiftSpinner.getValue());
    scalaProjectSettings.setOutputLimit((Integer) outputSpinner.getValue());
    scalaProjectSettings.setImportMembersUsingUnderScore(importMembersUsingUnderscoreCheckBox.isSelected());

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
    scalaProjectSettings.setDisableI18N(myDisablei18n.isSelected());
    scalaProjectSettings.setScalaPriority(useScalaClassesPriorityCheckBox.isSelected());
    scalaProjectSettings.setCollectionTypeHighlightingLevel(collectionHighlightingChooser.getSelectedIndex());
    scalaProjectSettings.setImportShortestPathForAmbiguousReferences(importTheShortestPathCheckBox.isSelected());
    scalaProjectSettings.setImportsWithPrefix(getPrefixPackages());
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
    if (scalaProjectSettings.getClassCountToUseImportOnDemand() !=
        (Integer) classCountSpinner.getValue()) return true;
    if (scalaProjectSettings.getShift() !=
        (Integer) shiftSpinner.getValue()) return true;
    if (scalaProjectSettings.getOutputLimit() !=
        (Integer) outputSpinner.getValue()) return true;
    if (scalaProjectSettings.isAddImportMostCloseToReference() !=
        addImportStatementInCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isAddFullQualifiedImports() !=
        addFullQualifiedImportsCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isSortImports() !=
        sortImportsCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isImportMembersUsingUnderScore() !=
        importMembersUsingUnderscoreCheckBox.isSelected()) return true;

    if (scalaProjectSettings.isSearchAllSymbols() !=
        searchAllSymbolsIncludeCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isEnableJavaToScalaConversion() !=
        enableConversionOnCopyCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isDontShowConversionDialog() !=
        donTShowDialogCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isTreatDocCommentAsBlockComment() !=
        treatDocCommentAsBlockComment.isSelected()) return true;
    if (scalaProjectSettings.isImportShortestPathForAmbiguousReferences() !=
        importTheShortestPathCheckBox.isSelected()) return true;

    if (scalaProjectSettings.isIgnorePerformance() != myResolveToAllClassesCheckBox.isSelected())
      return true;

    if (scalaProjectSettings.isDisableLangInjection() != myDisableLanguageInjection.isSelected())
      return true;

    if (scalaProjectSettings.isDisableI18N() != myDisablei18n.isSelected())
      return true;

    if (scalaProjectSettings.isScalaPriority() != useScalaClassesPriorityCheckBox.isSelected())
      return true;

    if (scalaProjectSettings.getCollectionTypeHighlightingLevel() !=
        collectionHighlightingChooser.getSelectedIndex()) return true;

    if (!Arrays.deepEquals(scalaProjectSettings.getImportsWithPrefix(), getPrefixPackages())) return true;
    
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
    
    setValue(addImportStatementInCheckBox, scalaProjectSettings.isAddImportMostCloseToReference());
    setValue(addFullQualifiedImportsCheckBox, scalaProjectSettings.isAddFullQualifiedImports());
    setValue(sortImportsCheckBox, scalaProjectSettings.isSortImports());
    setValue(implicitParametersSearchDepthSpinner, scalaProjectSettings.getImplicitParametersSearchDepth());
    setValue(classCountSpinner, scalaProjectSettings.getClassCountToUseImportOnDemand());
    setValue(shiftSpinner, scalaProjectSettings.getShift());
    setValue(outputSpinner, scalaProjectSettings.getOutputLimit());
    setValue(importMembersUsingUnderscoreCheckBox, scalaProjectSettings.isImportMembersUsingUnderScore());

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
    setValue(myDisablei18n, scalaProjectSettings.isDisableI18N());
    setValue(useScalaClassesPriorityCheckBox, scalaProjectSettings.isScalaPriority());
    setValue(importTheShortestPathCheckBox, scalaProjectSettings.isImportShortestPathForAmbiguousReferences());
    collectionHighlightingChooser.setSelectedIndex(scalaProjectSettings.getCollectionTypeHighlightingLevel()); 
    
    myReferencesWithPrefixModel = new DefaultListModel();
    for (String aPackage : scalaProjectSettings.getImportsWithPrefix()) {
      myReferencesWithPrefixModel.add(myReferencesWithPrefixModel.size(), aPackage);
    }
    referencesWithPrefixList.setModel(myReferencesWithPrefixModel);
    
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
