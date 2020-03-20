package org.jetbrains.plugins.scala.settings;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.TitledSeparator;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings;
import org.jetbrains.plugins.scala.components.InvalidRepoException;
import org.jetbrains.plugins.scala.components.ScalaPluginUpdater;
import org.jetbrains.plugins.scala.components.libextensions.ui.LibExtensionsSettingsPanelWrapper;
import org.jetbrains.plugins.scala.project.NamedValueRenderer;
import org.jetbrains.plugins.scala.settings.uiControls.ScalaUiWithDependency;
import org.jetbrains.plugins.scala.worksheet.interactive.WorksheetAutoRunner$;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.pluginBranch;
import static org.jetbrains.plugins.scala.settings.ScalaProjectSettings.*;
import static org.jetbrains.plugins.scala.settings.uiControls.ScalaUiWithDependency.ComponentWithSettings;
import static org.jetbrains.plugins.scala.settings.uiControls.ScalaUiWithDependency.EP_NAME;

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.07.2008
 */
// TODO: cleanup
//  1) split this panel into multiple per-tab
//  2) worksheet slider indicator SCL-16341
//  3) use bundles SCL-16963
@SuppressWarnings(value = "unchecked")
public class ScalaProjectSettingsPanel {

    private static final String LAST_SELECTED_TAB_INDEX = "scala_project_settings_configurable.last_selected_tab_index";

    private final LibExtensionsSettingsPanelWrapper extensionsPanel;

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
    private JComboBox<ScalaCollectionHighlightingLevel> collectionHighlightingChooser;
    private JPanel injectionJPanel;
    private JSpinner outputSpinner;
    private JSpinner implicitParametersSearchDepthSpinner;
    private JCheckBox myDontCacheCompound;
    private JCheckBox runWorksheetInTheCheckBox;
    private JTextArea myBasePackages;
    private JCheckBox showTypeInfoOnCheckBox;
    private JSpinner delaySpinner;
    private JComboBox<pluginBranch> updateChannel;
    private JCheckBox myAotCompletion;
    private JCheckBox useEclipseCompatibilityModeCheckBox;
    private JTextField scalaTestDefaultSuperClass;
    private JCheckBox treatScalaScratchFilesCheckBox;
    private JSlider autoRunDelaySlider;
    private JCheckBox customScalatestSyntaxHighlightingCheckbox;
    private JPanel librariesPanel;
    private JButton updateNowButton;
    private JCheckBox addOverrideToImplementCheckBox;
    private JCheckBox myProjectViewHighlighting;
    private JComboBox<ScalaMetaMode> scalaMetaMode;
    private JCheckBox metaTrimBodies;
    private JComboBox<ScFileMode> scTypeSelectionCombobox;
    private JComboBox<TrailingCommasMode> trailingCommasComboBox;
    private JCheckBox collapseWorksheetFoldByCheckBox;
    private JCheckBox showNotFoundImplicitArgumentsCheckBox;
    private JCheckBox showAmbiguousImplicitArgumentsCheckBox;
    private JCheckBox myGroupPackageObjectWithPackage;
    private JComboBox<Ivy2IndexingMode> ivy2IndexingModeCBB;
    private final Project myProject;

    private JTabbedPane tabbedPane;

    private final List<ComponentWithSettings> extraSettings = new ArrayList<>();

