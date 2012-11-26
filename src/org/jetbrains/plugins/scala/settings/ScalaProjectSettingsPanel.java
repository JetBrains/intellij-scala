package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.07.2008
 */
public class ScalaProjectSettingsPanel {
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
  private JCheckBox useScalaClassesPriorityCheckBox;
  private JComboBox collectionHighlightingChooser;
  private JCheckBox importTheShortestPathCheckBox;
  private JPanel myImportsWithPrefixPanel;
  private JSpinner shiftSpinner;
  private JBList referencesWithPrefixList;
  private DefaultListModel myReferencesWithPrefixModel;
  private Project myProject;

  public ScalaProjectSettingsPanel(Project project) {
    myProject = project;
    classCountSpinner.setModel(new SpinnerNumberModel(1, 1, null, 1));
    shiftSpinner.setModel(new SpinnerNumberModel(80, 40, null, 10));
    referencesWithPrefixList = new JBList();
    JPanel panel = ToolbarDecorator.createDecorator(referencesWithPrefixList)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            InputValidator validator = new InputValidator() {

              public boolean checkInput(String inputString) {
                return checkInput(inputString, true);
              }

              private boolean checkInput(String inputString, boolean checkExcludes) {
                if (checkExcludes && inputString.startsWith(ScalaProjectSettings.EXCLUDE_PREFIX)) {
                  return checkInput(inputString.substring(ScalaProjectSettings.EXCLUDE_PREFIX.length()), false);
                }
                return inputString.contains(".") && ScalaProjectSettingsUtil.isValidPackage(inputString);
              }

              public boolean canClose(String inputString) {
                return checkInput(inputString);
              }
            };
            String packageName = Messages.showInputDialog(myPanel,
                "Add pattern to use appropriate classes only with prefix",
                "Use References With Prefix:",
                Messages.getWarningIcon(), "", validator);
            addPrefixPackage(packageName);
          }
        }).disableUpDownActions().createPanel();
    myImportsWithPrefixPanel.add(panel, BorderLayout.CENTER);

    referencesWithPrefixList.getEmptyText().setText(ApplicationBundle.message("exclude.from.imports.no.exclusions"));
    setSettings();
  }

  private void addPrefixPackage(String packageName) {
    if (packageName == null) {
      return;
    }
    int index = -Arrays.binarySearch(myReferencesWithPrefixModel.toArray(), packageName) - 1;
    if (index < 0) return;

    myReferencesWithPrefixModel.add(index, packageName);
    referencesWithPrefixList.setSelectedValue(packageName, true);
    ListScrollingUtil.ensureIndexIsVisible(referencesWithPrefixList, index, 0);
    IdeFocusManager.getGlobalInstance().requestFocus(referencesWithPrefixList, false);
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

    ScalaProjectSettings.getInstance(myProject).setAddImportMostCloseToReference(addImportStatementInCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setAddFullQualifiedImports(addFullQualifiedImportsCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setSortImports(sortImportsCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setClassCountToUseImportOnDemand((Integer) classCountSpinner.getValue());
    ScalaProjectSettings.getInstance(myProject).setShift((Integer) shiftSpinner.getValue());
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
    ScalaProjectSettings.getInstance(myProject).setImportShortestPathForAmbiguousReferences(importTheShortestPathCheckBox.isSelected());
    ScalaProjectSettings.getInstance(myProject).setImportsWithPrefix(getPrefixPackages());
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
    if (ScalaProjectSettings.getInstance(myProject).getShift() !=
        (Integer) shiftSpinner.getValue()) return true;
    if (ScalaProjectSettings.getInstance(myProject).isAddImportMostCloseToReference() !=
        addImportStatementInCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance(myProject).isAddFullQualifiedImports() !=
        addFullQualifiedImportsCheckBox.isSelected()) return true;
    if (ScalaProjectSettings.getInstance(myProject).isSortImports() !=
        sortImportsCheckBox.isSelected()) return true;
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
    if (ScalaProjectSettings.getInstance(myProject).isImportShortestPathForAmbiguousReferences() !=
        importTheShortestPathCheckBox.isSelected()) return true;

    if (ScalaProjectSettings.getInstance(myProject).isIgnorePerformance() != myResolveToAllClassesCheckBox.isSelected())
      return true;

    if (ScalaProjectSettings.getInstance(myProject).isDisableLangInjection() != myDisableLanguageInjection.isSelected())
      return true;

    if (ScalaProjectSettings.getInstance(myProject).isScalaPriority() != useScalaClassesPriorityCheckBox.isSelected())
      return true;

    if (ScalaProjectSettings.getInstance(myProject).getCollectionTypeHighlightingLevel() !=
        collectionHighlightingChooser.getSelectedIndex()) return true;

    if (!Arrays.deepEquals(ScalaProjectSettings.getInstance(myProject).getImportsWithPrefix(),
        getPrefixPackages())) return true;

    return false;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void resetImpl() {
    setSettings();
  }

  private void setSettings() {
    setValue(addImportStatementInCheckBox,
        ScalaProjectSettings.getInstance(myProject).isAddImportMostCloseToReference());
    setValue(addFullQualifiedImportsCheckBox,
        ScalaProjectSettings.getInstance(myProject).isAddFullQualifiedImports());
    setValue(sortImportsCheckBox,
        ScalaProjectSettings.getInstance(myProject).isSortImports());
    setValue(classCountSpinner,
        ScalaProjectSettings.getInstance(myProject).getClassCountToUseImportOnDemand());
    setValue(shiftSpinner,
        ScalaProjectSettings.getInstance(myProject).getShift());
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
    setValue(importTheShortestPathCheckBox,
        ScalaProjectSettings.getInstance(myProject).isImportShortestPathForAmbiguousReferences());
    collectionHighlightingChooser.setSelectedIndex(ScalaProjectSettings.getInstance(myProject).getCollectionTypeHighlightingLevel());
    myReferencesWithPrefixModel = new DefaultListModel();
    for (String aPackage : ScalaProjectSettings.getInstance(myProject).getImportsWithPrefix()) {
      myReferencesWithPrefixModel.add(myReferencesWithPrefixModel.size(), aPackage);
    }
    referencesWithPrefixList.setModel(myReferencesWithPrefixModel);
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
