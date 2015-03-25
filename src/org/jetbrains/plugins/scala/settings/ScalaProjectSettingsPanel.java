package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.TitledSeparator;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings;
import org.jetbrains.plugins.scala.components.InvalidRepoException;
import org.jetbrains.plugins.scala.components.ScalaPluginUpdater;
import org.jetbrains.plugins.scala.settings.uiControls.DependencyAwareInjectionSettings;
import org.jetbrains.plugins.scala.settings.uiControls.ScalaUiWithDependency;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

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
  private JTextArea myBasePackages;
  private JCheckBox worksheetInteractiveModeCheckBox;
  private JCheckBox showTypeInfoOnCheckBox;
  private JSpinner delaySpinner;
  private JComboBox updateChannel;
  private JCheckBox myAotCompletion;
  private JCheckBox useEclipseCompatibilityModeCheckBox;
  private JTextField scalaTestDefaultSuperClass;
  private JCheckBox useOldImplicitConversionCheckBox;
  private ScalaUiWithDependency.ComponentWithSettings injectionPrefixTable;
  private Project myProject;

  public ScalaProjectSettingsPanel(Project project) {
    myProject = project;
    $$$setupUI$$$();
    outputSpinner.setModel(new SpinnerNumberModel(35, 1, null, 1));
    updateChannel.setModel(new EnumComboBoxModel(ScalaApplicationSettings.pluginBranch.class));

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

  public void apply() throws ConfigurationException {
    if (!isModified()) return;

    final ScalaProjectSettings scalaProjectSettings = ScalaProjectSettings.getInstance(myProject);
    final ScalaCompileServerSettings compileServerSettings = ScalaCompileServerSettings.getInstance();

    compileServerSettings.SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER = showTypeInfoOnCheckBox.isSelected();
    compileServerSettings.SHOW_TYPE_TOOLTIP_DELAY = (Integer) delaySpinner.getValue();

    try {
      ScalaPluginUpdater.doUpdatePluginHostsAndCheck((ScalaApplicationSettings.pluginBranch) updateChannel.getModel().getSelectedItem());
    } catch (InvalidRepoException e) {
      throw new ConfigurationException(e.getMessage());
    }

    scalaProjectSettings.setBasePackages(getBasePackages());
    scalaProjectSettings.setScalaTestDefaultSuperClass(scalaTestDefaultSuperClass.getText());
    scalaProjectSettings.setImplicitParametersSearchDepth((Integer) implicitParametersSearchDepthSpinner.getValue());
    scalaProjectSettings.setOutputLimit((Integer) outputSpinner.getValue());
    scalaProjectSettings.setInProcessMode(runWorksheetInTheCheckBox.isSelected());
    scalaProjectSettings.setInteractiveMode(worksheetInteractiveModeCheckBox.isSelected());
    scalaProjectSettings.setUseEclipseCompatibility(useEclipseCompatibilityModeCheckBox.isSelected());

    scalaProjectSettings.setSearchAllSymbols(searchAllSymbolsIncludeCheckBox.isSelected());
    scalaProjectSettings.setEnableJavaToScalaConversion(enableConversionOnCopyCheckBox.isSelected());
    scalaProjectSettings.setDontShowConversionDialog(donTShowDialogCheckBox.isSelected());
    scalaProjectSettings.setTreatDocCommentAsBlockComment(treatDocCommentAsBlockComment.isSelected());
    scalaProjectSettings.setUseOldImplicitConversionAlg(useOldImplicitConversionCheckBox.isSelected());

    scalaProjectSettings.setShowImplisitConversions(showImplicitConversionsInCheckBox.isSelected());
    scalaProjectSettings.setShowArgumentsToByNameParams(showArgumentsToByNameParametersCheckBox.isSelected());
    scalaProjectSettings.setIncludeBlockExpressions(includeBlockExpressionsExpressionsCheckBox.isSelected());
    scalaProjectSettings.setIncludeLiterals(includeLiteralsCheckBox.isSelected());

    scalaProjectSettings.setIgnorePerformance(myResolveToAllClassesCheckBox.isSelected());
    scalaProjectSettings.setDisableLangInjection(myDisableLanguageInjection.isSelected());
    scalaProjectSettings.setDontCacheCompoundTypes(myDontCacheCompound.isSelected());
    scalaProjectSettings.setAotCOmpletion(myAotCompletion.isSelected());
    scalaProjectSettings.setScalaPriority(useScalaClassesPriorityCheckBox.isSelected());
    scalaProjectSettings.setCollectionTypeHighlightingLevel(collectionHighlightingChooser.getSelectedIndex());
    injectionPrefixTable.saveSettings(scalaProjectSettings);
  }

  private List<String> getBasePackages() {
    String[] parts = myBasePackages.getText().split("\\n|,|;");
    List<String> result = new ArrayList<String>();
    for (String part : parts) {
      String name = part.trim();
      if (!name.isEmpty()) result.add(name);
    }
    return result;
  }

  private void setBasePackages(List<String> packages) {
    String s = StringUtil.join(packages, "\n");
    myBasePackages.setText(s);
  }

  @SuppressWarnings({"ConstantConditions", "RedundantIfStatement"})
  public boolean isModified() {

    final ScalaProjectSettings scalaProjectSettings = ScalaProjectSettings.getInstance(myProject);
    final ScalaCompileServerSettings compileServerSettings = ScalaCompileServerSettings.getInstance();

    if (compileServerSettings.SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER != showTypeInfoOnCheckBox.isSelected()) return true;
    if (compileServerSettings.SHOW_TYPE_TOOLTIP_DELAY != (Integer) delaySpinner.getValue()) return true;

    if (!ScalaPluginUpdater.getScalaPluginBranch().equals(updateChannel.getModel().getSelectedItem())) return true;

    if (!scalaProjectSettings.getBasePackages().equals(
        getBasePackages())) return true;
    if (!scalaProjectSettings.getScalaTestDefaultSuperClass().equals(
        scalaTestDefaultSuperClass.getText())) return true;
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
    if (scalaProjectSettings.isUseEclipseCompatibility() != useEclipseCompatibilityModeCheckBox.isSelected())
      return true;

    if (scalaProjectSettings.isSearchAllSymbols() !=
        searchAllSymbolsIncludeCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isEnableJavaToScalaConversion() !=
        enableConversionOnCopyCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isDontShowConversionDialog() !=
        donTShowDialogCheckBox.isSelected()) return true;
    if (scalaProjectSettings.isTreatDocCommentAsBlockComment() !=
        treatDocCommentAsBlockComment.isSelected()) return true;
    if (scalaProjectSettings.isUseOldImplicitConversionAlg() !=
        useOldImplicitConversionCheckBox.isSelected()) return true;

    if (scalaProjectSettings.isIgnorePerformance() != myResolveToAllClassesCheckBox.isSelected())
      return true;

    if (scalaProjectSettings.isDisableLangInjection() != myDisableLanguageInjection.isSelected())
      return true;


    if (scalaProjectSettings.isDontCacheCompoundTypes() != myDontCacheCompound.isSelected()) return true;

    if (scalaProjectSettings.isAotCompletion() != myAotCompletion.isSelected())
      return true;

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
    final ScalaCompileServerSettings compileServerSettings = ScalaCompileServerSettings.getInstance();

    setValue(showTypeInfoOnCheckBox, compileServerSettings.SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER);
    setValue(delaySpinner, compileServerSettings.SHOW_TYPE_TOOLTIP_DELAY);

    updateChannel.getModel().setSelectedItem(ScalaPluginUpdater.getScalaPluginBranch());

    setBasePackages(scalaProjectSettings.getBasePackages());
    setValue(scalaTestDefaultSuperClass, scalaProjectSettings.getScalaTestDefaultSuperClass());
    setValue(implicitParametersSearchDepthSpinner, scalaProjectSettings.getImplicitParametersSearchDepth());
    setValue(outputSpinner, scalaProjectSettings.getOutputLimit());
    setValue(runWorksheetInTheCheckBox, scalaProjectSettings.isInProcessMode());
    setValue(worksheetInteractiveModeCheckBox, scalaProjectSettings.isInteractiveMode());
    setValue(useEclipseCompatibilityModeCheckBox, scalaProjectSettings.isUseEclipseCompatibility());

    setValue(searchAllSymbolsIncludeCheckBox, scalaProjectSettings.isSearchAllSymbols());
    setValue(enableConversionOnCopyCheckBox, scalaProjectSettings.isEnableJavaToScalaConversion());
    setValue(donTShowDialogCheckBox, scalaProjectSettings.isDontShowConversionDialog());
    setValue(treatDocCommentAsBlockComment, scalaProjectSettings.isTreatDocCommentAsBlockComment());
    setValue(useOldImplicitConversionCheckBox, scalaProjectSettings.isUseOldImplicitConversionAlg());

    setValue(showImplicitConversionsInCheckBox, scalaProjectSettings.isShowImplisitConversions());
    setValue(showArgumentsToByNameParametersCheckBox, scalaProjectSettings.isShowArgumentsToByNameParams());
    setValue(includeBlockExpressionsExpressionsCheckBox, scalaProjectSettings.isIncludeBlockExpressions());
    setValue(includeLiteralsCheckBox, scalaProjectSettings.isIncludeLiterals());

    setValue(myResolveToAllClassesCheckBox, scalaProjectSettings.isIgnorePerformance());

    setValue(myDisableLanguageInjection, scalaProjectSettings.isDisableLangInjection());
    setValue(myDontCacheCompound, scalaProjectSettings.isDontCacheCompoundTypes());
    setValue(myAotCompletion, scalaProjectSettings.isAotCompletion());
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

  private static void setValue(final JTextField field, final String value) {
    field.setText(value);
  }

  private void createUIComponents() {
    injectionJPanel = new JPanel(new GridLayout(1, 1));
    injectionJPanel.setPreferredSize(new Dimension(200, 500));
    injectionJPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 0));
  }

  /**
   * Method generated by IntelliJ IDEA GUI Designer
   * >>> IMPORTANT!! <<<
   * DO NOT edit this method OR call it in your code!
   *
   * @noinspection ALL
   */
  private void $$$setupUI$$$() {
    createUIComponents();
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JTabbedPane tabbedPane1 = new JTabbedPane();
    myPanel.add(tabbedPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(14, 2, new Insets(9, 9, 0, 0), -1, -1));
    tabbedPane1.addTab("Editor", panel1);
    final Spacer spacer1 = new Spacer();
    panel1.add(spacer1, new GridConstraints(13, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    showImplicitConversionsInCheckBox = new JCheckBox();
    showImplicitConversionsInCheckBox.setSelected(true);
    showImplicitConversionsInCheckBox.setText("Highlight methods added via implicit conversion in code completion dialog");
    panel1.add(showImplicitConversionsInCheckBox, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    showArgumentsToByNameParametersCheckBox = new JCheckBox();
    showArgumentsToByNameParametersCheckBox.setSelected(true);
    showArgumentsToByNameParametersCheckBox.setText("Highlight arguments to by-name parameters");
    panel1.add(showArgumentsToByNameParametersCheckBox, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    includeBlockExpressionsExpressionsCheckBox = new JCheckBox();
    includeBlockExpressionsExpressionsCheckBox.setSelected(true);
    includeBlockExpressionsExpressionsCheckBox.setText("Include block expressions");
    includeBlockExpressionsExpressionsCheckBox.setToolTipText("Include expressions enclosed in in curly braces");
    panel1.add(includeBlockExpressionsExpressionsCheckBox, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    includeLiteralsCheckBox = new JCheckBox();
    includeLiteralsCheckBox.setSelected(true);
    includeLiteralsCheckBox.setText("Include literals ");
    includeLiteralsCheckBox.setToolTipText("Include string, number, etc");
    panel1.add(includeLiteralsCheckBox, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    final JLabel label1 = new JLabel();
    this.$$$loadLabelText$$$(label1, ResourceBundle.getBundle("org/jetbrains/plugins/scala/ScalaBundle").getString("collection.type.highlighting.option"));
    panel1.add(label1, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    collectionHighlightingChooser = new JComboBox();
    final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
    defaultComboBoxModel1.addElement("None");
    defaultComboBoxModel1.addElement("Only non-qualified");
    defaultComboBoxModel1.addElement("All");
    collectionHighlightingChooser.setModel(defaultComboBoxModel1);
    panel1.add(collectionHighlightingChooser, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final TitledSeparator titledSeparator1 = new TitledSeparator();
    titledSeparator1.setText("Highlighting");
    panel1.add(titledSeparator1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final TitledSeparator titledSeparator2 = new TitledSeparator();
    titledSeparator2.setText("Autocomplete");
    panel1.add(titledSeparator2, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myAotCompletion = new JCheckBox();
    myAotCompletion.setText("Ahead-of-time completion (parameter and variable names)");
    panel1.add(myAotCompletion, new GridConstraints(8, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    useScalaClassesPriorityCheckBox = new JCheckBox();
    useScalaClassesPriorityCheckBox.setSelected(true);
    useScalaClassesPriorityCheckBox.setText("Use Scala classes priority over Java classes");
    panel1.add(useScalaClassesPriorityCheckBox, new GridConstraints(9, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    enableConversionOnCopyCheckBox = new JCheckBox();
    enableConversionOnCopyCheckBox.setSelected(true);
    enableConversionOnCopyCheckBox.setText("Convert Java code to Scala on copy-paste");
    panel1.add(enableConversionOnCopyCheckBox, new GridConstraints(11, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    donTShowDialogCheckBox = new JCheckBox();
    donTShowDialogCheckBox.setText("Don't show dialog on paste and automatically convert to Scala code");
    panel1.add(donTShowDialogCheckBox, new GridConstraints(12, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    final TitledSeparator titledSeparator3 = new TitledSeparator();
    titledSeparator3.setText("Code Conversion");
    panel1.add(titledSeparator3, new GridConstraints(10, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(panel2, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    showTypeInfoOnCheckBox = new JCheckBox();
    showTypeInfoOnCheckBox.setText("Show type info on mouse hover after, ms");
    panel2.add(showTypeInfoOnCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    delaySpinner = new JSpinner();
    panel2.add(delaySpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(8, 2, new Insets(9, 9, 0, 0), -1, -1));
    tabbedPane1.addTab("Performance", panel3);
    final Spacer spacer2 = new Spacer();
    panel3.add(spacer2, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final JLabel label2 = new JLabel();
    label2.setText("Implicit parameters search depth (-1 for none):");
    panel3.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    implicitParametersSearchDepthSpinner = new JSpinner();
    panel3.add(implicitParametersSearchDepthSpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(50, -1), null, null, 0, false));
    myResolveToAllClassesCheckBox = new JCheckBox();
    myResolveToAllClassesCheckBox.setText("Resolve to all classes, even in wrong directories (this may cause performance problems)");
    panel3.add(myResolveToAllClassesCheckBox, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    treatDocCommentAsBlockComment = new JCheckBox();
    treatDocCommentAsBlockComment.setText("Disable parsing of documentation comments. This may improve editor performance for very large files. (SCL-2900)");
    panel3.add(treatDocCommentAsBlockComment, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDisableLanguageInjection = new JCheckBox();
    myDisableLanguageInjection.setText("Disable language injection in Scala files (injected languages may freeze typing with auto popup completion)");
    panel3.add(myDisableLanguageInjection, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDontCacheCompound = new JCheckBox();
    myDontCacheCompound.setText("Don't cache compound types (use it in case of big pauses in GC)");
    panel3.add(myDontCacheCompound, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    useOldImplicitConversionCheckBox = new JCheckBox();
    useOldImplicitConversionCheckBox.setText("Use old implicit conversion search algorithm");
    panel3.add(useOldImplicitConversionCheckBox, new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    searchAllSymbolsIncludeCheckBox = new JCheckBox();
    searchAllSymbolsIncludeCheckBox.setText("Search all symbols (include locals)");
    panel3.add(searchAllSymbolsIncludeCheckBox, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel4 = new JPanel();
    panel4.setLayout(new GridLayoutManager(5, 2, new Insets(9, 9, 0, 0), -1, -1));
    tabbedPane1.addTab("Worksheet", panel4);
    final Spacer spacer3 = new Spacer();
    panel4.add(spacer3, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    runWorksheetInTheCheckBox = new JCheckBox();
    runWorksheetInTheCheckBox.setSelected(true);
    runWorksheetInTheCheckBox.setText("Run worksheet in the compiler process");
    panel4.add(runWorksheetInTheCheckBox, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    worksheetInteractiveModeCheckBox = new JCheckBox();
    worksheetInteractiveModeCheckBox.setText("Run worksheet in the interactive mode");
    panel4.add(worksheetInteractiveModeCheckBox, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label3 = new JLabel();
    label3.setText("Output cutoff limit, lines: ");
    panel4.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    outputSpinner = new JSpinner();
    panel4.add(outputSpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(50, -1), null, null, 0, false));
    useEclipseCompatibilityModeCheckBox = new JCheckBox();
    useEclipseCompatibilityModeCheckBox.setText("Use \"eclipse compatibility\" mode");
    panel4.add(useEclipseCompatibilityModeCheckBox, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel5 = new JPanel();
    panel5.setLayout(new GridLayoutManager(2, 2, new Insets(9, 9, 0, 0), -1, -1));
    tabbedPane1.addTab("Base packages", panel5);
    final Spacer spacer4 = new Spacer();
    panel5.add(spacer4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final JScrollPane scrollPane1 = new JScrollPane();
    panel5.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myBasePackages = new JTextArea();
    myBasePackages.setColumns(50);
    myBasePackages.setRows(10);
    scrollPane1.setViewportView(myBasePackages);
    final Spacer spacer5 = new Spacer();
    panel5.add(spacer5, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    final JPanel panel6 = new JPanel();
    panel6.setLayout(new GridLayoutManager(3, 2, new Insets(9, 9, 0, 0), -1, -1));
    tabbedPane1.addTab("Misc", panel6);
    panel6.add(injectionJPanel, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final JLabel label4 = new JLabel();
    label4.setText("ScalaTest default super class:");
    panel6.add(label4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer6 = new Spacer();
    panel6.add(spacer6, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    scalaTestDefaultSuperClass = new JTextField();
    scalaTestDefaultSuperClass.setColumns(25);
    panel6.add(scalaTestDefaultSuperClass, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    final JPanel panel7 = new JPanel();
    panel7.setLayout(new GridLayoutManager(2, 2, new Insets(9, 9, 0, 0), -1, -1));
    tabbedPane1.addTab("Updates", panel7);
    final Spacer spacer7 = new Spacer();
    panel7.add(spacer7, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final JLabel label5 = new JLabel();
    label5.setText("Plugin update channel:");
    panel7.add(label5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    updateChannel = new JComboBox();
    updateChannel.setEditable(false);
    final DefaultComboBoxModel defaultComboBoxModel2 = new DefaultComboBoxModel();
    defaultComboBoxModel2.addElement("Release");
    defaultComboBoxModel2.addElement("EAP");
    defaultComboBoxModel2.addElement("Nightly");
    updateChannel.setModel(defaultComboBoxModel2);
    panel7.add(updateChannel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
  }

  /**
   * @noinspection ALL
   */
  private void $$$loadLabelText$$$(JLabel component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setDisplayedMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }

  /**
   * @noinspection ALL
   */
  public JComponent $$$getRootComponent$$$() {
    return myPanel;
  }
}
