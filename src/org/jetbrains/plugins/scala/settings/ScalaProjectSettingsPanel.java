package org.jetbrains.plugins.scala.settings;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings;
import org.jetbrains.plugins.scala.components.ScalaPluginUpdater;
import org.jetbrains.plugins.scala.settings.uiControls.DependencyAwareInjectionSettings;
import org.jetbrains.plugins.scala.settings.uiControls.ScalaUiWithDependency;

import javax.swing.*;
import java.awt.*;
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
  private JTextField myBasePackage;
  private JCheckBox worksheetInteractiveModeCheckBox;
  private JCheckBox showTypeInfoOnCheckBox;
  private JSpinner delaySpinner;
  private JComboBox updateChannel;
  private JCheckBox myAotCompletion;
  private JCheckBox useEclipseCompatibilityModeCheckBox;
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

  public void apply() {
    if (!isModified()) return;

    final ScalaProjectSettings scalaProjectSettings = ScalaProjectSettings.getInstance(myProject);
    final ScalaCompileServerSettings compileServerSettings = ScalaCompileServerSettings.getInstance();

    compileServerSettings.SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER = showTypeInfoOnCheckBox.isSelected();
    compileServerSettings.SHOW_TYPE_TOOLTIP_DELAY = (Integer) delaySpinner.getValue();

    ScalaPluginUpdater.doUpdatePluginHostsAndCheck((ScalaApplicationSettings.pluginBranch) updateChannel.getModel().getSelectedItem());

    scalaProjectSettings.setBasePackage(myBasePackage.getText());
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

  @SuppressWarnings({"ConstantConditions", "RedundantIfStatement"})
  public boolean isModified() {

    final ScalaProjectSettings scalaProjectSettings = ScalaProjectSettings.getInstance(myProject);
    final ScalaCompileServerSettings compileServerSettings = ScalaCompileServerSettings.getInstance();

    if (compileServerSettings.SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER != showTypeInfoOnCheckBox.isSelected()) return true;
    if (compileServerSettings.SHOW_TYPE_TOOLTIP_DELAY != (Integer) delaySpinner.getValue()) return true;

    if (!ScalaPluginUpdater.getScalaPluginBranch().equals(updateChannel.getModel().getSelectedItem())) return true;

    if (!scalaProjectSettings.getBasePackage().equals(
        myBasePackage.getText())) return true;
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

    setValue(myBasePackage, scalaProjectSettings.getBasePackage());
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
    myPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    final JTabbedPane tabbedPane1 = new JTabbedPane();
    myPanel.add(tabbedPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(23, 3, new Insets(9, 9, 0, 0), -1, -1));
    tabbedPane1.addTab("Core", panel1);
    searchAllSymbolsIncludeCheckBox = new JCheckBox();
    searchAllSymbolsIncludeCheckBox.setText("Search all symbols (include locals)");
    panel1.add(searchAllSymbolsIncludeCheckBox, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label1 = new JLabel();
    label1.setText("Java to Scala conversions settings:");
    panel1.add(label1, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    enableConversionOnCopyCheckBox = new JCheckBox();
    enableConversionOnCopyCheckBox.setSelected(true);
    enableConversionOnCopyCheckBox.setText("Enable Conversion on Copy");
    panel1.add(enableConversionOnCopyCheckBox, new GridConstraints(4, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    donTShowDialogCheckBox = new JCheckBox();
    donTShowDialogCheckBox.setText("Don't show dialog on paste and automatically convert to Scala code");
    panel1.add(donTShowDialogCheckBox, new GridConstraints(5, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label2 = new JLabel();
    label2.setText("Highlighting options:");
    panel1.add(label2, new GridConstraints(6, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    showImplicitConversionsInCheckBox = new JCheckBox();
    showImplicitConversionsInCheckBox.setSelected(true);
    showImplicitConversionsInCheckBox.setText("Highlight methods added via implicit conversion  in  code completion dialog");
    panel1.add(showImplicitConversionsInCheckBox, new GridConstraints(7, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label3 = new JLabel();
    label3.setText("Performance-related options:");
    panel1.add(label3, new GridConstraints(11, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myResolveToAllClassesCheckBox = new JCheckBox();
    myResolveToAllClassesCheckBox.setText("Resolve to all classes, even in wrong directories (this may cause performance problems)");
    panel1.add(myResolveToAllClassesCheckBox, new GridConstraints(13, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    showArgumentsToByNameParametersCheckBox = new JCheckBox();
    showArgumentsToByNameParametersCheckBox.setSelected(true);
    showArgumentsToByNameParametersCheckBox.setText("Highlight arguments to by-name parameters");
    panel1.add(showArgumentsToByNameParametersCheckBox, new GridConstraints(8, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    includeBlockExpressionsExpressionsCheckBox = new JCheckBox();
    includeBlockExpressionsExpressionsCheckBox.setSelected(true);
    includeBlockExpressionsExpressionsCheckBox.setText("Include block expressions");
    includeBlockExpressionsExpressionsCheckBox.setToolTipText("Include expressions enclosed in in curly braces");
    panel1.add(includeBlockExpressionsExpressionsCheckBox, new GridConstraints(9, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    includeLiteralsCheckBox = new JCheckBox();
    includeLiteralsCheckBox.setSelected(true);
    includeLiteralsCheckBox.setText("Include literals ");
    includeLiteralsCheckBox.setToolTipText("Include string, number, etc");
    panel1.add(includeLiteralsCheckBox, new GridConstraints(10, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    treatDocCommentAsBlockComment = new JCheckBox();
    treatDocCommentAsBlockComment.setText("Disable parsing of documentation comments. This may improve editor performance for very large files. (SCL-2900)");
    panel1.add(treatDocCommentAsBlockComment, new GridConstraints(14, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDisableLanguageInjection = new JCheckBox();
    myDisableLanguageInjection.setText("Disable language injection in Scala files (injected languages may freeze typing with auto popup completion)");
    panel1.add(myDisableLanguageInjection, new GridConstraints(15, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label4 = new JLabel();
    label4.setText("Completion options:");
    panel1.add(label4, new GridConstraints(18, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    useScalaClassesPriorityCheckBox = new JCheckBox();
    useScalaClassesPriorityCheckBox.setSelected(true);
    useScalaClassesPriorityCheckBox.setText("Use Scala classes priority over Java classes");
    panel1.add(useScalaClassesPriorityCheckBox, new GridConstraints(20, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(panel2, new GridConstraints(22, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    final JLabel label5 = new JLabel();
    label5.setText("Implicit parameters search depth:");
    panel1.add(label5, new GridConstraints(12, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    implicitParametersSearchDepthSpinner = new JSpinner();
    panel1.add(implicitParametersSearchDepthSpinner, new GridConstraints(12, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDontCacheCompound = new JCheckBox();
    myDontCacheCompound.setText("Don't cache compound types (use it in case of big pauses in GC)");
    panel1.add(myDontCacheCompound, new GridConstraints(16, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label6 = new JLabel();
    label6.setText("Base package clause:");
    panel1.add(label6, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myBasePackage = new JTextField();
    myBasePackage.setColumns(50);
    panel1.add(myBasePackage, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    final JLabel label7 = new JLabel();
    this.$$$loadLabelText$$$(label7, ResourceBundle.getBundle("org/jetbrains/plugins/scala/ScalaBundle").getString("collection.type.highlighting.option"));
    panel1.add(label7, new GridConstraints(21, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    collectionHighlightingChooser = new JComboBox();
    final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
    defaultComboBoxModel1.addElement("None");
    defaultComboBoxModel1.addElement("Only non-qualified");
    defaultComboBoxModel1.addElement("All");
    collectionHighlightingChooser.setModel(defaultComboBoxModel1);
    panel1.add(collectionHighlightingChooser, new GridConstraints(21, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    panel1.add(injectionJPanel, new GridConstraints(22, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    showTypeInfoOnCheckBox = new JCheckBox();
    showTypeInfoOnCheckBox.setText("Show type info on mouse motion with delay:");
    panel1.add(showTypeInfoOnCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    delaySpinner = new JSpinner();
    panel1.add(delaySpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    panel1.add(spacer1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myAotCompletion = new JCheckBox();
    myAotCompletion.setText("Ahead-of-time completion (parameter and variable names)");
    panel1.add(myAotCompletion, new GridConstraints(19, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    useOldImplicitConversionCheckBox = new JCheckBox();
    useOldImplicitConversionCheckBox.setText("Use old implicit conversion search algorithm");
    panel1.add(useOldImplicitConversionCheckBox, new GridConstraints(17, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(5, 2, new Insets(9, 9, 0, 0), -1, -1));
    tabbedPane1.addTab("Worksheet", panel3);
    final Spacer spacer2 = new Spacer();
    panel3.add(spacer2, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    runWorksheetInTheCheckBox = new JCheckBox();
    runWorksheetInTheCheckBox.setSelected(true);
    runWorksheetInTheCheckBox.setText("Run worksheet in the compiler process");
    panel3.add(runWorksheetInTheCheckBox, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    worksheetInteractiveModeCheckBox = new JCheckBox();
    worksheetInteractiveModeCheckBox.setText("Run worksheet in the interactive mode");
    panel3.add(worksheetInteractiveModeCheckBox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label8 = new JLabel();
    label8.setText("Output cutoff limit: ");
    panel3.add(label8, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    outputSpinner = new JSpinner();
    panel3.add(outputSpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(218, 24), null, 0, false));
    useEclipseCompatibilityModeCheckBox = new JCheckBox();
    useEclipseCompatibilityModeCheckBox.setText("Use \"eclipse compatibility\" mode");
    panel3.add(useEclipseCompatibilityModeCheckBox, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel4 = new JPanel();
    panel4.setLayout(new GridLayoutManager(2, 2, new Insets(9, 9, 0, 0), -1, -1));
    tabbedPane1.addTab("Misc", panel4);
    final Spacer spacer3 = new Spacer();
    panel4.add(spacer3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final JLabel label9 = new JLabel();
    label9.setText("Plugin update channel:");
    panel4.add(label9, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    updateChannel = new JComboBox();
    updateChannel.setEditable(false);
    final DefaultComboBoxModel defaultComboBoxModel2 = new DefaultComboBoxModel();
    defaultComboBoxModel2.addElement("Release");
    defaultComboBoxModel2.addElement("EAP");
    defaultComboBoxModel2.addElement("Nightly");
    updateChannel.setModel(defaultComboBoxModel2);
    panel4.add(updateChannel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