    public ScalaProjectSettingsPanel(Project project) {
        myProject = project;

        $$$setupUI$$$();

        collectionHighlightingChooser.setModel(new DefaultComboBoxModel<>(ScalaCollectionHighlightingLevel.values()));
        collectionHighlightingChooser.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(ScalaCollectionHighlightingLevel.None, ScalaBundle.message("scala.collection.highlighting.type.none")),
                Pair.create(ScalaCollectionHighlightingLevel.OnlyNonQualified, ScalaBundle.message("scala.collection.highlighting.type.only.non.qualified")),
                Pair.create(ScalaCollectionHighlightingLevel.All, ScalaBundle.message("scala.collection.highlighting.type.all"))
        ));

        outputSpinner.setModel(new SpinnerNumberModel(35, 1, null, 1));

        updateChannel.setModel(new EnumComboBoxModel<>(pluginBranch.class));
        updateChannel.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(pluginBranch.Nightly, ScalaBundle.message("scala.plugin.chanel.nightly")),
                Pair.create(pluginBranch.EAP, ScalaBundle.message("scala.plugin.chanel.eap")),
                Pair.create(pluginBranch.Release, ScalaBundle.message("scala.plugin.chanel.release"))
        ));

        scalaMetaMode.setModel(new EnumComboBoxModel<>(ScalaMetaMode.class));
        scalaMetaMode.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(ScalaMetaMode.Enabled, ScalaBundle.message("scala.meta.mode.enabled")),
                Pair.create(ScalaMetaMode.Disabled, ScalaBundle.message("scala.meta.mode.disabled")),
                Pair.create(ScalaMetaMode.Manual, ScalaBundle.message("scala.meta.mode.manual"))
        ));

        ivy2IndexingModeCBB.setModel(new EnumComboBoxModel<>(Ivy2IndexingMode.class));
        ivy2IndexingModeCBB.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(Ivy2IndexingMode.Disabled, ScalaBundle.message("ivy2.indexing.mode.disabled")),
                Pair.create(Ivy2IndexingMode.Metadata, ScalaBundle.message("ivy2.indexing.mode.metadata")),
                Pair.create(Ivy2IndexingMode.Classes, ScalaBundle.message("ivy2.indexing.mode.classes"))
        ));

        trailingCommasComboBox.setModel(new EnumComboBoxModel<>(TrailingCommasMode.class));
        trailingCommasComboBox.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(TrailingCommasMode.Enabled, ScalaBundle.message("trailing.commas.mode.enabled")),
                Pair.create(TrailingCommasMode.Disabled, ScalaBundle.message("trailing.commas.mode.disabled")),
                Pair.create(TrailingCommasMode.Auto, ScalaBundle.message("trailing.commas.mode.auto"))
        ));

        scTypeSelectionCombobox.setModel(new EnumComboBoxModel<>(ScFileMode.class));
        scTypeSelectionCombobox.setRenderer(SimpleMappingListCellRenderer.create(
                Pair.create(ScFileMode.Worksheet, ScalaBundle.message("script.file.mode.always.worksheet")),
                Pair.create(ScFileMode.Ammonite, ScalaBundle.message("script.file.mode.always.ammonite")),
                Pair.create(ScFileMode.Auto, ScalaBundle.message("script.file.mode.ammonite.in.test.sources.otherwise.worksheet"))
        ));

        updateNowButton.addActionListener(e -> {
            try {
                ScalaPluginUpdater.doUpdatePluginHosts((pluginBranch) updateChannel.getModel().getSelectedItem());
                UpdateChecker.updateAndShowResult(myProject, UpdateSettings.getInstance());
            } catch (InvalidRepoException ex) {
                Messages.showErrorDialog(ex.getMessage(), "Invalid Update Channel");
            }
        });

        autoRunDelaySlider.setMaximum(WorksheetAutoRunner$.MODULE$.RUN_DELAY_MS_MAXIMUM());
        autoRunDelaySlider.setMinimum(WorksheetAutoRunner$.MODULE$.RUN_DELAY_MS_MINIMUM());

        extensionsPanel = new LibExtensionsSettingsPanelWrapper((JPanel) librariesPanel.getParent(), project);
        extensionsPanel.build();

        for (ScalaUiWithDependency uiWithDependency : EP_NAME.getExtensionList()) {
            extraSettings.add(uiWithDependency.createComponent(injectionJPanel));
        }

        setSettings();

        initSelectedTab();
    }

    @NotNull
    protected FileType getFileType() {
        return ScalaFileType.INSTANCE;
    }

    public void apply() throws ConfigurationException {
        if (!isModified()) return;

        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);
        final ScalaCompileServerSettings compileServerSettings = ScalaCompileServerSettings.getInstance();

        compileServerSettings.SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER = showTypeInfoOnCheckBox.isSelected();
        compileServerSettings.SHOW_TYPE_TOOLTIP_DELAY = (Integer) delaySpinner.getValue();

        try {
            ScalaPluginUpdater.doUpdatePluginHostsAndCheck((pluginBranch) updateChannel.getModel().getSelectedItem());
        } catch (InvalidRepoException e) {
            throw new ConfigurationException(e.getMessage());
        }

        scalaProjectSettings.setBasePackages(getBasePackages());
        scalaProjectSettings.setScalaTestDefaultSuperClass(scalaTestDefaultSuperClass.getText());
        scalaProjectSettings.setImplicitParametersSearchDepth((Integer) implicitParametersSearchDepthSpinner.getValue());
        scalaProjectSettings.setOutputLimit((Integer) outputSpinner.getValue());
        scalaProjectSettings.setInProcessMode(runWorksheetInTheCheckBox.isSelected());
        scalaProjectSettings.setWorksheetFoldCollapsedByDefault(collapseWorksheetFoldByCheckBox.isSelected());
        scalaProjectSettings.setUseEclipseCompatibility(useEclipseCompatibilityModeCheckBox.isSelected());
        scalaProjectSettings.setTreatScratchFilesAsWorksheet(treatScalaScratchFilesCheckBox.isSelected());

        scalaProjectSettings.setSearchAllSymbols(searchAllSymbolsIncludeCheckBox.isSelected());
        scalaProjectSettings.setEnableJavaToScalaConversion(enableConversionOnCopyCheckBox.isSelected());
        scalaProjectSettings.setAddOverrideToImplementInConverter(addOverrideToImplementCheckBox.isSelected());
        scalaProjectSettings.setDontShowConversionDialog(donTShowDialogCheckBox.isSelected());
        scalaProjectSettings.setTreatDocCommentAsBlockComment(treatDocCommentAsBlockComment.isSelected());

        scalaProjectSettings.setShowImplisitConversions(showImplicitConversionsInCheckBox.isSelected());
        scalaProjectSettings.setShowNotFoundImplicitArguments(showNotFoundImplicitArgumentsCheckBox.isSelected());
        scalaProjectSettings.setShowAmbiguousImplicitArguments(showAmbiguousImplicitArgumentsCheckBox.isSelected());
        scalaProjectSettings.setShowArgumentsToByNameParams(showArgumentsToByNameParametersCheckBox.isSelected());
        if (scalaProjectSettings.isCustomScalatestSyntaxHighlighting() != customScalatestSyntaxHighlightingCheckbox.isSelected()) {
            //settings have changed. but actual highlighting will only change on next pass of highlighting visitors
            new ScalaTestHighlightingDialog().show();
        }
        scalaProjectSettings.setCustomScalatestSyntaxHighlighting(customScalatestSyntaxHighlightingCheckbox.isSelected());
        scalaProjectSettings.setIncludeBlockExpressions(includeBlockExpressionsExpressionsCheckBox.isSelected());
        scalaProjectSettings.setIncludeLiterals(includeLiteralsCheckBox.isSelected());

        scalaProjectSettings.setIgnorePerformance(myResolveToAllClassesCheckBox.isSelected());
        scalaProjectSettings.setDisableLangInjection(myDisableLanguageInjection.isSelected());
        scalaProjectSettings.setDontCacheCompoundTypes(myDontCacheCompound.isSelected());
        scalaProjectSettings.setAotCOmpletion(myAotCompletion.isSelected());
        scalaProjectSettings.setScalaPriority(useScalaClassesPriorityCheckBox.isSelected());
        scalaProjectSettings.setCollectionTypeHighlightingLevel((ScalaCollectionHighlightingLevel) collectionHighlightingChooser.getSelectedItem());
        scalaProjectSettings.setAutoRunDelay(getWorksheetDelay());

        if (scalaProjectSettings.isProjectViewHighlighting() && !myProjectViewHighlighting.isSelected()) {
            ProblemSolverUtils.clearProblemsIn(myProject);
        }
        scalaProjectSettings.setProjectViewHighlighting(myProjectViewHighlighting.isSelected());
        if (scalaProjectSettings.isGroupPackageObjectWithPackage() != myGroupPackageObjectWithPackage.isSelected()) {
            AbstractProjectViewPane pane = ProjectView.getInstance(myProject).getCurrentProjectViewPane();
            if (pane != null) {
                pane.updateFromRoot(true);
            }
        }
        scalaProjectSettings.setGroupPackageObjectWithPackage(myGroupPackageObjectWithPackage.isSelected());

        scalaProjectSettings.setScalaMetaMode((ScalaMetaMode) scalaMetaMode.getModel().getSelectedItem());
        scalaProjectSettings.setMetaTrimMethodBodies(metaTrimBodies.isSelected());

        scalaProjectSettings.setIvy2IndexingMode((Ivy2IndexingMode) ivy2IndexingModeCBB.getModel().getSelectedItem());

        Object type = scTypeSelectionCombobox.getSelectedItem();
        if (type != null)
            scalaProjectSettings.setScFileMode(ScFileMode.valueOf(type.toString()));
        Object trailingComa = trailingCommasComboBox.getSelectedItem();
        if (trailingComa != null)
            scalaProjectSettings.setTrailingCommasMode(TrailingCommasMode.valueOf(trailingComa.toString()));

        scalaProjectSettings.setEnableLibraryExtensions(extensionsPanel.enabledCB().isSelected());

        extraSettings.forEach(s -> s.saveSettings(scalaProjectSettings));
    }

    private List<String> getBasePackages() {
        String[] parts = myBasePackages.getText().split("[\\n,;]");
        List<String> result = new ArrayList<>();
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

    @SuppressWarnings({"RedundantIfStatement"})
    public boolean isModified() {

        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);
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
        if (scalaProjectSettings.isShowNotFoundImplicitArguments() !=
                showNotFoundImplicitArgumentsCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isShowAmbiguousImplicitArguments() !=
                showAmbiguousImplicitArgumentsCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isShowArgumentsToByNameParams() !=
                showArgumentsToByNameParametersCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isCustomScalatestSyntaxHighlighting() !=
                customScalatestSyntaxHighlightingCheckbox.isSelected()) return true;
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
        if (scalaProjectSettings.isWorksheetFoldCollapsedByDefault() !=
                collapseWorksheetFoldByCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isUseEclipseCompatibility() != useEclipseCompatibilityModeCheckBox.isSelected())
            return true;
        if (scalaProjectSettings.isTreatScratchFilesAsWorksheet() != treatScalaScratchFilesCheckBox.isSelected())
            return true;

        if (scalaProjectSettings.isSearchAllSymbols() !=
                searchAllSymbolsIncludeCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isEnableJavaToScalaConversion() !=
                enableConversionOnCopyCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isAddOverrideToImplementInConverter() !=
                addOverrideToImplementCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isDontShowConversionDialog() !=
                donTShowDialogCheckBox.isSelected()) return true;
        if (scalaProjectSettings.isTreatDocCommentAsBlockComment() !=
                treatDocCommentAsBlockComment.isSelected()) return true;

        if (scalaProjectSettings.isIgnorePerformance() != myResolveToAllClassesCheckBox.isSelected())
            return true;

        if (scalaProjectSettings.isDisableLangInjection() != myDisableLanguageInjection.isSelected())
            return true;


        if (scalaProjectSettings.isDontCacheCompoundTypes() != myDontCacheCompound.isSelected()) return true;

        if (scalaProjectSettings.isAotCompletion() != myAotCompletion.isSelected())
            return true;

        if (scalaProjectSettings.isProjectViewHighlighting() != myProjectViewHighlighting.isSelected())
            return true;

        if (scalaProjectSettings.isGroupPackageObjectWithPackage() != myGroupPackageObjectWithPackage.isSelected())
            return true;

        if (scalaProjectSettings.isScalaPriority() != useScalaClassesPriorityCheckBox.isSelected())
            return true;

        if (scalaProjectSettings.getCollectionTypeHighlightingLevel() !=
                collectionHighlightingChooser.getSelectedItem()) return true;

        if (scalaProjectSettings.getAutoRunDelay() != getWorksheetDelay()) return true;

        for (ComponentWithSettings setting : extraSettings) {
            if (setting.isModified(scalaProjectSettings)) return true;
        }

        if (!scalaProjectSettings.getScalaMetaMode().equals(scalaMetaMode.getModel().getSelectedItem())) return true;
        if (scalaProjectSettings.isMetaTrimMethodBodies() != metaTrimBodies.isSelected()) return true;

        if (!scalaProjectSettings.getIvy2IndexingMode().equals(ivy2IndexingModeCBB.getModel().getSelectedItem()))
            return true;

        if (scalaProjectSettings.getScFileMode() != scTypeSelectionCombobox.getSelectedItem()) return true;
        if (scalaProjectSettings.getTrailingCommasMode() != trailingCommasComboBox.getSelectedItem()) return true;

        if (scalaProjectSettings.isEnableLibraryExtensions() != extensionsPanel.enabledCB().isSelected()) return true;

        return false;
    }

    public JComponent getPanel() {
        return myPanel;
    }

    void dispose() {
        int tabIdx = tabbedPane.getSelectedIndex();
        PropertiesComponent.getInstance(myProject).setValue(LAST_SELECTED_TAB_INDEX, tabIdx, -1);
    }

    private void initSelectedTab() {
        int tabIdx = PropertiesComponent.getInstance(myProject).getInt(LAST_SELECTED_TAB_INDEX, -1);
        setSelectedTabIndex(tabIdx);
    }

    private void setSelectedTabIndex(int index) {
        if (0 <= index && index < tabbedPane.getTabCount()) {
            tabbedPane.setSelectedIndex(index);
        }
    }

    protected void resetImpl() {
        setSettings();
    }

    private void setSettings() {
        final ScalaProjectSettings scalaProjectSettings = getInstance(myProject);
        final ScalaCompileServerSettings compileServerSettings = ScalaCompileServerSettings.getInstance();

        setValue(showTypeInfoOnCheckBox, compileServerSettings.SHOW_TYPE_TOOLTIP_ON_MOUSE_HOVER);
        setValue(delaySpinner, compileServerSettings.SHOW_TYPE_TOOLTIP_DELAY);

        updateChannel.getModel().setSelectedItem(ScalaPluginUpdater.getScalaPluginBranch());

        setBasePackages(scalaProjectSettings.getBasePackages());
        setValue(scalaTestDefaultSuperClass, scalaProjectSettings.getScalaTestDefaultSuperClass());
        setValue(implicitParametersSearchDepthSpinner, scalaProjectSettings.getImplicitParametersSearchDepth());
        setValue(outputSpinner, scalaProjectSettings.getOutputLimit());
        setValue(runWorksheetInTheCheckBox, scalaProjectSettings.isInProcessMode());
        setValue(collapseWorksheetFoldByCheckBox, scalaProjectSettings.isWorksheetFoldCollapsedByDefault());
        setValue(useEclipseCompatibilityModeCheckBox, scalaProjectSettings.isUseEclipseCompatibility());
        setValue(treatScalaScratchFilesCheckBox, scalaProjectSettings.isTreatScratchFilesAsWorksheet());

        setValue(searchAllSymbolsIncludeCheckBox, scalaProjectSettings.isSearchAllSymbols());
        setValue(enableConversionOnCopyCheckBox, scalaProjectSettings.isEnableJavaToScalaConversion());
        setValue(addOverrideToImplementCheckBox, scalaProjectSettings.isAddOverrideToImplementInConverter());
        setValue(donTShowDialogCheckBox, scalaProjectSettings.isDontShowConversionDialog());
        setValue(treatDocCommentAsBlockComment, scalaProjectSettings.isTreatDocCommentAsBlockComment());

        setValue(showImplicitConversionsInCheckBox, scalaProjectSettings.isShowImplisitConversions());
        setValue(showNotFoundImplicitArgumentsCheckBox, scalaProjectSettings.isShowNotFoundImplicitArguments());
        setValue(showAmbiguousImplicitArgumentsCheckBox, scalaProjectSettings.isShowAmbiguousImplicitArguments());
        setValue(showArgumentsToByNameParametersCheckBox, scalaProjectSettings.isShowArgumentsToByNameParams());
        setValue(customScalatestSyntaxHighlightingCheckbox, scalaProjectSettings.isCustomScalatestSyntaxHighlighting());
        setValue(includeBlockExpressionsExpressionsCheckBox, scalaProjectSettings.isIncludeBlockExpressions());
        setValue(includeLiteralsCheckBox, scalaProjectSettings.isIncludeLiterals());

        setValue(myResolveToAllClassesCheckBox, scalaProjectSettings.isIgnorePerformance());

        setValue(myDisableLanguageInjection, scalaProjectSettings.isDisableLangInjection());
        setValue(myDontCacheCompound, scalaProjectSettings.isDontCacheCompoundTypes());
        setValue(myAotCompletion, scalaProjectSettings.isAotCompletion());
        setValue(useScalaClassesPriorityCheckBox, scalaProjectSettings.isScalaPriority());
        collectionHighlightingChooser.setSelectedItem(scalaProjectSettings.getCollectionTypeHighlightingLevel());
        setWorksheetDelay(scalaProjectSettings.getAutoRunDelay());

        setValue(myProjectViewHighlighting, scalaProjectSettings.isProjectViewHighlighting());
        setValue(myGroupPackageObjectWithPackage, scalaProjectSettings.isGroupPackageObjectWithPackage());

        scTypeSelectionCombobox.setSelectedItem(scalaProjectSettings.getScFileMode());
        trailingCommasComboBox.setSelectedItem(scalaProjectSettings.getTrailingCommasMode());

        for (ComponentWithSettings setting : extraSettings) {
            setting.loadSettings(scalaProjectSettings);
        }

        scalaMetaMode.getModel().setSelectedItem(scalaProjectSettings.getScalaMetaMode());
        setValue(metaTrimBodies, scalaProjectSettings.isMetaTrimMethodBodies());

        ivy2IndexingModeCBB.getModel().setSelectedItem(scalaProjectSettings.getIvy2IndexingMode());

        setValue(extensionsPanel.enabledCB(), scalaProjectSettings.isEnableLibraryExtensions());
    }

    private int getWorksheetDelay() {
        return autoRunDelaySlider.getValue();
    }

    private void setWorksheetDelay(int delay) {
        autoRunDelaySlider.setValue(delay);
    }

    private static void setValue(JSpinner spinner, int value) {
        spinner.setValue(value);
    }

    private static void setValue(final JCheckBox box, final boolean value) {
        box.setSelected(value);
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
        tabbedPane = new JTabbedPane();
        myPanel.add(tabbedPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(18, 2, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.editor"), panel1);
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(17, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        showImplicitConversionsInCheckBox = new JCheckBox();
        showImplicitConversionsInCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showImplicitConversionsInCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlight.implicit.conversions"));
        panel1.add(showImplicitConversionsInCheckBox, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showArgumentsToByNameParametersCheckBox = new JCheckBox();
        showArgumentsToByNameParametersCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showArgumentsToByNameParametersCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlight.arguments.to.by.name.parameters"));
        panel1.add(showArgumentsToByNameParametersCheckBox, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        includeBlockExpressionsExpressionsCheckBox = new JCheckBox();
        includeBlockExpressionsExpressionsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(includeBlockExpressionsExpressionsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.include.block.expressions"));
        includeBlockExpressionsExpressionsCheckBox.setToolTipText("Include expressions enclosed in in curly braces");
        panel1.add(includeBlockExpressionsExpressionsCheckBox, new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        includeLiteralsCheckBox = new JCheckBox();
        includeLiteralsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(includeLiteralsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.include.literals"));
        includeLiteralsCheckBox.setToolTipText("Include string, number, etc");
        panel1.add(includeLiteralsCheckBox, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final TitledSeparator titledSeparator1 = new TitledSeparator();
        titledSeparator1.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlighting"));
        panel1.add(titledSeparator1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final TitledSeparator titledSeparator2 = new TitledSeparator();
        titledSeparator2.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.autocomplete"));
        panel1.add(titledSeparator2, new GridConstraints(10, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        myAotCompletion = new JCheckBox();
        this.$$$loadButtonText$$$(myAotCompletion, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.ahead.of.time.completion"));
        panel1.add(myAotCompletion, new GridConstraints(11, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        useScalaClassesPriorityCheckBox = new JCheckBox();
        useScalaClassesPriorityCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(useScalaClassesPriorityCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.use.scala.classes.priority.over.java"));
        panel1.add(useScalaClassesPriorityCheckBox, new GridConstraints(12, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        enableConversionOnCopyCheckBox = new JCheckBox();
        enableConversionOnCopyCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(enableConversionOnCopyCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.convert.java.code.to.scala.on.copy.paste"));
        panel1.add(enableConversionOnCopyCheckBox, new GridConstraints(14, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        donTShowDialogCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(donTShowDialogCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.automatically.convert.to.scala.code.without.dialog"));
        panel1.add(donTShowDialogCheckBox, new GridConstraints(15, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
        final TitledSeparator titledSeparator3 = new TitledSeparator();
        titledSeparator3.setText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.code.conversion"));
        panel1.add(titledSeparator3, new GridConstraints(13, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        showTypeInfoOnCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(showTypeInfoOnCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.show.type.info.on.mouse.hover"));
        panel2.add(showTypeInfoOnCheckBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        delaySpinner = new JSpinner();
        panel2.add(delaySpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        customScalatestSyntaxHighlightingCheckbox = new JCheckBox();
        this.$$$loadButtonText$$$(customScalatestSyntaxHighlightingCheckbox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.custom.scalatest.keywords.highlighting"));
        panel1.add(customScalatestSyntaxHighlightingCheckbox, new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        addOverrideToImplementCheckBox = new JCheckBox();
        addOverrideToImplementCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(addOverrideToImplementCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.add.override.keyword.to.method.implementation"));
        panel1.add(addOverrideToImplementCheckBox, new GridConstraints(16, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showNotFoundImplicitArgumentsCheckBox = new JCheckBox();
        showNotFoundImplicitArgumentsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showNotFoundImplicitArgumentsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.show.hints.if.no.implicit.arguments.found"));
        panel1.add(showNotFoundImplicitArgumentsCheckBox, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel3, new GridConstraints(9, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.collection.type.highlighting.option"));
        panel3.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        collectionHighlightingChooser = new JComboBox();
        panel3.add(collectionHighlightingChooser, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        showAmbiguousImplicitArgumentsCheckBox = new JCheckBox();
        showAmbiguousImplicitArgumentsCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(showAmbiguousImplicitArgumentsCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.show.hints.if.ambiguous.implicit.arguments.found"));
        panel1.add(showAmbiguousImplicitArgumentsCheckBox, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(3, 1, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.project.view"), panel4);
        myProjectViewHighlighting = new JCheckBox();
        this.$$$loadButtonText$$$(myProjectViewHighlighting, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.highlight.nodes.with.errors"));
        panel4.add(myProjectViewHighlighting, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel4.add(spacer2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myGroupPackageObjectWithPackage = new JCheckBox();
        this.$$$loadButtonText$$$(myGroupPackageObjectWithPackage, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.group.package.object.with.package"));
        panel4.add(myGroupPackageObjectWithPackage, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(10, 2, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.performance"), panel5);
        final Spacer spacer3 = new Spacer();
        panel5.add(spacer3, new GridConstraints(9, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.implicit.parameters.search.depth"));
        panel5.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        implicitParametersSearchDepthSpinner = new JSpinner();
        panel5.add(implicitParametersSearchDepthSpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(50, -1), null, null, 0, false));
        myResolveToAllClassesCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(myResolveToAllClassesCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.resolve.to.all.classes.even.in.wrong.directories"));
        panel5.add(myResolveToAllClassesCheckBox, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        treatDocCommentAsBlockComment = new JCheckBox();
        this.$$$loadButtonText$$$(treatDocCommentAsBlockComment, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.disable.parsing.of.documentation.comments"));
        panel5.add(treatDocCommentAsBlockComment, new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myDisableLanguageInjection = new JCheckBox();
        this.$$$loadButtonText$$$(myDisableLanguageInjection, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.disable.language.injection.in.scala.files"));
        panel5.add(myDisableLanguageInjection, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myDontCacheCompound = new JCheckBox();
        this.$$$loadButtonText$$$(myDontCacheCompound, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.dont.cache.compound.types"));
        panel5.add(myDontCacheCompound, new GridConstraints(8, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        searchAllSymbolsIncludeCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(searchAllSymbolsIncludeCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.search.all.symbols"));
        panel5.add(searchAllSymbolsIncludeCheckBox, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        this.$$$loadLabelText$$$(label3, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.annot212"));
        label3.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.annot212.tooltip"));
        panel5.add(label3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        scalaMetaMode = new JComboBox();
        scalaMetaMode.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.modeOptions.tooltip"));
        panel5.add(scalaMetaMode, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        metaTrimBodies = new JCheckBox();
        this.$$$loadButtonText$$$(metaTrimBodies, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.trimBodies.caption"));
        metaTrimBodies.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scala.meta.settings.trimBodies.tooltip"));
        panel5.add(metaTrimBodies, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ivy2IndexingModeCBB = new JComboBox();
        ivy2IndexingModeCBB.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.sbt.index.ivy2.mode.hint"));
        panel5.add(ivy2IndexingModeCBB, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        this.$$$loadLabelText$$$(label4, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.sbt.index.ivy2.mode"));
        label4.setToolTipText(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.sbt.index.ivy2.mode.hint"));
        panel5.add(label4, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(8, 6, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.worksheet"), panel6);
        final Spacer spacer4 = new Spacer();
        panel6.add(spacer4, new GridConstraints(7, 0, 1, 6, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        runWorksheetInTheCheckBox = new JCheckBox();
        runWorksheetInTheCheckBox.setSelected(true);
        this.$$$loadButtonText$$$(runWorksheetInTheCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.run.worksheet.in.the.compiler.process"));
        panel6.add(runWorksheetInTheCheckBox, new GridConstraints(2, 0, 1, 6, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        this.$$$loadLabelText$$$(label5, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.output.cutoff.limit"));
        panel6.add(label5, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        useEclipseCompatibilityModeCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(useEclipseCompatibilityModeCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.use.eclipse.compatibility.mode"));
        panel6.add(useEclipseCompatibilityModeCheckBox, new GridConstraints(3, 0, 1, 6, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        treatScalaScratchFilesCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(treatScalaScratchFilesCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.treat.scala.scratch.files.as.worksheet.files"));
        panel6.add(treatScalaScratchFilesCheckBox, new GridConstraints(4, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        this.$$$loadLabelText$$$(label6, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.delay.before.auto.run"));
        panel6.add(label6, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        autoRunDelaySlider = new JSlider();
        autoRunDelaySlider.setExtent(0);
        autoRunDelaySlider.setMaximum(3000);
        autoRunDelaySlider.setMinimum(700);
        autoRunDelaySlider.setValue(700);
        panel6.add(autoRunDelaySlider, new GridConstraints(6, 1, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        scTypeSelectionCombobox = new JComboBox();
        panel6.add(scTypeSelectionCombobox, new GridConstraints(0, 1, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        this.$$$loadLabelText$$$(label7, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.treat.sc.files.as"));
        panel6.add(label7, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        collapseWorksheetFoldByCheckBox = new JCheckBox();
        this.$$$loadButtonText$$$(collapseWorksheetFoldByCheckBox, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.collapse.long.output.by.default"));
        panel6.add(collapseWorksheetFoldByCheckBox, new GridConstraints(5, 0, 1, 5, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer5 = new Spacer();
        panel6.add(spacer5, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        outputSpinner = new JSpinner();
        panel6.add(outputSpinner, new GridConstraints(1, 4, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, new Dimension(50, -1), null, null, 0, false));
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(2, 2, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.base.packages"), panel7);
        final Spacer spacer6 = new Spacer();
        panel7.add(spacer6, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel7.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myBasePackages = new JTextArea();
        myBasePackages.setColumns(50);
        myBasePackages.setRows(10);
        scrollPane1.setViewportView(myBasePackages);
        final Spacer spacer7 = new Spacer();
        panel7.add(spacer7, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(4, 3, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.misc"), panel8);
        panel8.add(injectionJPanel, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label8 = new JLabel();
        this.$$$loadLabelText$$$(label8, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.scalatest.default.super.class"));
        panel8.add(label8, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer8 = new Spacer();
        panel8.add(spacer8, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        scalaTestDefaultSuperClass = new JTextField();
        scalaTestDefaultSuperClass.setColumns(25);
        panel8.add(scalaTestDefaultSuperClass, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label9 = new JLabel();
        this.$$$loadLabelText$$$(label9, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.trailing.commas"));
        panel8.add(label9, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        trailingCommasComboBox = new JComboBox();
        panel8.add(trailingCommasComboBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer9 = new Spacer();
        panel8.add(spacer9, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridLayoutManager(2, 3, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.updates"), panel9);
        final Spacer spacer10 = new Spacer();
        panel9.add(spacer10, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JLabel label10 = new JLabel();
        this.$$$loadLabelText$$$(label10, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.plugin.update.channel"));
        panel9.add(label10, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        updateChannel = new JComboBox();
        updateChannel.setEditable(false);
        panel9.add(updateChannel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        updateNowButton = new JButton();
        this.$$$loadButtonText$$$(updateNowButton, this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.check.for.updates"));
        panel9.add(updateNowButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridLayoutManager(1, 1, new Insets(9, 9, 0, 0), -1, -1));
        tabbedPane.addTab(this.$$$getMessageFromBundle$$$("messages/ScalaBundle", "scala.project.settings.form.tabs.extensions"), panel10);
        librariesPanel = new JPanel();
        librariesPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel10.add(librariesPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 1, false));
    }

    private static Method $$$cachedGetBundleMethod$$$ = null;

    private String $$$getMessageFromBundle$$$(String path, String key) {
        ResourceBundle bundle;
        try {
            Class<?> thisClass = this.getClass();
            if ($$$cachedGetBundleMethod$$$ == null) {
                Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
                $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
            }
            bundle = (ResourceBundle) $$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
        } catch (Exception e) {
            bundle = ResourceBundle.getBundle(path);
        }
        return bundle.getString(key);
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
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
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
            component.setMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myPanel;
    }


    private class ScalaTestHighlightingDialog extends DialogWrapper {

        ScalaTestHighlightingDialog() {
            super(myProject);
            init();
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
            JComponent res = new JPanel();
            res.add(new JLabel("Changes in ScalaTest highlighting will be processed correctly only on freshly highlighted files." +
                    "For best experience please restart Intellij IDEA"));
            return res;
        }

        @NotNull
        @Override
        protected Action[] createActions() {
            return new Action[]{getOKAction()};
        }
    }

}
